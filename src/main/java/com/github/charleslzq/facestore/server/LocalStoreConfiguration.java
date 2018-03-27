package com.github.charleslzq.facestore.server;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
@EnableConfigurationProperties(FaceStoreProperties.class)
public class LocalStoreConfiguration {

    @Autowired
    private FaceStoreProperties faceStoreProperties;

    @Autowired(required = false)
    private AsyncTaskExecutor asyncTaskExecutor = new SimpleAsyncTaskExecutor();

    @Bean
    public FaceStoreWebSocketBackend faceStoreWebSocketBackend(FaceStoreHealthIndicator faceStoreHealthIndicator) {
        return new FaceStoreWebSocketBackend(faceStoreProperties.getDirectory(), asyncTaskExecutor, faceStoreHealthIndicator);
    }
}
