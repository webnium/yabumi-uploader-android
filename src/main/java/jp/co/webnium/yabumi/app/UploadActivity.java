package jp.co.webnium.yabumi.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class UploadActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        Intent intent = getIntent();
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        uploadImage(imageUri);
    }

    private void uploadImage(Uri imageUri)
    {
        String url = getString(R.string.yabumi_api_base) + "images.json";
        AsyncHttpClient client = new AsyncHttpClient();
        client.setUserAgent(getUserAgent());

        InputStream img;
        try {
            img = getContentResolver().openInputStream(imageUri);
        } catch (FileNotFoundException e) {
            return;
        }

        RequestParams params = new RequestParams();
        params.put("imagedata", img, "", "application/octet-stream");
        client.post(url, params, new MyAsyncHttpResponseHandler(this));
    }

    private String getUserAgent() {
        String versionName;
        try {
            PackageInfo info;
            info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "Unknown";
        }

        return String.format(
                "%s/%s (Android %s; %s %s)",
                getString(R.string.api_client_ua),
                versionName,
                Build.VERSION.RELEASE,
                Build.MANUFACTURER,
                Build.MODEL
        );
    }

    private class MyAsyncHttpResponseHandler extends JsonHttpResponseHandler {
        private final int STATUS_STARING = 0;
        private final int STATUS_UPLOADING = 1;
        private final int STATUS_RECEIVING = 2;

        private Context mContext;
        private ProgressDialog mProgressDialog;
        private int status = STATUS_STARING;

        public MyAsyncHttpResponseHandler(Context context)
        {
            mContext = context;
        }

        @Override
        public void onStart()
        {
            status = STATUS_STARING;
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage(getString(R.string.start_upload));
            mProgressDialog.show();
        }

        @Override
        public void onProgress(int position, int length) {
            if (status == STATUS_STARING) {
                status = STATUS_UPLOADING;
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setMax(length);
                mProgressDialog.setMessage(getString(R.string.uploading));
            } else if(status == STATUS_UPLOADING && length != mProgressDialog.getMax()) {
                status = STATUS_RECEIVING;
                mProgressDialog.setMax(length);
                mProgressDialog.setMessage(getString(R.string.receiving_response));
            }

            mProgressDialog.setProgress(position);
        }

        @Override
        public void onSuccess(int statusCode, JSONObject response) {
            String editUrl;
            String url;
            try {
                url = response.getString("url");
                editUrl = response.getString("editUrl");
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            Toast.makeText(getBaseContext(), "Uploaded to " + editUrl, Toast.LENGTH_LONG).show();

            ClipData.Item item = new ClipData.Item(url);
            String[] mimeType = new String[1];
            mimeType[0] = ClipDescription.MIMETYPE_TEXT_URILIST;

            ClipData clipData = new ClipData(new ClipDescription("text_data", mimeType), item);
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

            clipboardManager.setPrimaryClip(clipData);

            Intent intent = new Intent(getApplicationContext(), ViewerActivity.class);
            intent.setData(Uri.parse(editUrl));

            startActivity(intent);
        }

        @Override
        public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] responseBody, java.lang.Throwable error) {
            Toast.makeText(getBaseContext(), "Error " + error, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onFinish()
        {
            mProgressDialog.dismiss();
        }
    }
}
