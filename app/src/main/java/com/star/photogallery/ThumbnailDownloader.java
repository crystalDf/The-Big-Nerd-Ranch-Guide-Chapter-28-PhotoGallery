package com.star.photogallery;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ThumbnailDownloader<Token> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";

    private static final int MESSAGE_DOWNLOAD = 0;

    private Handler mHandler;
    private Map<Token, String> requestMap =
            Collections.synchronizedMap(new HashMap<Token, String>());

    private Handler mResponseHandler;
    private Listener<Token> mListener;

    public interface Listener<Token> {
        public void onThumbnailDownloaded(Token token, Bitmap thumbnail);
    }

    public void setListener(Listener<Token> listener) {
        mListener = listener;
    }

//    public ThumbnailDownloader() {
//        super(TAG);
//    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
    }

    @Override
    protected void onLooperPrepared() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    Token token = (Token) msg.obj;
                    Log.i(TAG, "Got a request for url: " + requestMap.get(token));
                    handleRequest(token);
                }
            }
        };
    }

    public void queueThumbnail(Token token, String url) {
        Log.i(TAG, "Got an URL: " + url);

        requestMap.put(token, url);

        mHandler.obtainMessage(MESSAGE_DOWNLOAD, token).sendToTarget();
    }

    private void handleRequest(final Token token) {
        try {
            final String url = requestMap.get(token);
            if (url != null) {
                final Bitmap bitmap;
                Bitmap tempBitmap;

                if ((tempBitmap = SingletonLruCache.getBitmapFromMemoryCache(url)) != null) {
                    bitmap = tempBitmap;
                } else {
                    byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                    bitmap = BitmapFactory.decodeByteArray(
                            bitmapBytes, 0, bitmapBytes.length);
                    SingletonLruCache.addBitmapToMemoryCache(url, bitmap);
                    Log.i(TAG, "Bitmap created");
                }

                mResponseHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(requestMap.get(token))) {
                            requestMap.remove(token);
                            mListener.onThumbnailDownloaded(token, bitmap);
                        }
                    }
                });
            }
        } catch (IOException e) {
            Log.e(TAG, "Error downloading image", e);
            e.printStackTrace();
        }

    }

    public void clearQueue() {
        mHandler.removeMessages(MESSAGE_DOWNLOAD);
        requestMap.clear();
    }
}
