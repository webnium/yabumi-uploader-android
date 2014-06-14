package jp.co.webnium.yabumi.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.FileAsyncHttpResponseHandler;

import org.apache.http.Header;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;

import jp.co.webnium.yabumi.Client;
import jp.co.webnium.yabumi.Image;
import jp.co.webnium.yabumi.app.util.ImageSamplingTask;
import jp.co.webnium.yabumi.app.util.SystemUiHider;
import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class ViewerActivity extends Activity {
    private static final int MAXIMUM_IMAGE_SIZE_IN_BYTE = 5000000;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    /**
     * The image to treat with this activity.
     */
    private Image mImage;

    private PhotoViewAttacher mPhotoViewAttacher;

    private ImageSamplingTask mSetImageTask;

    private ImageView mContentView;

    private ProgressBar mProgressBar;

    private Client mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_viewer);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);

        mContentView = (ImageView) findViewById(R.id.fullscreen_content);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, mContentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.share_button).setOnTouchListener(mDelayHideTouchListener);

        mPhotoViewAttacher = new PhotoViewAttacher(mContentView);
        mPhotoViewAttacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
            @Override
            public void onViewTap(View view, float x, float y) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        mClient = new Client(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.viewer, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (mImage == null) {
            return true;
        }

        MenuItem menuItemDelete = menu.findItem(R.id.action_delete);
        MenuItem menuItemChangeExpiration = menu.findItem(R.id.action_change_expiration);

        if (menuItemDelete != null) menuItemDelete.setVisible(mImage.isOwned());
        if (menuItemChangeExpiration != null) menuItemChangeExpiration.setVisible(mImage.isOwned());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                shareImage();
                return true;
            case R.id.action_delete:
                deleteImage();
                return true;
            case R.id.action_change_expiration:
                changeImageExpiration();
                return true;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteImage() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setTitle(R.string.deleting_image);
        alertDialog.setMessage(R.string.ensure_deleting_image);

        alertDialog.setPositiveButton(R.string.yes_delete, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mClient.delete(mImage, new AsyncHttpResponseHandler() {
                    private ProgressDialog mProgressDialog;

                    @Override
                    public void onStart() {
                        mProgressDialog = new ProgressDialog(ViewerActivity.this);
                        mProgressDialog.setTitle(R.string.deleting_image);
                        mProgressDialog.show();
                    }

                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        AlertDialog.Builder completeMessageDialog = new AlertDialog.Builder(ViewerActivity.this);
                        completeMessageDialog.setTitle(R.string.done_deletion);
                        completeMessageDialog.setMessage(R.string.done_deletion_message);
                        completeMessageDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        });
                        completeMessageDialog.show();
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        AlertDialog.Builder completeMessageDialog = new AlertDialog.Builder(ViewerActivity.this);
                        completeMessageDialog.setTitle(R.string.fail_deletion);
                        completeMessageDialog.setMessage(R.string.fail_deletion_message);
                        completeMessageDialog.show();
                    }

                    @Override
                    public void onFinish() {
                        mProgressDialog.dismiss();
                    }
                });
            }
        });

        alertDialog.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d("ViewerActivity", "No, I don't delete image");
            }
        });

        alertDialog.show();
    }

    private void changeImageExpiration() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setItems(R.array.expiry_options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
                Calendar expiresAt = null;
                int[] values = getResources().getIntArray(R.array.expiry_option_values);
                if (values.length > i) {
                    expiresAt = Calendar.getInstance(Locale.US);
                    expiresAt.add(Calendar.SECOND, values[i]);
                }

                mClient.changeExpiration(mImage, expiresAt, new AsyncHttpResponseHandler() {
                    private ProgressDialog mProgressDialog;

                    @Override
                    public void onStart() {
                        mProgressDialog = new ProgressDialog(ViewerActivity.this);
                        mProgressDialog.setTitle(R.string.changing_expiration);
                        mProgressDialog.show();
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        Toast.makeText(ViewerActivity.this, R.string.fail_change_expiration_message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFinish() {
                        mProgressDialog.dismiss();
                    }
                });
            }
        });

        alertDialog.show();
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void onTapShareButton(View view) {
        shareImage();
    }

    private void shareImage() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, mImage.getUrl());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, getText(R.string.share_image_via)));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPhotoViewAttacher.cleanup();

        mClient.cancelRequests(true);
        cancelSetImageTask();
    }

    private void cancelSetImageTask() {
        if (mSetImageTask != null) {
            mSetImageTask.cancel(true);
        }
    }

    private void handleIntent(Intent intent) {
        Uri uri;
        uri = intent.getData();

        mImage = Image.fromUri(uri);

        if (!mImage.isAvailable()) {
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            return;
        }

        loadImage();
    }

    private void loadImage() {
        mContentView.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        mClient.get(mImage, new FileAsyncHttpResponseHandler(this) {
            @Override
            public void onSuccess(File file) {
                setImage(file);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] binaryData, Throwable error) {
                Toast.makeText(ViewerActivity.this, error.toString(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void setImage(File file) {
        cancelSetImageTask();

        final WeakReference<ImageView> imageViewWeakRef = new WeakReference<ImageView>(mContentView);
        final WeakReference<PhotoViewAttacher> photoViewAttacherWeakReference = new WeakReference<PhotoViewAttacher>(mPhotoViewAttacher);
        final WeakReference<ProgressBar> progressBarWeakReference = new WeakReference<ProgressBar>(mProgressBar);
        mSetImageTask = new ImageSamplingTask(MAXIMUM_IMAGE_SIZE_IN_BYTE) {
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) return;

                final ImageView imageView = imageViewWeakRef.get();
                final PhotoViewAttacher photoViewAttacher = photoViewAttacherWeakReference.get();
                final ProgressBar progressBar = progressBarWeakReference.get();

                if (imageView == null || photoViewAttacher == null || progressBar == null) {
                    return;
                }

                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                photoViewAttacher.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                progressBar.setVisibility(View.GONE);
            }
        };

        mSetImageTask.execute(file.getAbsolutePath());
    }
}
