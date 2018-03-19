package com.github.charleslzq.facestore.server.type;

import com.github.charleslzq.facestore.FaceDataType;
import lombok.Data;

@Data
public class ServerFaceDataType implements FaceDataType<Person, Face> {
    private final Class<Person> personClass = Person.class;
    private final Class<Face> faceClass = Face.class;
}
