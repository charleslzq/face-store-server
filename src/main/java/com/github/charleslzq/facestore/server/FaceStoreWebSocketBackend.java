package com.github.charleslzq.facestore.server;

import com.fatboyindustrial.gsonjodatime.Converters;
import com.github.charleslzq.facestore.FaceData;
import com.github.charleslzq.facestore.FaceFileReadWriteStore;
import com.github.charleslzq.facestore.FaceStoreChangeListener;
import com.github.charleslzq.facestore.ReadWriteFaceStore;
import com.github.charleslzq.facestore.server.message.ClientMessagePayloadType;
import com.github.charleslzq.facestore.server.message.Message;
import com.github.charleslzq.facestore.server.message.MessageHeaders;
import com.github.charleslzq.facestore.server.message.ServerMessagePayloadType;
import com.github.charleslzq.facestore.server.type.Face;
import com.github.charleslzq.facestore.server.type.Person;
import com.github.charleslzq.facestore.server.type.ServerFaceDataType;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FaceStoreWebSocketBackend implements WebSocketHandler, FaceStoreChangeListener<Person, Face> {
    private static final String HEART_BEAT_MESSAGE = "@heart";
    private static final TypeToken<Message<Object>> RAW_CLIENT_MESSAGE_TYPE = new TypeToken<Message<Object>>() {
    };
    private static final TypeToken<Message<Person>> PERSON_MESSAGE_TYPE = new TypeToken<Message<Person>>() {
    };
    private static final TypeToken<Message<Face>> FACE_MESSAGE_TYPE = new TypeToken<Message<Face>>() {
    };
    private static final TypeToken<Message<FaceData<Person, Face>>> FACE_DATA_MESSAGE_TYPE = new TypeToken<Message<FaceData<Person, Face>>>() {
    };
    private final Gson gson = Converters.registerLocalDateTime(new GsonBuilder()).create();
    private final ReadWriteFaceStore<Person, Face> faceStore;
    private final Map<InetSocketAddress, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public FaceStoreWebSocketBackend(String directory, AsyncTaskExecutor asyncTaskExecutor) {
        faceStore = new FaceFileReadWriteStore<>(
                directory,
                new ServerFaceDataType(),
                Collections.singletonList(new AsyncFaceDataChangeListener<>(asyncTaskExecutor, this))
        );
    }

    public void handleMessage(@NonNull WebSocketSession webSocketSession, @NonNull WebSocketMessage<?> webSocketMessage) {
        if (webSocketMessage instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) webSocketMessage;
            String content = textMessage.getPayload();
            if (!HEART_BEAT_MESSAGE.equals(content)) {
                Message<Object> rawMessage = gson.fromJson(content, RAW_CLIENT_MESSAGE_TYPE.getType());
                Map<String, String> headers = rawMessage.getHeaders();
                ClientMessagePayloadType type = ClientMessagePayloadType.valueOf(headers.get(MessageHeaders.TYPE_HEADER));
                switch (type) {
                    case REFRESH:
                        faceStore.getPersonIds().forEach(personId -> {
                            sendMessage(webSocketSession, new Message<>(ImmutableMap.of(
                                    MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.PERSON.name()
                            ), faceStore.getPerson(personId)));
                            faceStore.getFaceIdList(personId).forEach(faceId ->
                                    sendMessage(webSocketSession, new Message<>(ImmutableMap.of(
                                            MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.FACE.name(),
                                            MessageHeaders.PERSON_ID, personId
                                    ), faceStore.getFace(personId, faceId)))
                            );
                        });
                        break;
                    case PERSON:
                        Message<Person> personMessage = gson.fromJson(content, PERSON_MESSAGE_TYPE.getType());
                        faceStore.savePerson(personMessage.getPayload());
                        break;
                    case FACE:
                        Message<Face> faceMessage = gson.fromJson(content, FACE_MESSAGE_TYPE.getType());
                        faceStore.saveFace(faceMessage.getHeaders().get(MessageHeaders.PERSON_ID), faceMessage.getPayload());
                        break;
                    case FACE_DATA:
                        Message<FaceData<Person, Face>> faceDataMessage = gson.fromJson(content, FACE_DATA_MESSAGE_TYPE.getType());
                        faceStore.saveFaceData(faceDataMessage.getPayload());
                        break;
                    case PERSON_DELETE:
                        faceStore.deleteFaceData(rawMessage.getHeaders().get(MessageHeaders.PERSON_ID));
                        break;
                    case FACE_CLEAR:
                        faceStore.clearFace(rawMessage.getHeaders().get(MessageHeaders.PERSON_ID));
                        break;
                    case FACE_DELETE:
                        faceStore.deleteFace(
                                rawMessage.getHeaders().get(MessageHeaders.PERSON_ID),
                                rawMessage.getHeaders().get(MessageHeaders.FACE_ID)
                        );
                        break;
                }
            }
        }
    }

    public void afterConnectionEstablished(@NonNull WebSocketSession webSocketSession) {
        log.info("Session connected from {}, {}", webSocketSession.getRemoteAddress(), webSocketSession.getAttributes());
        sessions.put(webSocketSession.getRemoteAddress(), webSocketSession);
    }

    public void handleTransportError(@NonNull WebSocketSession webSocketSession, @NonNull Throwable throwable) {
        log.warn("Session error", throwable);
    }

    public void afterConnectionClosed(@NonNull WebSocketSession webSocketSession, @NonNull CloseStatus closeStatus) {
        log.info("Session dis-connected from {}, {}, {}", webSocketSession.getRemoteAddress(), webSocketSession.getAttributes(), closeStatus);
        sessions.remove(webSocketSession.getRemoteAddress());
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

    public void onFaceDataDelete(String personId) {
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

    public void onPersonFaceClear(String personId) {
        publish(new Message<>(ImmutableMap.of(
                MessageHeaders.TYPE_HEADER, ServerMessagePayloadType.FACE_CLEAR.name(),
                MessageHeaders.PERSON_ID, personId
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
        sessions.values().forEach(session -> sendMessage(session, message));
    }

    private synchronized void sendMessage(WebSocketSession webSocketSession, Message message) {
        try {
            webSocketSession.sendMessage(new TextMessage(gson.toJson(message)));
        } catch (IOException e) {
            log.error("Error sending message " + message.toString(), e);
        }
    }
}
