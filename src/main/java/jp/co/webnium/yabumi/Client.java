package jp.co.webnium.yabumi;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.FileAsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.ResponseHandlerInterface;

import org.apache.http.Header;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import jp.co.webnium.yabumi.app.R;

/**
 * Yabumi API Client
 */
public class Client {
    static private final DateFormat RFC2822_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    static private final DateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US) {{setTimeZone(TimeZone.getTimeZone("UTC"));}};

    private String mBaseUrl;
    private Context mContext;
    private AsyncHttpClient mClient;

    public Client(Context context) {
        this(context, context.getString(R.string.yabumi_api_base));
    }

    public Client(Context context, String baseUrl) {
        mBaseUrl = baseUrl;
        mContext = context;

        mClient = new AsyncHttpClient();
        mClient.setUserAgent(getUserAgent());
    }

    public void upload(Uri imageUri, ResponseHandlerInterface handler) {
        final String url = mBaseUrl + "images.json";

        InputStream img;
        try {
            img = mContext.getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            return;
        }

        RequestParams params = new RequestParams();
        params.put("imagedata", img, "", "application/octet-stream");

        mClient.post(mContext, url, params, handler);
    }

    public void get(Image image, final ResponseHandlerInterface handler) {
        final String url = mBaseUrl + "images/" + image.getFilename();

        if (image.extension == null) {
            loadMetadata(image, new OnMetadataLoadedListener() {
                @Override
                public void onLoaded(Image image) {
                    final String url = mBaseUrl + "images/" + image.getFilename();
                    mClient.get(mContext, url, handler);
                }

                @Override
                public void onNotFound() {
                }
            });

            return;
        }
        mClient.get(mContext, url, handler);
    }

    public RequestHandle getThumbnail(Image image, int sideLength, OnFileLoadedListener listener) {
        final Uri uri = Uri.parse(mBaseUrl + "images/" + image.id + ".png?resize=" + sideLength);

        return getResource(uri, listener);
    }

    public File getCachedThumbnail(Image image, int sideLength) {
        final Uri uri = Uri.parse(mBaseUrl + "images/" + image.id + ".png?resize=" + sideLength);

        return getCacheFileFromUri(uri);
    }

    public void loadMetadata(final Image image, final OnMetadataLoadedListener listener) {
        final String url = mBaseUrl + "images/" + image.id + ".json";

        mClient.get(mContext, url, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    image.extension = response.getString("extension");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                listener.onLoaded(image);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable e) {
                if (statusCode == 404) {
                    listener.onNotFound();
                }
            }
        });
    }

    private String getUserAgent() {
        String versionName;
        try {
            PackageManager packageManager = mContext.getPackageManager();
            PackageInfo info;
            assert packageManager != null;
            info = packageManager.getPackageInfo(mContext.getPackageName(), PackageManager.GET_META_DATA);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "Unknown";
        }

        return String.format(
                "%s/%s (Android %s; %s %s)",
                mContext.getString(R.string.api_client_ua),
                versionName,
                Build.VERSION.RELEASE,
                Build.MANUFACTURER,
                Build.MODEL
        );
    }

    public void delete(Image image, ResponseHandlerInterface handler) {
        final String url = mBaseUrl + "images/" + image.getFilename();

        RequestParams params = new RequestParams();

        params.put("_method", "delete");
        params.put("pin", image.pin);
        mClient.post(mContext, url, params, handler);
    }

    public void changeExpiration(Image image, Calendar expiresAt, ResponseHandlerInterface handler) {
        final String url = mBaseUrl + "images/" + image.id + ".json";
        final String jsonString;

        jsonString = String.format("{\"pin\":\"%s\", \"expiresAt\": %s}", image.pin, expiresAt == null ? "null" : "\"" + RFC2822_DATE_FORMAT.format(expiresAt.getTime()) + "\"");
        try {
            mClient.put(mContext, url, new StringEntity(jsonString), "application/json", handler);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void cancelRequests(boolean mayInterruptIfRunning) {
        mClient.cancelRequests(mContext, mayInterruptIfRunning);
    }

    public void putHistory(String key, Image image) {
        final String url = generateHistoryUrl(key, image);
        final String jsonString = String.format("{\"pin\":\"%s\"}", image.pin);

        try {
            mClient.put(mContext, url, new StringEntity(jsonString), "application/json", new AsyncHttpResponseHandler());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void deleteHistory(String key, Image image) {
        final String url = generateHistoryUrl(key, image);
        mClient.delete(mContext, url, new AsyncHttpResponseHandler());
    }

    private String generateHistoryUrl(String key, Image image) {
        return mBaseUrl + "histories/" + key + "/images/" + image.id + ".json";
    }

    public void getHistories(String key, final ImageListRetrieveCallback callback) {
        final String url = mBaseUrl + "histories/" + key + ".json";

        mClient.get(mContext, url, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, JSONObject response) {
                if (statusCode != 200) {
                    callback.onFailure(statusCode);
                }

                JSONArray jsonImages;
                try {
                    jsonImages = response.getJSONArray("images");
                } catch (JSONException e) {
                    e.printStackTrace();
                    callback.onFailure(503);
                    return;
                }

                final int length = jsonImages.length();
                final ArrayList<Image> imageArrayList = new ArrayList<Image>();
                for (int i = 0; i < length; i++) {
                    Image image;

                    try {
                        final JSONObject object;
                        object = jsonImages.getJSONObject(i);

                        image = new Image();
                        image.id = object.getString("id");
                        image.pin = object.getString("pin");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onFailure(503);
                        return;
                    }

                    imageArrayList.add(image);
                }

                callback.onSuccess(imageArrayList);
            }
        });
    }

    private RequestHandle getResource(final Uri uri, final OnFileLoadedListener listener) {
        final File cacheFile = getCacheFileFromUri(uri);

        Header[] headers = {};

        if (cacheFile.exists()) {
            Log.d("Client", HTTP_DATE_FORMAT.format(cacheFile.lastModified()));
            headers = new Header[]{new BasicHeader("If-Modified-Since", HTTP_DATE_FORMAT.format(cacheFile.lastModified()))};
        } else {
            try {
                cacheFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        final FileAsyncHttpResponseHandler handler = new FileAsyncHttpResponseHandler(cacheFile) {
            @Override
            public void onSuccess(File file) {
                listener.onLoaded(file);
            }

            @Override
            public void onFailure(int statusCode, Throwable e, File response) {
                if (statusCode == 404) {
                    response.delete();
                    listener.onNotFound();
                }
            }
        };

        return mClient.get(mContext, uri.toString(), headers, new RequestParams(), handler);
    }

    private File getCacheFileFromUri(final Uri uri) {
        final File dir = new File(mContext.getCacheDir(), "/resources");
        dir.mkdirs();

        String filename;

        try {
            filename = URLEncoder.encode(uri.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return new File(dir, filename);
    }

    public interface ImageListRetrieveCallback {
        public void onSuccess(ArrayList<Image> images);

        public void onFailure(int statusCode);
    }

    public interface OnFileLoadedListener {
        public void onLoaded(File file);

        public void onNotFound();
    }

    public interface OnMetadataLoadedListener {
        public void onLoaded(Image image);

        public void onNotFound();
    }
}
