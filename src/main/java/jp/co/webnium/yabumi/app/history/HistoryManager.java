package jp.co.webnium.yabumi.app.history;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Iterator;
import java.util.Map;

import jp.co.webnium.yabumi.Client;
import jp.co.webnium.yabumi.Image;

/**
 * History Manager class.
 * <p/>
 * Created by Koichi on 2014/06/15.
 */
public abstract class HistoryManager {
    final static String PREF_HISTORY_SYNCING = "history_syncing";
    final static String PREF_HISTORY_SYNCING_KEY = "history_syncing_key";

    public static HistoryManager create(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        String syncingKey = preferences.getString(PREF_HISTORY_SYNCING_KEY, "");

        if (!syncingKey.isEmpty() && preferences.getBoolean(PREF_HISTORY_SYNCING, false)) {
            return new SyncingHistoryManager(context, syncingKey);
        }

        return new LocalHistoryManager(context);
    }

    /**
     * Add new image
     *
     * @param image an image to add to the history.
     */
    abstract public void add(Image image);

    abstract public Iterator<Image> getAll();

    /**
     * Local history manager.
     * <p/>
     * This class provides non-syncing history.
     */
    public static class LocalHistoryManager extends HistoryManager {
        final static String HISTORY_PREFERENCE_NAME = "localHistory";

        private SharedPreferences mHistory;

        protected LocalHistoryManager(Context context) {
            mHistory = context.getSharedPreferences(HISTORY_PREFERENCE_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public void add(Image image) {

            if (!image.isOwned()) {
                // ignore not owned images.
                return;
            }

            SharedPreferences.Editor editor = mHistory.edit();
            editor.putString(image.id, image.pin);
            editor.commit();
        }

        @Override
        public Iterator<Image> getAll() {
            final Iterator<? extends Map.Entry<String, ?>> iterator;

            try {
                //noinspection ConstantConditions
                iterator = mHistory.getAll().entrySet().iterator();
            } catch (NullPointerException e) {
                return null;
            }

            return new Iterator<Image>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Image next() {
                    Image image = new Image();
                    Map.Entry<String, ?> entry = iterator.next();
                    image.id = entry.getKey();
                    image.pin = (String) entry.getValue();

                    return image;
                }

                @Override
                public void remove() {
                    /* do nothing, don't remove element with this iterator */
                }
            };
        }
    }

    /**
     * Syncing History Manager
     * <p/>
     * This class provides syncing history.
     */
    public static class SyncingHistoryManager extends HistoryManager {
        private String mKey;
        private Client mClient;
        private LocalHistoryManager mLocalHistory;

        protected SyncingHistoryManager(Context context, String key) {
            mKey = key;
            mClient = new Client(context);
            mLocalHistory = new LocalHistoryManager(context);
        }

        @Override
        public void add(Image image) {

            if (!image.isOwned()) {
                // ignore not owned images.
                return;
            }

            mLocalHistory.add(image); // Add to local history too.
            mClient.putHistory(mKey, image);
        }

        @Override
        public Iterator<Image> getAll() {
            return null;
        }
    }
}
