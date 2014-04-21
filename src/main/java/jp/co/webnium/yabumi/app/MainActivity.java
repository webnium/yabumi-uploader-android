package jp.co.webnium.yabumi.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import jp.co.webnium.yabumi.Image;

public class MainActivity extends ActionBarActivity {

    static final int CAPTURE_IMAGE_REQUEST = 1;
    static final int GET_CONTENT_REQUEST = 2;
    static final String CAPTURE_IMAGE_MIME_TYPE = "image/jpeg";

    private Uri mCapturedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onClickUploadButton(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(TextUtils.join(";", Image.AVAILABLE_CONTENT_TYPES));
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

        Intent intent = new Intent(getApplicationContext(), UploadActivity.class);
        intent.putExtra(Intent.EXTRA_STREAM, data.getData());
        startActivity(intent);
    }

    private void handleCaptureResult(int resultCode, Intent data)
    {
        if (resultCode != Activity.RESULT_OK) {
            // Capture is failed.
            // Delete image for capture because it's a just garbage.
            getContentResolver().delete(mCapturedImageUri, null, null);
            return;
        }

        Intent intent = new Intent(getApplicationContext(), UploadActivity.class);
        intent.putExtra(Intent.EXTRA_STREAM, mCapturedImageUri);
        intent.setType(CAPTURE_IMAGE_MIME_TYPE);
        startActivity(intent);
    }
}
