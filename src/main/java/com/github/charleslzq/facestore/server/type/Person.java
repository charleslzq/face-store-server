package com.github.charleslzq.facestore.server.type;

import com.github.charleslzq.facestore.Meta;
import lombok.Data;
import org.joda.time.LocalDateTime;

@Data
public class Person implements Meta {
    private String id;
    private String name;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
