package com.github.charleslzq.facestore.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.charleslzq.facestore.server.message.ClientMessagePayloadType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.LocalDateTime;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Component
public class FaceStoreHealthIndicator extends AbstractHealthIndicator {
    private final Set<InetSocketAddress> clients = new CopyOnWriteArraySet<>();
    private final List<ClientMessage> clientMessages = new CopyOnWriteArrayList<>();

    public void addClient(InetSocketAddress address) {
        clients.add(address);
    }

    public void removeClient(InetSocketAddress address) {
        clients.remove(address);
    }

    public void recordClientMessage(ClientMessage clientMessage) {
        clientMessages.add(clientMessage);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (clients.size() > 0) {
            builder.up();
        } else {
            builder.unknown();
        }
        builder.withDetail("Active Clients", clients);
        builder.withDetail("Client Messages", ImmutableMap.<String, Object>builder()
                .put("Received in all", clientMessages.size())
                .put("Address Counting", clientMessages.stream().collect(Collectors.groupingBy(
                        ClientMessage::getAddress,
                        Collectors.counting()
                )))
                .put("Type Counting", clientMessages.stream().collect(Collectors.groupingBy(
                        clientMessage -> clientMessage.type.name(),
                        Collectors.counting()
                )))
                .put("Time statistics", ImmutableMap.of(
                        "Address Distribution", clientMessages.stream().collect(Collectors.groupingBy(
                                ClientMessage::getAddress
                        )).entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream().map(ClientMessage::getDuration).reduce(0.0, (a, b) -> a + b) / entry.getValue().size()
                        )),
                        "Type Distribution", clientMessages.stream().collect(Collectors.groupingBy(
                                clientMessage -> clientMessage.type.name()
                        )).entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream().map(ClientMessage::getDuration).reduce(0.0, (a, b) -> a + b) / entry.getValue().size()
                        ))
                ))
                .put("Recent 20 Messages", Lists.reverse(clientMessages).stream().limit(20).collect(Collectors.toList()))
                .build()
        );

    }

    @Data
    @AllArgsConstructor
    public static class ClientMessage {
        private final InetSocketAddress address;
        private final ClientMessagePayloadType type;
        private final String token;
        @JsonSerialize(using = JacksonLocalDateTimeConverter.class)
        private final LocalDateTime startTime;
        @JsonSerialize(using = JacksonLocalDateTimeConverter.class)
        private final LocalDateTime endTime;

        @JsonInclude
        public Double getDuration() {
            return new Duration(startTime.toDateTime(DateTimeZone.UTC), endTime.toDateTime(DateTimeZone.UTC)).getMillis() / 1000.0;
        }
    }
}
