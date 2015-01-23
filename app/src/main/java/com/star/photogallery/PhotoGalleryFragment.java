package com.star.photogallery;


import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private GridView mGridView;
    private ArrayList<GalleryItem> mItems;

    private ThumbnailDownloader<ImageView> mThumbnailThread;

    private int mCurrentPage = 1;
    private int mFetchedPage = 0;
    private int mCurrentPosition = 0;

    private String mTotalSearch;

    private ThumbnailCacheDownloader mThumbnailCacheThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        updateItems();

        mThumbnailThread = new ThumbnailDownloader<>(new Handler());
        mThumbnailThread.setListener(new ThumbnailDownloader.Listener<ImageView>() {

            @Override
            public void onThumbnailDownloaded(ImageView imageView, Bitmap thumbnail) {
                if (isVisible()) {
                    imageView.setImageBitmap(thumbnail);
                }
            }
        });
        mThumbnailThread.setPriority(5);
        mThumbnailThread.start();
        mThumbnailThread.getLooper();
        Log.i(TAG, "Background thread started");

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int catchSize = maxMemory / 8;

        SingletonLruCache.getInstance(catchSize);

        mThumbnailCacheThread = new ThumbnailCacheDownloader();
        mThumbnailCacheThread.setPriority(1);
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

//        setupAdapter();

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

            Activity activity = getActivity();

            if (activity == null) {
                return new ArrayList<>();
            }

            String query = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString(FlickrFetchr.PREF_SEARCH_QUERY, null);

            if (query != null) {

                FlickrFetchr flickrFetchr = new FlickrFetchr();
                ArrayList<GalleryItem> items = flickrFetchr.search(query, params[0]);
                mTotalSearch = flickrFetchr.getTotalSearch();
                return items;
            } else {
                mTotalSearch = null;
                return new FlickrFetchr().fetchItems(params[0]);
            }
        }

        @Override
        protected void onPostExecute(ArrayList<GalleryItem> items) {

            if (mTotalSearch != null) {
                Toast.makeText(getActivity(), mTotalSearch,
                        Toast.LENGTH_LONG).show();
            }

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

            imageView.setImageResource(R.drawable.emma);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);

        SearchView searchView = (SearchView) searchItem.getActionView();

        SearchManager searchManager = (SearchManager)
                getActivity().getSystemService(Context.SEARCH_SERVICE);
        ComponentName componentName = getActivity().getComponentName();
        SearchableInfo searchableInfo = searchManager.getSearchableInfo(componentName);

        searchView.setSearchableInfo(searchableInfo);
        searchView.setSubmitButtonEnabled(true);

//        String query = PreferenceManager.getDefaultSharedPreferences(getActivity())
//                .getString(FlickrFetchr.PREF_SEARCH_QUERY, null);
//
//        if (query != null) {
//            searchView.setQuery(query, true);
//        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_search:
                String query = PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .getString(FlickrFetchr.PREF_SEARCH_QUERY, null);

                getActivity().startSearch(query, true, null, false);
                return true;
            case R.id.menu_item_clear:
                PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                        .putString(FlickrFetchr.PREF_SEARCH_QUERY, null).commit();
                updateItems();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateItems() {

        mCurrentPage = 1;
        mFetchedPage = 0;
        mCurrentPosition = 0;

        mItems = null;

        new FetchItemsTask().execute(mCurrentPage);

        setupAdapter();
    }

}