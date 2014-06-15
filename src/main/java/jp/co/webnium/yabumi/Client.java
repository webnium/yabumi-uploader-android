package jp.co.webnium.yabumi;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.ResponseHandlerInterface;

import org.apache.http.entity.StringEntity;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import jp.co.webnium.yabumi.app.R;

/**
 * Yabumi API Client
 */
public class Client {
    static private final DateFormat RFC2822_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

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

    public void get(Image image, ResponseHandlerInterface handler) {
        final String url = mBaseUrl + "images/" + image.getFilename();
        mClient.get(mContext, url, handler);
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
        final String url = mBaseUrl + "histories/" + key + "/images/" + image.id + ".json";
        final String jsonString = String.format("{\"pin\":\"%s\"}", image.pin);

        try {
            mClient.put(mContext, url, new StringEntity(jsonString), "application/json", new AsyncHttpResponseHandler());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
