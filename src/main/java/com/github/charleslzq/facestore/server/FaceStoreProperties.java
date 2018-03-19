package com.github.charleslzq.facestore.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "face.store.file")
public class FaceStoreProperties {
    private String directory;
}
