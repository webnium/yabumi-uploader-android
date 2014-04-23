package jp.co.webnium.yabumi.app.util;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

/**
 * ImageSamplingTask
 * <p/>
 * refs http://developer.android.com/training/displaying-bitmaps/process-bitmap.html
 */
public class ImageSamplingTask extends AsyncTask<String, Void, Bitmap> {
    private int mMaximumSize;

    public ImageSamplingTask(int maximumSize) {
        mMaximumSize = maximumSize;
    }

    // Decode image in background.
    @Override
    protected Bitmap doInBackground(String... params) {
        return decodeSampledBitmapFromFile(params[0], mMaximumSize);
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