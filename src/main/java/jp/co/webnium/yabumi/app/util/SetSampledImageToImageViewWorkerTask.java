package jp.co.webnium.yabumi.app.util;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * SetSampledImageToImageViewWorkerTask
 * <p/>
 * refs http://developer.android.com/training/displaying-bitmaps/process-bitmap.html
 */
public class SetSampledImageToImageViewWorkerTask extends AsyncTask<String, Void, Bitmap> {
    private final WeakReference<ImageView> imageViewReference;
    private final WeakReference<PhotoViewAttacher> photoViewAttacherReference;
    private int mMaximumSize;

    public SetSampledImageToImageViewWorkerTask(ImageView imageView, PhotoViewAttacher photoViewAttacher, int maximumSize) {
        // Use a WeakReference to ensure the ImageView can be garbage collected
        imageViewReference = new WeakReference<ImageView>(imageView);
        photoViewAttacherReference = new WeakReference<PhotoViewAttacher>(photoViewAttacher);
        mMaximumSize = maximumSize;
    }

    // Decode image in background.
    @Override
    protected Bitmap doInBackground(String... params) {
        return decodeSampledBitmapFromFile(params[0], mMaximumSize);
    }

    // Once complete, see if ImageView is still around and set bitmap.
    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap != null) {
            final ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }

            final PhotoViewAttacher photoViewAttacher = photoViewAttacherReference.get();
            if (photoViewAttacher != null) {
                photoViewAttacher.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            }
        }
    }

    private static Bitmap decodeSampledBitmapFromFile(String file, int maximumSize) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, maximumSize);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file, options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int maximumSize) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height * width * 2 > maximumSize) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) * (halfWidth / inSampleSize) * 2 > maximumSize) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}