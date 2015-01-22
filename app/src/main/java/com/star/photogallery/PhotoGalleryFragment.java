package com.star.photogallery;


import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.ArrayList;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private GridView mGridView;
    private ArrayList<GalleryItem> mItems;

    private ThumbnailDownloader<ImageView> mThumbnailThread;

    private int mCurrentPage = 1;
    private int mFetchedPage = 0;
    private int mCurrentPosition = 0;

    private ThumbnailCacheDownloader mThumbnailCacheThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        new FetchItemsTask().execute(mCurrentPage);

        mThumbnailThread = new ThumbnailDownloader<>(new Handler());
        mThumbnailThread.setListener(new ThumbnailDownloader.Listener<ImageView>() {

            @Override
            public void onThumbnailDownloaded(ImageView imageView, Bitmap thumbnail) {
                if (isVisible()) {
                    imageView.setImageBitmap(thumbnail);
                }
            }
        });
        mThumbnailThread.start();
        mThumbnailThread.getLooper();
        Log.i(TAG, "Background thread started");

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int catchSize = maxMemory / 8;

        SingletonLruCache.getInstance(catchSize);

        mThumbnailCacheThread = new ThumbnailCacheDownloader();
        mThumbnailCacheThread.start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mGridView = (GridView) v.findViewById(R.id.gridView);

        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (((firstVisibleItem + visibleItemCount) == totalItemCount) &&
                        (mCurrentPage == mFetchedPage)) {
                    mCurrentPosition = firstVisibleItem + 3;
                    mCurrentPage++;
                    new FetchItemsTask().execute(mCurrentPage);
                }
            }
        });

        setupAdapter();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailThread.clearQueue();
        mThumbnailCacheThread.clearCacheQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailThread.quit();
        mThumbnailCacheThread.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, ArrayList<GalleryItem>> {

        @Override
        protected ArrayList<GalleryItem> doInBackground(Integer... params) {

            return new FlickrFetchr().fetchItems(params[0]);
        }

        @Override
        protected void onPostExecute(ArrayList<GalleryItem> items) {
            if (mItems == null) {
                mItems = items;
            } else {
                mItems.addAll(items);
            }

            mFetchedPage++;

            setupAdapter();
        }
    }

    private void setupAdapter() {
        if ((getActivity() != null) && (mGridView != null)) {
            if (mItems != null) {
                mGridView.setAdapter(new GalleryItemAdapter(mItems));
            } else {
                mGridView.setAdapter(null);
            }
            mGridView.setSelection(mCurrentPosition);
        }
    }

    private class GalleryItemAdapter extends ArrayAdapter<GalleryItem> {

        public GalleryItemAdapter(ArrayList<GalleryItem> items) {
            super(getActivity(), 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(
                        R.layout.gallery_item, parent, false);
            }

            ImageView imageView = (ImageView) convertView.findViewById(
                    R.id.gallery_item_imageView);

            imageView.setImageResource(R.drawable.brian_up_close);
            GalleryItem item = getItem(position);

            Bitmap bitmap = SingletonLruCache.getBitmapFromMemoryCache(item.getUrl());

            if (bitmap == null) {
                mThumbnailThread.queueThumbnail(imageView, item.getUrl());
            } else {
                if (isVisible()) {
                    imageView.setImageBitmap(bitmap);
                }
            }

            for (int i = position - 10; i <= position + 10; i++) {
                if (i >= 0 && i < mItems.size()) {
                    String url = mItems.get(i).getUrl();
                    if (SingletonLruCache.getBitmapFromMemoryCache(url) == null) {
                        mThumbnailCacheThread.queueThumbnailCache(url);
                    }
                }
            }

            return convertView;
        }
    }

}
