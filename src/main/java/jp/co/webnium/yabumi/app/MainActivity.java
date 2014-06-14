package jp.co.webnium.yabumi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import jp.co.webnium.yabumi.Client;
import jp.co.webnium.yabumi.Image;

public class MainActivity extends ActionBarActivity {

    static final int CAPTURE_IMAGE_REQUEST = 1;
    static final int GET_CONTENT_REQUEST = 2;
    static final String CAPTURE_IMAGE_MIME_TYPE = "image/jpeg";

    private Uri mCapturedImageUri;
    private ProgressDialog mProgressDialog;
    private Client mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mClient = new Client(this);

        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mClient.cancelRequests(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }

        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        uploadImage(imageUri);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onClickUploadButton(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(Intent.createChooser(intent, "Select to upload."), GET_CONTENT_REQUEST);
    }

    public void onClickCameraButton(View view) {
        String filename = System.currentTimeMillis() + ".jpg";

        ContentValues capturedImageUriValues = new ContentValues();
        capturedImageUriValues.put(MediaStore.Images.Media.TITLE, filename);
        capturedImageUriValues.put(MediaStore.Images.Media.MIME_TYPE, CAPTURE_IMAGE_MIME_TYPE);
        mCapturedImageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, capturedImageUriValues);

        if (mCapturedImageUri == null) {
            Log.e("MainActivity", "Capture image url creation failed.");
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,  mCapturedImageUri);

        startActivityForResult(intent, CAPTURE_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CAPTURE_IMAGE_REQUEST:
                handleCaptureResult(resultCode, data);
                break;
            case GET_CONTENT_REQUEST:
                handleGetContentResult(resultCode, data);
                break;
        }
    }

    private void handleGetContentResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        Uri imageUri = data.getData();

        final String mimeType = getContentResolver().getType(imageUri);
        if (!Arrays.asList(Image.AVAILABLE_CONTENT_TYPES).contains(mimeType)) {
            String message = getString(R.string.supported_file_types, TextUtils.join(", ", Image.AVAILABLE_CONTENT_TYPES));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.unsupported_mime_type)
                    .setMessage(message)
                    .setCancelable(true)
                    .show();
            return;
        }
        uploadImage(imageUri);
    }

    private void handleCaptureResult(int resultCode, Intent data)
    {
        if (resultCode != Activity.RESULT_OK) {
            // Capture is failed.
            // Delete image for capture because it's a just garbage.
            getContentResolver().delete(mCapturedImageUri, null, null);
            return;
        }

        uploadImage(mCapturedImageUri);
    }

    private void uploadImage(Uri imageUri) {
        if (imageUri == null) {
            Log.e("MainActivity", "Image url is null when uploading image.");
            return;
        }
        mClient.upload(imageUri, new MyAsyncHttpResponseHandler());
    }

    private class MyAsyncHttpResponseHandler extends JsonHttpResponseHandler {
        @Override
        public void onStart() {
            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage(getString(R.string.uploading));
            mProgressDialog.show();
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
        public void onFinish() {
            mProgressDialog.dismiss();
        }
    }
}
