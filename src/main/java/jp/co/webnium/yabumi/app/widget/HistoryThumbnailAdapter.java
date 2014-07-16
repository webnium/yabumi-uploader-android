package jp.co.webnium.yabumi.app.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import com.loopj.android.http.RequestHandle;

import java.io.File;
import java.lang.ref.WeakReference;

import jp.co.webnium.yabumi.Client;
import jp.co.webnium.yabumi.Image;
import jp.co.webnium.yabumi.app.R;
import jp.co.webnium.yabumi.app.history.HistoryManager;

/**
 * ListAdapter for History thumbnails
 *
 * Created by Koichi on 2014/07/12.
 */
public class HistoryThumbnailAdapter extends ArrayAdapter<Image> {
    private int mResource;
    private Client mClient;
    private Bitmap mLoadingBitmap;
    private HistoryManager mHistoryManager;
    private OnClickThumbnailListener mOnClickThumbnailListener;

    public HistoryThumbnailAdapter(Context context, int resource, HistoryManager historyManager, Client client) {
        super(context, resource, historyManager.getArrayList());
        mResource = resource;
        mClient = client;
        mLoadingBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.loading);
        mHistoryManager = historyManager;
    }

    public void setOnClickThumbnailListener(OnClickThumbnailListener listener) {
        mOnClickThumbnailListener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(mResource, null);
        } else {
            prepareForReuseView((ImageView) convertView.findViewById(R.id.historyThumbnailImage));
        }

        final Image image = getItem(position);
        final View view = convertView;
        final ImageView imageView = (ImageView) view.findViewById(R.id.historyThumbnailImage);
        setImageToView(image, imageView);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mOnClickThumbnailListener == null) {
                    return;
                }

                mOnClickThumbnailListener.onClickThumbnail(image);
            }
        });
        view.setClickable(true);

        return view;
    }

    private void prepareForReuseView(final ImageView convertView) {
        final Drawable drawable = convertView.getDrawable();
        if (!(drawable instanceof AsyncDrawable)) {
            return;
        }

        RequestHandle requestHandle = ((AsyncDrawable) drawable).getRequestHandle();
        if (requestHandle != null) {
            requestHandle.cancel(true);
        }
    }

    private void setImageToView(final Image image, ImageView view) {
        if (viewHasDrawableForImage(view, image)) {
            return;
        }

        final Resources res = getContext().getResources();
        final WeakReference<ImageView> viewReference = new WeakReference<ImageView>(view);
        final RequestHandle requestHandle = mClient.getThumbnail(
                image,
                res.getDimensionPixelSize(R.dimen.history_thumbnail_width),
                res.getDimensionPixelSize(R.dimen.history_thumbnail_height),
                new Client.OnFileLoadedListener() {

                    @Override
                    public void onLoaded(File file) {
                        ImageView view = viewReference.get();
                        if (view == null) return;

                        view.setImageDrawable(new ImageDrawable(view.getResources(), BitmapFactory.decodeFile(file.getAbsolutePath()), image));
                    }

                    @Override
                    public void onNotFound() {
                        mHistoryManager.remove(image);
                    }
                }
        );

        final AsyncDrawable drawable = new AsyncDrawable(res, mLoadingBitmap, image, requestHandle);
        view.setImageDrawable(drawable);
    }

    private boolean viewHasDrawableForImage(ImageView view, Image image) {
        final Drawable drawable = view.getDrawable();
        return drawable instanceof ImageDrawable && ((ImageDrawable) drawable).getImage().equals(image);
    }

    private static class ImageDrawable extends BitmapDrawable {
        private final Image mImage;

        public ImageDrawable(Resources resources, Bitmap bitmap, Image image) {
            super(resources, bitmap);
            mImage = image;
        }

        public Image getImage() {
            return mImage;
        }
    }

    private static class AsyncDrawable extends ImageDrawable {
        private final WeakReference<RequestHandle> mRequestWeakRef;

        public AsyncDrawable(Resources resources, Bitmap bitmap, Image image, RequestHandle requestHandle) {
            super(resources, bitmap, image);
            mRequestWeakRef = new WeakReference<RequestHandle>(requestHandle);
        }

        public RequestHandle getRequestHandle() {
            return mRequestWeakRef.get();
        }

    }

    public interface OnClickThumbnailListener {
        public void onClickThumbnail(Image image);
    }
}
