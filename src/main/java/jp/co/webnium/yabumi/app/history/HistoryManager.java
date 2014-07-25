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

    public static HistoryManager getInstance(Context context) {
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
     * @param callback the callback
     */
    abstract public void sync(SyncingCallback callback);

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
     * @return list of images.
     */
    abstract public ArrayList<Image> getArrayList();

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

        private Context mContext;
        private final ArrayList<Image> mHistoryList = new ArrayList<Image>();

        protected LocalHistoryManager(Context context) {
            mContext = context;
            loadHistoryList();
        }

        @Override
        public void add(Image image) {

            if (!image.isOwned()) {
                // ignore not owned images.
                return;
            }

            SharedPreferences.Editor editor = mContext.getSharedPreferences(HISTORY_PREFERENCE_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(image.id, image.pin);
            editor.commit();
        }

        @Override
        public void remove(Image image) {
            SharedPreferences.Editor editor = mContext.getSharedPreferences(HISTORY_PREFERENCE_NAME, Context.MODE_PRIVATE).edit();
            editor.remove(image.id);
            editor.commit();
            mHistoryList.remove(image);
        }

        @Override
        public void sync(SyncingCallback callback) {
            loadHistoryList();
            callback.onSynced(this);
        }

        private void loadHistoryList() {
            Map<String, ?> historyMap = mContext.getSharedPreferences(HISTORY_PREFERENCE_NAME, Context.MODE_PRIVATE).getAll();

            mHistoryList.clear();
            for (Map.Entry<String, ?> e : historyMap.entrySet()) {
                Image image = new Image();
                image.id = e.getKey();
                image.pin = (String) e.getValue();
                mHistoryList.add(image);
            }

            Collections.sort(mHistoryList, Collections.reverseOrder());
        }

        @Override
        public ArrayList<Image> getArrayList() {
            return mHistoryList;
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
        public ArrayList<Image> getArrayList() {
            return mLocalHistory.getArrayList();
        }

        @Override
        public void sync(final SyncingCallback callback) {
            mClient.getHistories(mKey, new Client.ImageListRetrieveCallback() {
                @Override
                public void onSuccess(ArrayList<Image> remoteHistory) {
                    ArrayList<Image> localHistory = mLocalHistory.getArrayList();

                    Collections.sort(remoteHistory, Collections.reverseOrder());

                    final Iterator<Image> localItr = localHistory.listIterator();
                    final Iterator<Image> remoteItr = remoteHistory.listIterator();

                    Image local = tryGetNext(localItr);
                    Image remote = tryGetNext(remoteItr);
                    while (local != null || remote != null) {

                        if (local == null) {
                            mLocalHistory.add(remote);
                            remote = tryGetNext(remoteItr);
                            continue;
                        }

                        if (remote == null) {
                            mClient.putHistory(mKey, local);
                            local = tryGetNext(localItr);
                            continue;
                        }

                        final int compareState = local.compareTo(remote);
                        if (compareState < 0) {
                            mClient.putHistory(mKey, local);
                            local = tryGetNext(localItr);
                            continue;
                        }

                        if (compareState == 0) {
                            local = tryGetNext(localItr);
                            remote = tryGetNext(remoteItr);
                            continue;
                        }

                        mLocalHistory.add(remote);
                        remote = tryGetNext(remoteItr);
                    }

                    mLocalHistory.sync(new SyncingCallback() {
                        @Override
                        public void onSynced(HistoryManager manager) {
                            callback.onSynced(SyncingHistoryManager.this);
                        }

                        @Override
                        public void onFailure(HistoryManager manager) {
                            callback.onFailure(SyncingHistoryManager.this);
                        }
                    });
                }

                @Override
                public void onFailure(int statusCode) {
                    callback.onFailure(SyncingHistoryManager.this);
                }
            });
        }

        static private Image tryGetNext(Iterator<Image> it) {
            return it.hasNext() ? it.next() : null;
        }
    }
}
