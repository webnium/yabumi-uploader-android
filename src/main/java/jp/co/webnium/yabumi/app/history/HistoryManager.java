package jp.co.webnium.yabumi.app.history;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
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
     * Sync the history.
     *
     * @param callback
     */
    public void sync(SyncingCallback callback) {
        callback.onSynced(this);
    }

    /**
     * Add new image
     *
     * @param image an image to add to the history.
     */
    abstract public void add(Image image);

    abstract public void remove(Image image);

    /**
     * Get images in the history
     *
     * @return
     */
    abstract public ArrayList<Image> getAll();

    /**
     * Callback for syncing
     */
    public interface SyncingCallback {
        public void onSynced(HistoryManager manager);

        public void onFailure(HistoryManager manager);
    }

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
        public void remove(Image image) {
            SharedPreferences.Editor editor = mHistory.edit();
            editor.remove(image.id);
            editor.commit();
        }

        @Override
        public ArrayList<Image> getAll() {
            ArrayList<Image> historyList = new ArrayList<Image>();
            Map<String, ?> historyMap = mHistory.getAll();

            for (Map.Entry<String, ?> e : historyMap.entrySet()) {
                Image image = new Image();
                image.id = e.getKey();
                image.pin = (String) e.getValue();
                historyList.add(image);
            }

            Collections.sort(historyList, Collections.reverseOrder());

            return historyList;
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
        public void remove(Image image) {
            mLocalHistory.remove(image);
            mClient.deleteHistory(mKey, image);
        }

        @Override
        public ArrayList<Image> getAll() {
            return mLocalHistory.getAll();
        }

        @Override
        public void sync(final SyncingCallback callback) {
            mClient.getHistories(mKey, new Client.ImageListRetrieveCallback() {
                @Override
                public void onSuccess(ArrayList<Image> remoteHistory) {
                    ArrayList<Image> localHistory = mLocalHistory.getAll();

                    Collections.sort(remoteHistory, Collections.reverseOrder());

                    final Iterator<Image> localItr = localHistory.listIterator();
                    final Iterator<Image> remoteItr = remoteHistory.listIterator();

                    Image local = localItr.next();
                    Image remote = remoteItr.next();
                    while (local != null || remote != null) {

                        if (local == null) {
                            mLocalHistory.add(remote);
                            remote = remoteItr.next();
                            continue;
                        }

                        if (remote == null) {
                            mClient.putHistory(mKey, local);
                            local = localItr.next();
                            continue;
                        }

                        switch (local.compareTo(remote)) {
                            case 1:
                                mClient.putHistory(mKey, local);
                                local = localItr.next();
                                break;
                            case 0:
                                local = localItr.next();
                                remote = remoteItr.next();
                                break;
                            case -1:
                                mLocalHistory.add(remote);
                                remote = remoteItr.next();
                                break;
                        }
                    }

                    callback.onSynced(SyncingHistoryManager.this);
                }

                @Override
                public void onFailure(int statusCode) {
                    callback.onFailure(SyncingHistoryManager.this);
                }
            });
        }
    }
}
