package com.github.charleslzq.facestore.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(FaceStoreProperties.class)
public class FaceStoreConfiguration implements WebSocketConfigurer {

    @Autowired
    private FaceStoreProperties faceStoreProperties;

    @Autowired(required = false)
    private AsyncTaskExecutor asyncTaskExecutor = new SimpleAsyncTaskExecutor();

    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(new FaceStoreWebSocketBackend(faceStoreProperties.getDirectory(), asyncTaskExecutor), "/face-store").setAllowedOrigins("*");
    }
}
