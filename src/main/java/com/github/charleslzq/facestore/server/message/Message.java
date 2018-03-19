package com.github.charleslzq.facestore.server.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class Message<T> {
    private final Map<String, String> headers = new HashMap<>();
    private final T payload;

    public Message(Map<String, String> headers, T payload) {
        this.headers.putAll(headers);
        this.payload = payload;
    }
}
