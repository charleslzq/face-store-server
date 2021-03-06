package com.github.charleslzq.facestore.server;

import com.github.charleslzq.facestore.FaceStoreChangeListener;
import com.github.charleslzq.facestore.ListenableReadWriteFaceStore;
import com.github.charleslzq.facestore.server.type.Face;
import com.github.charleslzq.facestore.server.type.Person;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import rx.Observable;

import java.util.List;

@Component("faceStoreCacheWrapper")
public class FaceStoreCacheWrapper implements ListenableReadWriteFaceStore<Person, Face> {

    @Autowired
    @Qualifier("listenableReadWriteFaceStore")
    private ListenableReadWriteFaceStore<Person, Face> internal;

    @Override
    @Caching(
            evict = {
                    @CacheEvict(cacheNames = CacheNames.PERSON, key = "#person.id"),
                    @CacheEvict(cacheManager = CacheNames.PERSON_ID_LIST)
            }
    )
    public void savePerson(Person person) {
        internal.savePerson(person);
    }

    @Override
    @Caching(
            evict = {
                    @CacheEvict(cacheNames = CacheNames.FACE, key = "#personId + '_' + #face.id"),
                    @CacheEvict(cacheNames = CacheNames.FACE_ID_LIST, key = "#personId")
            }
    )
    public void saveFace(String personId, Face face) {
        internal.saveFace(personId, face);
    }

    @Override
    @Caching(
            evict = {
                    @CacheEvict(cacheNames = CacheNames.PERSON, key = "#personId"),
                    @CacheEvict(cacheNames = CacheNames.FACE_ID_LIST, key = "#personId"),
                    @CacheEvict(CacheNames.PERSON_ID_LIST)
            }
    )
    public void deletePerson(String personId) {
        internal.deletePerson(personId);
    }

    @Override
    @Caching(
            evict = {
                    @CacheEvict(cacheNames = CacheNames.FACE, key = "#personId + '_' + #faceId"),
                    @CacheEvict(cacheNames = CacheNames.FACE_ID_LIST, key = "#personId")
            }
    )
    public void deleteFace(String personId, String faceId) {
        internal.deleteFace(personId, faceId);
    }

    @NotNull
    @Override
    @Cacheable(CacheNames.PERSON_ID_LIST)
    public List<String> getPersonIds() {
        return internal.getPersonIds();
    }

    @NotNull
    @Override
    public Observable<String> getPersonIdsAsObservable() {
        return internal.getPersonIdsAsObservable();
    }

    @Nullable
    @Override
    @Cacheable(cacheNames = CacheNames.PERSON, key = "#personId")
    public Person getPerson(String personId) {
        return internal.getPerson(personId);
    }

    @NotNull
    @Override
    public Observable<Person> getPersonAsObservable(String personId) {
        return internal.getPersonAsObservable(personId);
    }

    @NotNull
    @Override
    @Cacheable(cacheNames = CacheNames.FACE_ID_LIST, key = "#personId")
    public List<String> getFaceIdList(String personId) {
        return internal.getFaceIdList(personId);
    }

    @NotNull
    @Override
    public Observable<String> getFaceIdListAsObservable(String personId) {
        return internal.getFaceIdListAsObservable(personId);
    }

    @Nullable
    @Override
    @Cacheable(cacheNames = CacheNames.FACE, key = "#personId + '_' + #faceId")
    public Face getFace(String personId, String faceId) {
        return internal.getFace(personId, faceId);
    }

    @NotNull
    @Override
    public Observable<Face> getFaceAsObservable(String personId, String faceId) {
        return internal.getFaceAsObservable(personId, faceId);
    }

    @NotNull
    @Override
    public List<FaceStoreChangeListener<Person, Face>> getListeners() {
        return internal.getListeners();
    }

    @NotNull
    @Override
    public Class<Person> getPersonClass() {
        return Person.class;
    }

    @NotNull
    @Override
    public Class<Face> getFaceClass() {
        return Face.class;
    }

    public static class CacheNames {
        static final String PERSON_ID_LIST = "personIdList";
        static final String PERSON = "person";
        static final String FACE = "face";
        static final String FACE_ID_LIST = "faceIdList";
    }
}
