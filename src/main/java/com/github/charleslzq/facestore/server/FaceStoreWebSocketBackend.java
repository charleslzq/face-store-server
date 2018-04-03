package com.github.charleslzq.facestore.server;

import com.fatboyindustrial.gsonjodatime.Converters;
import com.github.charleslzq.facestore.FaceStoreChangeListener;
import com.github.charleslzq.facestore.ListenableReadWriteFaceStore;
import com.github.charleslzq.facestore.server.message.ClientMessagePayloadType;
import com.github.charleslzq.facestore.server.message.Message;
import com.github.charleslzq.facestore.server.message.MessageHeaders;
import com.github.charleslzq.facestore.server.message.ServerMessagePayloadType;
import com.github.charleslzq.facestore.server.type.Face;
import com.github.charleslzq.facestore.server.type.Person;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FaceStoreWebSocketBackend implements WebSocketHandler, FaceStoreChangeListener<Person, Face> {
    private static final String HEART_BEAT_MESSAGE = "@heart";
    private static final TypeToken<Message<Object>> RAW_CLIENT_MESSAGE_TYPE = new TypeToken<Message<Object>>() {
    };
    private static final TypeToken<Message<Person>> PERSON_MESSAGE_TYPE = new TypeToken<Message<Person>>() {
    };
    private static final TypeToken<Message<Face>> FACE_MESSAGE_TYPE = new TypeToken<Message<Face>>() {
    };
    private final Gson gson = Converters.registerLocalDateTime(new GsonBuilder()).create();
    private final Map<InetSocketAddress, WebSocketSession> sessions = new ConcurrentHashMap<>();
    @Autowired(required = false)
    private AsyncTaskExecutor asyncTaskExecutor = new SimpleAsyncTaskExecutor();
    @Autowired
    @Qualifier("faceStoreCacheWrapper")
    private ListenableReadWriteFaceStore<Person, Face> faceStore;
    @Autowired
    private FaceStoreHealthIndicator faceStoreHealthIndicator;

    @PostConstruct
    public void setup() {
        faceStore.getListeners().add(new AsyncFaceDataChangeListener<>(asyncTaskExecutor, this));
    }

    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) {
        if (webSocketMessage instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) webSocketMessage;
            String content = textMessage.getPayload();
            if (!HEART_BEAT_MESSAGE.equals(content)) {
                Message<Object> rawMessage = gson.fromJson(content, RAW_CLIENT_MESSAGE_TYPE.getType());
                Map<String, String> headers = rawMessage.getHeaders();
                ClientMessagePayloadType type = ClientMessagePayloadType.valueOf(headers.get(MessageHeaders.TYPE_HEADER));
                String token = headers.get(MessageHeaders.TOKEN);
                LocalDateTime startTime = LocalDateTime.now();
                LocalDateTime clientSentTime = gson.fromJson(headers.get(MessageHeaders.TIMESTAMP), LocalDateTime.class);
                log.info("Handling client Request {} with token {} sent at {}", type, token, clientSentTime);
                switch (type) {
                    case REFRESH:
                        List<String> persons = faceStore.getPersonIds();
                        sendMessage(webSocketSession, new Message<>(ImmutableMap.of(
                                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.PERSON_ID_LIST.name(),
                                MessageHeaders.TOKEN, token,
                                MessageHeaders.TIMESTAMP, gson.toJson(LocalDateTime.now())
                        ), persons));
                        for (int index = 0; index < persons.size(); index++) {
                            String personId = persons.get(index);
                            sendMessage(webSocketSession, new Message<>(ImmutableMap.of(
                                    MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.PERSON.name(),
                                    MessageHeaders.TOKEN, token,
                                    MessageHeaders.SIZE, String.valueOf(persons.size()),
                                    MessageHeaders.INDEX, String.valueOf(index),
                                    MessageHeaders.TIMESTAMP, gson.toJson(LocalDateTime.now())
                            ), faceStore.getPerson(personId)));
                            List<String> faces = faceStore.getFaceIdList(personId);
                            sendMessage(webSocketSession, new Message<>(ImmutableMap.of(
                                    MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.FACE_ID_LIST.name(),
                                    MessageHeaders.TOKEN, token,
                                    MessageHeaders.PERSON_ID, personId,
                                    MessageHeaders.TIMESTAMP, gson.toJson(LocalDateTime.now())
                            ), faces));
                            for (int faceIndex = 0; faceIndex < faces.size(); faceIndex++) {
                                String faceId = faces.get(faceIndex);
                                sendMessage(webSocketSession, new Message<>(ImmutableMap.<String, String>builder()
                                        .put(MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.FACE.name())
                                        .put(MessageHeaders.TOKEN, token)
                                        .put(MessageHeaders.PERSON_ID, personId)
                                        .put(MessageHeaders.SIZE, String.valueOf(faces.size()))
                                        .put(MessageHeaders.INDEX, String.valueOf(faceIndex))
                                        .put(MessageHeaders.TIMESTAMP, gson.toJson(LocalDateTime.now()))
                                        .build(),
                                        faceStore.getFace(personId, faceId)));
                            }
                        }
                        confirm(webSocketSession, type, token, startTime);
                        break;
                    case PERSON:
                        Message<Person> personMessage = gson.fromJson(content, PERSON_MESSAGE_TYPE.getType());
                        faceStore.savePerson(personMessage.getPayload());
                        confirm(webSocketSession, type, token, startTime);
                        break;
                    case FACE:
                        Message<Face> faceMessage = gson.fromJson(content, FACE_MESSAGE_TYPE.getType());
                        faceStore.saveFace(faceMessage.getHeaders().get(MessageHeaders.PERSON_ID), faceMessage.getPayload());
                        confirm(webSocketSession, type, token, startTime);
                        break;
                    case PERSON_DELETE:
                        faceStore.deletePerson(rawMessage.getHeaders().get(MessageHeaders.PERSON_ID));
                        confirm(webSocketSession, type, token, startTime);
                        break;
                    case FACE_DELETE:
                        faceStore.deleteFace(
                                rawMessage.getHeaders().get(MessageHeaders.PERSON_ID),
                                rawMessage.getHeaders().get(MessageHeaders.FACE_ID)
                        );
                        confirm(webSocketSession, type, token, startTime);
                        break;
                }
            }
        }
    }

    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        log.info("Session connected from {}, {}", webSocketSession.getRemoteAddress(), webSocketSession.getAttributes());
        sessions.put(webSocketSession.getRemoteAddress(), webSocketSession);
        faceStoreHealthIndicator.addClient(webSocketSession.getRemoteAddress());
    }

    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) {
        log.warn("Session error", throwable);
    }

    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) {
        log.info("Session dis-connected from {}, {}, {}", webSocketSession.getRemoteAddress(), webSocketSession.getAttributes(), closeStatus);
        sessions.remove(webSocketSession.getRemoteAddress());
        faceStoreHealthIndicator.removeClient(webSocketSession.getRemoteAddress());
    }

    public boolean supportsPartialMessages() {
        return false;
    }

    public void onPersonUpdate(Person person) {
        sendPerson(person);
    }

    public void onFaceUpdate(String personId, Face face) {
        sendFace(personId, face);
    }

    public void onPersonDelete(String personId) {
        publish(new Message<>(ImmutableMap.of(
                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.PERSON_DELETE.name(),
                MessageHeaders.PERSON_ID, personId
        ), ""));
    }

    public void onFaceDelete(String personId, String faceId) {
        publish(new Message<>(ImmutableMap.of(
                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.FACE_DELETE.name(),
                MessageHeaders.PERSON_ID, personId,
                MessageHeaders.FACE_ID, faceId
        ), ""));
    }

    private void sendPerson(Person person) {
        publish(new Message<>(ImmutableMap.of(
                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.PERSON.name()
        ), person));
    }

    private void sendFace(String personId, Face face) {
        publish(new Message<>(ImmutableMap.of(
                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.FACE.name(),
                MessageHeaders.PERSON_ID, personId
        ), face));
    }

    private void publish(Message message) {
        int size = sessions.values().size();
        log.info("Ready to send message to {} client(s)", size);
        long success = sessions.values().stream().filter(session -> sendMessage(session, message)).count();
        log.info("Successfully send message to {}/{} client(s)", success, size);
    }

    private void confirm(WebSocketSession webSocketSession, ClientMessagePayloadType type, String token, LocalDateTime startTime) {
        LocalDateTime now = LocalDateTime.now();
        FaceStoreHealthIndicator.ClientMessage clientMessage = new FaceStoreHealthIndicator.ClientMessage(
                webSocketSession.getRemoteAddress(),
                type,
                token,
                startTime,
                now
        );
        faceStoreHealthIndicator.recordClientMessage(clientMessage);
        log.info("Request {} handled, use {} second(s)", token, clientMessage.getDuration());
        sendMessage(webSocketSession, new Message<>(ImmutableMap.of(
                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.CONFIRM.name(),
                MessageHeaders.TOKEN, token,
                MessageHeaders.TIMESTAMP, gson.toJson(LocalDateTime.now())
        ), new Object()));
    }

    private synchronized boolean sendMessage(WebSocketSession webSocketSession, Message message) {
        try {
            webSocketSession.sendMessage(new TextMessage(gson.toJson(message)));
            return true;
        } catch (IOException e) {
            log.error("Error sending message " + message.toString() + " to " + webSocketSession.getRemoteAddress(), e);
            return false;
        }
    }
}
