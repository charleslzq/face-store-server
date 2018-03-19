package com.github.charleslzq.facestore.server;

import com.github.charleslzq.facestore.FaceStoreChangeListener;
import com.github.charleslzq.facestore.Meta;
import org.springframework.core.task.AsyncTaskExecutor;

public class AsyncFaceDataChangeListener<P extends Meta, F extends Meta> implements FaceStoreChangeListener<P, F> {
    private final AsyncTaskExecutor asyncTaskExecutor;
    private final FaceStoreChangeListener<P, F> faceStoreChangeListener;

    public AsyncFaceDataChangeListener(AsyncTaskExecutor asyncTaskExecutor, FaceStoreChangeListener<P, F> faceStoreChangeListener) {
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.faceStoreChangeListener = faceStoreChangeListener;
    }

    @Override
    public void onPersonUpdate(P p) {
        asyncTaskExecutor.submit(() -> faceStoreChangeListener.onPersonUpdate(p));
    }

    @Override
    public void onFaceUpdate(String s, F f) {
        asyncTaskExecutor.submit(() -> faceStoreChangeListener.onFaceUpdate(s, f));
    }

    @Override
    public void onFaceDataDelete(String s) {
        asyncTaskExecutor.submit(() -> faceStoreChangeListener.onFaceDataDelete(s));
    }

    @Override
    public void onFaceDelete(String s, String s1) {
        asyncTaskExecutor.submit(() -> faceStoreChangeListener.onFaceDelete(s, s1));
    }

    @Override
    public void onPersonFaceClear(String s) {
        asyncTaskExecutor.submit(() -> faceStoreChangeListener.onPersonFaceClear(s));
    }
}
