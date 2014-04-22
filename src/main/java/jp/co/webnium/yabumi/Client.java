package jp.co.webnium.yabumi;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.ResponseHandlerInterface;

import java.io.FileNotFoundException;
import java.io.InputStream;

import jp.co.webnium.yabumi.app.R;

/**
 * Yabumi API Client
 */
public class Client {
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

        mClient.post(url, params, handler);
    }

    public void get(Image image, ResponseHandlerInterface handler) {
        final String url = mBaseUrl + "images/" + image.getFilename();
        mClient.get(url, handler);
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
}
