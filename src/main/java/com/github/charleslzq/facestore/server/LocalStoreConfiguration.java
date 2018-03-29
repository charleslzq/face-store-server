package com.github.charleslzq.facestore.server;


import com.fatboyindustrial.gsonjodatime.Converters;
import com.github.charleslzq.facestore.FaceFileReadWriteStore;
import com.github.charleslzq.facestore.ListenableReadWriteFaceStore;
import com.github.charleslzq.facestore.server.type.Face;
import com.github.charleslzq.facestore.server.type.Person;
import com.github.charleslzq.facestore.server.type.ServerFaceDataType;
import com.google.gson.GsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

@Configuration
@EnableCaching
@EnableConfigurationProperties(FaceStoreProperties.class)
public class LocalStoreConfiguration {

    @Autowired
    private FaceStoreProperties faceStoreProperties;

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager();
    }

    @Bean
    public ListenableReadWriteFaceStore<Person, Face> listenableReadWriteFaceStore() {
        return new FaceFileReadWriteStore<>(
                faceStoreProperties.getDirectory(),
                new ServerFaceDataType(),
                Converters.registerLocalDateTime(new GsonBuilder()).create(),
                new ArrayList<>()
        );
    }
}
