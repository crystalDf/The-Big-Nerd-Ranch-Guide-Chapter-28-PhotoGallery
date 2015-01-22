package com.star.photogallery;


import android.net.Uri;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class FlickrFetchr {

    public static final String TAG = "FlickrFetchr";

    public static final String PREF_SEARCH_QUERY = "searchQuery";

    private static final String ENDPOINT = "https://api.flickr.com/services/rest/";
    private static final String API_KEY = "03e55c312c15c20d0b02b48dbf58e646";
    private static final String METHOD_GET_RECENT = "flickr.photos.getRecent";
    private static final String METHOD_SEARCH = "flickr.photos.search";
    private static final String PARAM_EXTRAS = "extras";
    private static final String PARAM_TEXT = "text";
    private static final String EXTRA_SMALL_URL = "url_s";
    private static final String PAGE = "page";

    private static final String XML_PHOTO = "photo";
    private static final String XML_PHOTOS = "photos";

    private String mTotalSearch;

    public String getTotalSearch() {
        return mTotalSearch;
    }

    public byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            int bytesRead;

            byte[] buffer = new byte[1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }

            out.close();

            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    public String getUrl(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    public ArrayList<GalleryItem> downloadGalleryItems(String url) {
        ArrayList<GalleryItem> items = new ArrayList<>();

        try {

            String xmlString = getUrl(url);

            Log.i(TAG, "Received xml: " + xmlString);

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();

            parser.setInput(new StringReader(xmlString));

            parseItems(items, parser);

        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch items: " + e);
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Failed to parse items: " + e);
            e.printStackTrace();
        }

        return items;
    }

    public ArrayList<GalleryItem> fetchItems(int page) {

        String url = Uri.parse(ENDPOINT).buildUpon()
                .appendQueryParameter("method", METHOD_GET_RECENT)
                .appendQueryParameter("api_key", API_KEY)
                .appendQueryParameter(PARAM_EXTRAS, EXTRA_SMALL_URL)
                .appendQueryParameter(PAGE, (page + ""))
                .build().toString();

        return downloadGalleryItems(url);

    }

    public ArrayList<GalleryItem> search(String query, int page) {

        String url = Uri.parse(ENDPOINT).buildUpon()
                .appendQueryParameter("method", METHOD_SEARCH)
                .appendQueryParameter("api_key", API_KEY)
                .appendQueryParameter(PARAM_EXTRAS, EXTRA_SMALL_URL)
                .appendQueryParameter(PARAM_TEXT, query)
                .appendQueryParameter(PAGE, (page + ""))
                .build().toString();

        return downloadGalleryItems(url);

    }

    private void parseItems(ArrayList<GalleryItem> items, XmlPullParser parser)
            throws IOException, XmlPullParserException {

        int eventType = parser.next();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG &&
                    XML_PHOTO.equals(parser.getName())) {
                String id = parser.getAttributeValue(null, "id");
                String caption = parser.getAttributeValue(null, "title");
                String smallUrl = parser.getAttributeValue(null, EXTRA_SMALL_URL);
                GalleryItem item = new GalleryItem();
                item.setId(id);
                item.setCaption(caption);
                item.setUrl(smallUrl);
                items.add(item);
            } else if (eventType == XmlPullParser.START_TAG &&
                    XML_PHOTOS.equals(parser.getName())) {
                mTotalSearch = parser.getAttributeValue(null, "total");
            }
            eventType = parser.next();
        }
    }
}
