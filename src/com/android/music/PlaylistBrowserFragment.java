/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.music;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.Fragment;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.music.MusicUtils.Defs;
import com.android.music.MusicUtils.ServiceToken;

public class PlaylistBrowserFragment extends Fragment implements
        View.OnCreateContextMenuListener, MusicUtils.Defs {
    private static final String TAG = "PlaylistBrowserActivity";
    private static final int DELETE_PLAYLIST = CHILD_MENU_BASE + 1;
    private static final int EDIT_PLAYLIST = CHILD_MENU_BASE + 2;
    private static final int RENAME_PLAYLIST = CHILD_MENU_BASE + 3;
    private static final int CHANGE_WEEKS = CHILD_MENU_BASE + 4;
    private static final int CLEAR_ALL_PLAYLISTS = CHILD_MENU_BASE + 5;
    private static final long RECENTLY_ADDED_PLAYLIST = -1;
    private static final long ALL_SONGS_PLAYLIST = -2;
    private static final long PODCASTS_PLAYLIST = -3;
    private PlaylistListAdapter mAdapter;
    private boolean mAdapterSent;
    private static int mLastListPosCourse = -1;
    private static int mLastListPosFine = -1;
    private CharSequence mTitle;
    private boolean mCreateShortcut;
    private ServiceToken mToken;
    private GridView mGridView;
    private static MediaPlaybackActivity parentActivity;
    private TextView sdErrorMessageView;
    private View sdErrorMessageIcon;
    private BitmapDrawable mDefaultAlbumIcon;
    private String[] mPlaylistMemberCols;
    private String[] mPlaylistMemberCols1;
    private Toolbar mToolbar;
    static final LruCache<Integer, Bitmap[]> playlistMap = new LruCache<Integer, Bitmap[]>(
            10);

    public PlaylistBrowserFragment() {
    }

    public Activity getParentActivity() {
        return parentActivity;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        parentActivity = (MediaPlaybackActivity) activity;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        parentActivity = (MusicBrowserActivity) getActivity();
        final Intent intent = parentActivity.getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            mCreateShortcut = true;
        }
        mPlaylistMemberCols = new String[] {
                MediaStore.Audio.Playlists.Members._ID,
                MediaStore.Audio.Media.ARTIST };
        mPlaylistMemberCols1 = new String[] {
			MediaStore.Audio.Media.ALBUM_ID };
        parentActivity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mToken = MusicUtils.bindToService(parentActivity,
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName classname,
                            IBinder obj) {
                        if (Intent.ACTION_VIEW.equals(action)) {
                            Bundle b = intent.getExtras();
                            if (b == null) {
                                Log.w(TAG,
                                        "Unexpected:getExtras() returns null.");
                            } else {
                                try {
                                    long id = Long.parseLong(b
                                            .getString("playlist"));
                                    if (id == RECENTLY_ADDED_PLAYLIST) {
                                        playRecentlyAdded();
                                    } else if (id == PODCASTS_PLAYLIST) {
                                        playPodcasts();
                                    } else if (id == ALL_SONGS_PLAYLIST) {
                                        long[] list = MusicUtils
                                                .getAllSongs(parentActivity);
                                        if (list != null) {
                                            MusicUtils.playAll(parentActivity,
                                                    list, 0);
                                        } else {
                                            Toast.makeText(parentActivity,
                                                    R.string.list_empty,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        MusicUtils.playPlaylist(parentActivity,
                                                id);
                                    }
                                } catch (NumberFormatException e) {
                                    Log.w(TAG, "Playlist id missing or broken");
                                }
                            }
                            parentActivity.finish();
                            return;
                        }
                        ((MediaPlaybackActivity) parentActivity)
                                .updateNowPlaying(getParentActivity());
                    }

                    public void onServiceDisconnected(ComponentName classname) {
                    }
                });
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addDataScheme("file");
        parentActivity.registerReceiver(mScanListener, f);
        Resources r = getResources();
        mDefaultAlbumIcon = (BitmapDrawable) r
                .getDrawable(R.drawable.unknown_albums);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        arrangeGridColums(newConfig);
    }

    public void arrangeGridColums(Configuration newConfig) {
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mGridView.setNumColumns(3);
        } else {
            mGridView.setNumColumns(2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.media_picker_fragment_album,
                container, false);
        sdErrorMessageView = (TextView) rootView.findViewById(R.id.sd_message);
        sdErrorMessageIcon = rootView.findViewById(R.id.sd_icon);
        mToolbar = (Toolbar) parentActivity.findViewById(R.id.music_tool_bar);
        mGridView = (GridView) rootView.findViewById(R.id.album_list);
        mGridView.setTextFilterEnabled(true);
        arrangeGridColums(parentActivity.getResources().getConfiguration());
        mGridView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                com.android.music.PlaylistBrowserFragment.PlaylistListAdapter.ViewHolder vh = (com.android.music.PlaylistBrowserFragment.PlaylistListAdapter.ViewHolder) view
                        .getTag();
                mToolbar.setTitle(vh.tv.getText().toString());
                Fragment fragment = null;
                fragment = new TrackBrowserFragment();
                Bundle args = new Bundle();
                if (mCreateShortcut) {
                    final Intent shortcut = new Intent();
                    shortcut.setAction(Intent.ACTION_EDIT);
                    shortcut.setDataAndType(Uri.EMPTY,
                            "vnd.android.cursor.dir/playlist");
                    shortcut.putExtra("playlist", String.valueOf(id));

                    final Intent intent = new Intent();
                    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
                    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                            ((TextView) view.findViewById(R.id.line1))
                                    .getText());
                    intent.putExtra(
                            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource
                                    .fromContext(
                                            parentActivity,
                                            R.drawable.ic_launcher_shortcut_music_playlist));

                    parentActivity.setResult(parentActivity.RESULT_OK, intent);
                    parentActivity.finish();
                    return;
                }
                if (id == RECENTLY_ADDED_PLAYLIST) {
                    args.putString("playlist", "recentlyadded");
                    fragment.setArguments(args);
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_page, fragment,
                                    "track_fragment").commit();
                } else if (id == PODCASTS_PLAYLIST) {
                    args.putString("playlist", "podcasts");
                    fragment.setArguments(args);
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_page, fragment,
                                    "track_fragment").commit();
                } else {
                    args.putBoolean("editValue", true);
                    args.putString("playlist", Long.valueOf(id).toString());
                    fragment.setArguments(args);
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_page, fragment,
                                    "track_fragment").commit();
                }
                MusicUtils.navigatingTabPosition = 3;

            }
        });
        if (mAdapter == null) {
            mAdapter = new PlaylistListAdapter(parentActivity.getApplication(),
                    this, R.layout.track_list_common_playlist, mPlaylistCursor,
                    new String[] { MediaStore.Audio.Playlists.NAME },
                    new int[] { android.R.id.text1 });
            mGridView.setAdapter(mAdapter);
            getPlaylistCursor(mAdapter.getQueryHandler(), null);
        } else {
            mAdapter.setFragment(this);
            mGridView.setAdapter(mAdapter);
            mPlaylistCursor = mAdapter.getCursor();
            // If mPlaylistCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mPlaylistCursor != null) {
                init(mPlaylistCursor);
            } else {
                getPlaylistCursor(mAdapter.getQueryHandler(), null);
            }
        }
        return rootView;
    }

    @Override
    public void onDestroy() {
        if (mGridView != null) {
            mLastListPosCourse = mGridView.getFirstVisiblePosition();
            View cv = mGridView.getChildAt(0);
            if (cv != null) {
                mLastListPosFine = cv.getTop();
            }
        }
        MusicUtils.unbindFromService(mToken);
        // If we have an adapter and didn't send it off to another activity yet,
        // we should
        // close its cursor, which we do by assigning a null cursor to it. Doing
        // this
        // instead of closing the cursor directly keeps the framework from
        // accessing
        // the closed cursor later.
        if (!mAdapterSent && mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        mGridView.setAdapter(null);
        mAdapter = null;
        parentActivity.unregisterReceiver(mScanListener);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        parentActivity.registerReceiver(mTrackListListener, f);
        mTrackListListener.onReceive(null, null);
        MusicUtils.setSpinnerState(parentActivity);
        // When system language is changed, the name of "Recently added" is also
        // changed
        // at the same time. So we should update the cursor to refresh the
        // listview.
        if (mAdapter != null) {
            getPlaylistCursor(mAdapter.getQueryHandler(), null);
        }
    }

    @Override
    public void onPause() {
        parentActivity.unregisterReceiver(mTrackListListener);
        mReScanHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mGridView.invalidateViews();
            ((MediaPlaybackActivity) parentActivity)
                    .updateNowPlaying(getParentActivity());
        }
    };
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.setSpinnerState(parentActivity);
            mReScanHandler.sendEmptyMessage(0);
        }
    };

    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getPlaylistCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };

    public void init(Cursor cursor) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(cursor);
        if (mPlaylistCursor == null) {
            displayDatabaseError();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }
        // restore previous position
        if (mLastListPosCourse >= 0) {
            mGridView.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
            mLastListPosCourse = -1;
        }
        hideDatabaseError();
    }

    public void displayDatabaseError() {

        String status = Environment.getExternalStorageState();
        int title, message;

        if (android.os.Environment.isExternalStorageRemovable()) {
            title = R.string.sdcard_error_title;
            message = R.string.sdcard_error_message;
        } else {
            title = R.string.sdcard_error_title_nosdcard;
            message = R.string.sdcard_error_message_nosdcard;
        }

        if (status.equals(Environment.MEDIA_SHARED)
                || status.equals(Environment.MEDIA_UNMOUNTED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                title = R.string.sdcard_busy_title;
                message = R.string.sdcard_busy_message;
            } else {
                title = R.string.sdcard_busy_title_nosdcard;
                message = R.string.sdcard_busy_message_nosdcard;
            }
        } else if (status.equals(Environment.MEDIA_REMOVED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                title = R.string.sdcard_missing_title;
                message = R.string.sdcard_missing_message;
            } else {
                title = R.string.sdcard_missing_title_nosdcard;
                message = R.string.sdcard_missing_message_nosdcard;
            }
        } else if (status.equals(Environment.MEDIA_MOUNTED)) {
            // The card is mounted, but we didn't get a valid cursor.
            // This probably means the mediascanner hasn't started scanning the
            // card yet (there is a small window of time during boot where this
            // will happen).
            Intent intent = new Intent();
            intent.setClass(parentActivity, ScanningProgress.class);
            parentActivity.startActivityForResult(intent, Defs.SCAN_DONE);
        }
        if (sdErrorMessageView != null) {
            sdErrorMessageView.setVisibility(View.VISIBLE);
        }
        if (sdErrorMessageIcon != null) {
            sdErrorMessageIcon.setVisibility(View.VISIBLE);
        }
        if (mGridView != null) {
            mGridView.setVisibility(View.GONE);
        }
        sdErrorMessageView.setText(message);
    }

    public void hideDatabaseError() {
        if (sdErrorMessageView != null) {
            sdErrorMessageView.setVisibility(View.GONE);
        }
        if (sdErrorMessageIcon != null) {
            sdErrorMessageIcon.setVisibility(View.GONE);
        }
        if (mGridView != null) {
            mGridView.setVisibility(View.VISIBLE);
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfoIn) {
        if (mCreateShortcut) {
            return;
        }

        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;

        menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);

        if (mi.id >= 0) {
            menu.add(0, DELETE_PLAYLIST, 0, R.string.delete_playlist_menu);
        }

        if (mi.id == RECENTLY_ADDED_PLAYLIST) {
            menu.add(0, EDIT_PLAYLIST, 0, R.string.edit_playlist_menu);
        }

        if (mi.id >= 0) {
            menu.add(0, RENAME_PLAYLIST, 0, R.string.rename_playlist_menu);
        }

        mPlaylistCursor.moveToPosition(mi.position);
        mTitle = mPlaylistCursor.getString(mPlaylistCursor
                .getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME));
        menu.setHeaderTitle(mTitle.equals("My recordings") ? getResources()
                .getString(R.string.audio_db_playlist_name) : mTitle);
    }

    public boolean onContextItemSelected(MenuItem item, int id) {
        Intent intent = new Intent();
        switch (item.getItemId()) {
        case PLAY_SELECTION:
            if (id == RECENTLY_ADDED_PLAYLIST) {
                playRecentlyAdded();
            } else if (id == PODCASTS_PLAYLIST) {
                playPodcasts();
            } else {
                MusicUtils.playPlaylist(parentActivity, id);
            }
            break;
        case DELETE_PLAYLIST:
            // it may not convenient to users when delete new or exist
            // playlist without any notification.
            // show a dialog to confirm deleting this playlist.
            // get playlist name
            if ("My recordings".equals(mTitle)) {
                mTitle = this.getResources().getString(
                        R.string.audio_db_playlist_name);
            }
            String desc = getString(R.string.delete_playlist_message, mTitle);
            Uri uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id);
            Bundle b = new Bundle();
            b.putString("description", desc);
            b.putParcelable("Playlist", uri);
            intent.setClass(parentActivity, DeleteItems.class);
            intent.putExtras(b);
            startActivityForResult(intent, -1);
            break;
        case EDIT_PLAYLIST:
            if (id == RECENTLY_ADDED_PLAYLIST) {
                intent.setClass(parentActivity, WeekSelector.class);
                startActivityForResult(intent, CHANGE_WEEKS);
                return true;
            } else {
                Log.e(TAG, "should not be here");
            }
            break;
        case RENAME_PLAYLIST:
            intent.setClass(parentActivity, RenamePlaylist.class);
            intent.putExtra("rename", Long.valueOf(id));
            startActivityForResult(intent, RENAME_PLAYLIST);
            break;
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
        case SCAN_DONE:
            if (resultCode == parentActivity.RESULT_CANCELED) {
                parentActivity.finish();
            } else if (mAdapter != null) {
                getPlaylistCursor(mAdapter.getQueryHandler(), null);
            }
            break;
        }
    }

    private void playRecentlyAdded() {
        // do a query for all songs added in the last X weeks
        int X = MusicUtils.getIntPref(parentActivity, "numweeks", 2)
                * (3600 * 24 * 7);
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        String where = MediaStore.MediaColumns.DATE_ADDED + ">"
                + (System.currentTimeMillis() / 1000 - X);
        Cursor cursor = MusicUtils.query(parentActivity,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ccols, where,
                null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

        if (cursor == null) {
            return;
        }
        try {
            int len = cursor.getCount();
            long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                cursor.moveToNext();
                list[i] = cursor.getLong(0);
            }
            MusicUtils.playAll(parentActivity, list, 0);
        } catch (SQLiteException ex) {
        } finally {
            cursor.close();
        }
    }

    private void playPodcasts() {
        // do a query for all files that are podcasts
        final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
        Cursor cursor = MusicUtils.query(parentActivity,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ccols,
                MediaStore.Audio.Media.IS_PODCAST + "=1", null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

        if (cursor == null) {
            return;
        }
        try {
            int len = cursor.getCount();
            long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                cursor.moveToNext();
                list[i] = cursor.getLong(0);
            }
            MusicUtils.playAll(parentActivity, list, 0);
        } catch (SQLiteException ex) {
        } finally {
            cursor.close();
        }
    }

    String[] mCols = new String[] { MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME };

    private Cursor getPlaylistCursor(AsyncQueryHandler async,
            String filterstring) {

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Playlists.NAME + " != ''");

        // Add in the filtering constraints
        String[] keywords = null;
        if (filterstring != null) {
            String[] searchWords = filterstring.split(" ");
            keywords = new String[searchWords.length];
            Collator col = Collator.getInstance();
            col.setStrength(Collator.PRIMARY);
            for (int i = 0; i < searchWords.length; i++) {
                keywords[i] = '%' + searchWords[i] + '%';
            }
            for (int i = 0; i < searchWords.length; i++) {
                where.append(" AND ");
                where.append(MediaStore.Audio.Playlists.NAME + " LIKE ?");
            }
        }

        String whereclause = where.toString();

        if (async != null) {
            async.startQuery(0, null,
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mCols,
                    whereclause, keywords, MediaStore.Audio.Playlists.NAME);
            return null;
        }
        Cursor c = null;
        c = MusicUtils.query(parentActivity,
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mCols,
                whereclause, keywords, MediaStore.Audio.Playlists.NAME);
        return mergedCursor(c);
    }

    private Cursor mergedCursor(Cursor c) {
        if (c == null) {
            return null;
        }
        if (c instanceof MergeCursor) {
            // this shouldn't happen, but fail gracefully
            Log.d("PlaylistBrowserActivity", "Already wrapped");
            return c;
        }
        MatrixCursor autoplaylistscursor = new MatrixCursor(mCols);
        if (mCreateShortcut) {
            ArrayList<Object> all = new ArrayList<Object>(2);
            all.add(ALL_SONGS_PLAYLIST);
            all.add(getString(R.string.play_all));
            autoplaylistscursor.addRow(all);
        }
        ArrayList<Object> recent = new ArrayList<Object>(2);
        recent.add(RECENTLY_ADDED_PLAYLIST);
        if (isAdded()) {
            recent.add(getResources().getString(R.string.recentlyadded));
            autoplaylistscursor.addRow(recent);
        }

        // check if there are any podcasts
        Cursor counter = MusicUtils.query(parentActivity,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] { "count(*)" }, "is_podcast=1", null, null);
        if (counter != null) {
            counter.moveToFirst();
            int numpodcasts = counter.getInt(0);
            counter.close();
            if (numpodcasts > 0) {
                ArrayList<Object> podcasts = new ArrayList<Object>(2);
                podcasts.add(PODCASTS_PLAYLIST);
                podcasts.add(getString(R.string.podcasts_listitem));
                autoplaylistscursor.addRow(podcasts);
            }
        }

        Cursor cc = new MergeCursor(new Cursor[] { autoplaylistscursor, c });
        return cc;
    }

    static class PlaylistListAdapter extends SimpleCursorAdapter {
        int mTitleIdx;
        int mIdIdx;
        Cursor mCursor;
        private BitmapDrawable mDefaultAlbumIcon;
        private PlaylistBrowserFragment mFragment = null;
        private AsyncQueryHandler mQueryHandler;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;

        static class ViewHolder {
            ImageView albumArtIcon1, albumArtIcon2, albumArtIcon3, albumArtIcon4;
            TextView tv;
            CharSequence mTitle;
        }

        class QueryHandler extends AsyncQueryHandler {
            QueryHandler(ContentResolver res) {
                super(res);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie,
                    Cursor cursor) {
                if (cursor != null) {
                    cursor = mFragment.mergedCursor(cursor);
                }
                mFragment.init(cursor);
            }
        }

        PlaylistListAdapter(Context context,
                PlaylistBrowserFragment currentfragment, int layout,
                Cursor cursor, String[] from, int[] to) {
            super(context, layout, cursor, from, to);
            mFragment = currentfragment;
            getColumnIndices(cursor);
            mQueryHandler = new QueryHandler(context.getContentResolver());

        }

        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME);
                mIdIdx = cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);
                mCursor = cursor;
            }
        }

        public void setFragment(PlaylistBrowserFragment newfragment) {
            mFragment = newfragment;
        }

        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ViewHolder vh = new ViewHolder();
            vh.albumArtIcon1 = (ImageView) v.findViewById(R.id.icon);
            vh.albumArtIcon2 = (ImageView) v.findViewById(R.id.icon1);
            vh.albumArtIcon3 = (ImageView) v.findViewById(R.id.icon2);
            vh.albumArtIcon4 = (ImageView) v.findViewById(R.id.icon3);
            vh.tv = (TextView) v.findViewById(R.id.line1);
            Resources r = context.getResources();
            mDefaultAlbumIcon = (BitmapDrawable) r
                    .getDrawable(R.drawable.unknown_albums);
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, final Context context, Cursor cursor) {
            final ViewHolder vh = (ViewHolder) view.getTag();
            String name = cursor.getString(mTitleIdx);
            if (name.equals("My recordings")) {
                name = mFragment.getResources().getString(
                        R.string.audio_db_playlist_name);
            }
            vh.mTitle = name;
            vh.tv.setText(name);

            final int id = cursor.getInt(cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID));
            new DownloadAlbumArt(id).run();
            ImageView imageViews[] = new ImageView[] { vh.albumArtIcon1,
                    vh.albumArtIcon2, vh.albumArtIcon3, vh.albumArtIcon4 };
            Bitmap[] albumartArray = null;
            albumartArray = PlaylistBrowserFragment.playlistMap.get(id);
            if (albumartArray != null) {
                if (albumartArray.length == 1) {
                    if (albumartArray[0] != null) {
                        vh.albumArtIcon1.setImageBitmap(albumartArray[0]);
                        vh.albumArtIcon2.setImageBitmap(albumartArray[0]);
                        vh.albumArtIcon3.setImageBitmap(albumartArray[0]);
                        vh.albumArtIcon4.setImageBitmap(albumartArray[0]);
                    } else {
                        for (int i = 0; i < albumartArray.length; i++) {
                            imageViews[i].setImageBitmap(mDefaultAlbumIcon
                                    .getBitmap());
                        }
                    }
                } else if (albumartArray.length == 2) {
                    if (albumartArray[0] != null) {
                        vh.albumArtIcon1.setImageBitmap(albumartArray[0]);
                        vh.albumArtIcon4.setImageBitmap(albumartArray[0]);
                    } else {
                        for (int i = 0; i < albumartArray.length; i++) {
                            imageViews[i].setImageBitmap(mDefaultAlbumIcon
                                    .getBitmap());
                        }
                    }
                    if (albumartArray[1] != null) {
                        vh.albumArtIcon2.setImageBitmap(albumartArray[1]);
                        vh.albumArtIcon3.setImageBitmap(albumartArray[1]);
                    } else {
                        for (int i = 0; i < albumartArray.length; i++) {
                            imageViews[i].setImageBitmap(mDefaultAlbumIcon
                                    .getBitmap());
                        }
                    }

                } else if (albumartArray.length == 3) {
                    for (int i = 0; i < albumartArray.length; i++) {
                        if (i == 3)
                            break;
                        if (albumartArray[i] != null) {
                            imageViews[i].setImageBitmap(albumartArray[i]);
                        } else {
                            imageViews[i].setImageBitmap(mDefaultAlbumIcon
                                    .getBitmap());
                        }
                    }
                    vh.albumArtIcon4.setVisibility(View.GONE);
                } else if (albumartArray.length == 4) {
                    vh.albumArtIcon4.setVisibility(View.VISIBLE);
                    for (int i = 0; i < albumartArray.length; i++) {
                        if (albumartArray[i] != null) {
                            imageViews[i].setImageBitmap(albumartArray[i]);

                        } else {
                            imageViews[i].setImageBitmap(mDefaultAlbumIcon
                                    .getBitmap());
                        }
                    }
                }
            }
            final ImageView menu = (ImageView) view
                    .findViewById(R.id.play_indicator);
            menu.setTag(id);
            menu.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFragment.mTitle = vh.mTitle;
                    PopupMenu popup = new PopupMenu(mFragment
                            .getParentActivity(), menu);
                    popup.getMenu().add(0, PLAY_SELECTION, 0,
                            R.string.play_selection);
                    if (id >= 0) {
                        popup.getMenu().add(0, DELETE_PLAYLIST, 0,
                                R.string.delete_playlist_menu);
                    }
                    if (id == RECENTLY_ADDED_PLAYLIST) {
                        popup.getMenu().add(0, EDIT_PLAYLIST, 0,
                                R.string.edit_playlist_menu);
                    }
                    if (id >= 0) {
                        popup.getMenu().add(0, RENAME_PLAYLIST, 0,
                                R.string.rename_playlist_menu);
                    }
                    popup.show();
                    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            mFragment.onContextItemSelected(item,
                                    Integer.parseInt(menu.getTag().toString()));
                            return true;
                        }
                    });
                }
            });
        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (mFragment.getParentActivity().isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mFragment.mPlaylistCursor) {
                mFragment.mPlaylistCursor = cursor;
                super.changeCursor(cursor);
                getColumnIndices(cursor);
            }
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String s = constraint.toString();
            if (mConstraintIsValid
                    && ((s == null && mConstraint == null) || (s != null && s
                            .equals(mConstraint)))) {
                return getCursor();
            }
            Cursor c = mFragment.getPlaylistCursor(null, s);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }

        private class DownloadAlbumArt implements Runnable {
            String[] mPlaylistMemberCols1 = new String[] {
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.ARTIST };
            int id;
            Bitmap[] mAlbumArtArray;

            public DownloadAlbumArt(int id) {
                this.id = id;
            }

            @Override
            public void run() {
                try {
                    if (id != -1) {
                        Uri uri = MediaStore.Audio.Playlists.Members
                                .getContentUri("external", Long.valueOf(id));
                        Cursor ret = parentActivity.getContentResolver().query(
                                uri, mPlaylistMemberCols1, null, null, null);
                        if (ret.moveToFirst()) {
                            int count = ret.getCount();
                            if (count > 4)
                                count = 4;
                            mAlbumArtArray = new Bitmap[count];
                            for (int j = 0; j < count; j++) {
                                long albumId = ret
                                        .getLong(ret
                                                .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                                Bitmap b = MusicUtils.getArtworkQuick(
                                        parentActivity, albumId,
                                        mDefaultAlbumIcon.getBitmap()
                                                .getWidth(), mDefaultAlbumIcon
                                                .getBitmap().getHeight());
                                if (b == null) {
                                    b = mDefaultAlbumIcon.getBitmap();
                                }
                                mAlbumArtArray[j] = b;
                                ret.moveToNext();
                            }
                        }
                    } else {
                        int X = MusicUtils.getIntPref(parentActivity,
                                "numweeks", 2) * (3600 * 24 * 7);
                        final String[] ccols = new String[] { MediaStore.Audio.Media.ALBUM_ID };
                        String where1 = MediaStore.MediaColumns.DATE_ADDED
                                + ">" + (System.currentTimeMillis() / 1000 - X);
                        Cursor cursor = MusicUtils.query(parentActivity,
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                ccols, where1, null,
                                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
                        try {
                            int len = cursor.getCount();
                            if (len > 4)
                                len = 4;
                            mAlbumArtArray = new Bitmap[len];
                            String[] list = new String[4];
                            for (int i = 0; i < list.length; i++) {
                                if (cursor.moveToNext()) {
                                    long albumId = cursor
                                            .getLong(cursor
                                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                                    Bitmap b = MusicUtils.getArtworkQuick(
                                            parentActivity, albumId,
                                            mDefaultAlbumIcon.getBitmap()
                                                    .getWidth(),
                                            mDefaultAlbumIcon.getBitmap()
                                                    .getHeight());
                                    if (b == null) {
                                        b = mDefaultAlbumIcon.getBitmap();
                                    }
                                    mAlbumArtArray[i] = b;
                                }
                            }
                        } catch (SQLiteException ex) {
                        }
                    }
                    playlistMap.put(id, mAlbumArtArray);
                } catch (Exception e) {
                    System.out.println("Exception caught" + e.getMessage());
                    e.printStackTrace();
                }

            }
        }
    }
    private Cursor mPlaylistCursor;
}