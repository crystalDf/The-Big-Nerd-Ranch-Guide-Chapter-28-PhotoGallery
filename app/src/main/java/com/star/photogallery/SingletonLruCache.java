package com.star.photogallery;


import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

public class SingletonLruCache extends LruCache<String, Bitmap> {

    private static SingletonLruCache sSinglettonLruCache;

    private SingletonLruCache(int maxSize) {
        super(maxSize);
    }

    public static SingletonLruCache getInstance(int maxSize) {
        if (sSinglettonLruCache == null) {
            synchronized (SingletonLruCache.class) {
                if (sSinglettonLruCache == null) {
                    sSinglettonLruCache = new SingletonLruCache(maxSize);
                }
            }
        }
        return sSinglettonLruCache;
    }

    @Override
    protected int sizeOf(String key, Bitmap value) {
        return value.getByteCount() / 1024;
    }

    public static Bitmap getBitmapFromMemoryCache(String key) {
        if (key == null) {
            return null;
        }
        return sSinglettonLruCache.get(key);
    }

    public static void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            sSinglettonLruCache.put(key, bitmap);
        }
    }
}
