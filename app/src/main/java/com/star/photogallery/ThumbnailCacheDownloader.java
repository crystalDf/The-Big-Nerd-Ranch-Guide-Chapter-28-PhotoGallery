package com.star.photogallery;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;

public class ThumbnailCacheDownloader extends HandlerThread {

    private static final String TAG = "ThumbnailCacheDownloader";

    private static final int MESSAGE_CACHE_DOWNLOAD = 1;

    private Handler mHandler;

    public ThumbnailCacheDownloader() {
        super(TAG);
    }

    @Override
    protected void onLooperPrepared() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_CACHE_DOWNLOAD) {
                    String url = (String) msg.obj;
                    Log.i(TAG, "Got a request for url: " + url);
                    handleCacheRequest(url);
                }
            }
        };
    }

    public void queueThumbnailCache(String url) {
        Log.i(TAG, "Got an URL: " + url);

        mHandler.obtainMessage(MESSAGE_CACHE_DOWNLOAD, url).sendToTarget();
    }

    private void handleCacheRequest(String url) {
        try {
            if ((url != null) &&
                    (SingletonLruCache.getBitmapFromMemoryCache(url) == null)) {
                byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                Bitmap bitmap = BitmapFactory.decodeByteArray(
                        bitmapBytes, 0, bitmapBytes.length);
                SingletonLruCache.addBitmapToMemoryCache(url, bitmap);
                Log.i(TAG, "Bitmap created");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error downloading image", e);
            e.printStackTrace();
        }
    }

    public void clearCacheQueue() {
        mHandler.removeMessages(MESSAGE_CACHE_DOWNLOAD);
    }
}
