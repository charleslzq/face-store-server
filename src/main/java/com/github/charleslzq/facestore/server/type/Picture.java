package com.github.charleslzq.facestore.server.type;

import lombok.Data;

@Data
public class Picture {
    private byte[] mBuffer;
    private int mDensity;
    private boolean mGalleryCached;
    private boolean mIsMutable;
    private boolean mRecycled;
    private boolean mRequestPremultiplied;
    private int mHeight;
    private int mWidth;
    private int mNativePtr;
}
