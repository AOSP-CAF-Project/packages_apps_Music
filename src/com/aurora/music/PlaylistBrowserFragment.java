/*
 * Copyright (c) 2019, Aurora OSS, Last Modified 2/7/19 5:04 PM
 * Copyright (C) 2007-2018, The Android Open Source Project
 * Copyright (c) 2014-2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.aurora.music;

import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.aurora.music.MusicUtils.Defs;
import com.aurora.music.MusicUtils.ServiceToken;
import com.aurora.music.adapters.PlaylistListAdapter;

import java.text.Collator;
import java.util.ArrayList;

public class PlaylistBrowserFragment extends Fragment implements
        View.OnCreateContextMenuListener, MusicUtils.Defs {
    public static final LruCache<Integer, Bitmap[]> playlistMap = new LruCache<Integer, Bitmap[]>(
            20);
    public static final int DELETE_PLAYLIST = CHILD_MENU_BASE + 1;
    public static final int EDIT_PLAYLIST = CHILD_MENU_BASE + 2;
    public static final int RENAME_PLAYLIST = CHILD_MENU_BASE + 3;
    public static final int CHANGE_WEEKS = CHILD_MENU_BASE + 4;
    public static final int CLEAR_ALL_PLAYLISTS = CHILD_MENU_BASE + 5;
    public static final int ADD_BY_FILEMANAGER = CHILD_MENU_BASE + 6;
    public static final int REQUEST_CODE_FILE = CHILD_MENU_BASE + 7;
    public static final long RECENTLY_ADDED_PLAYLIST = -1;
    public static final long ALL_SONGS_PLAYLIST = -2;
    public static final long PODCASTS_PLAYLIST = -3;
    public static final long FAVORITE_PLAYLIST = -100;
    public final String TAG = "PlaylistBrowserFragment";
    public PopupMenu mPopupMenu;
    public PlaylistListAdapter mAdapter;
    public boolean mAdapterSent;
    public int mLastListPosCourse = -1;
    public CharSequence mTitle;
    public boolean mCreateShortcut;
    public ServiceToken mToken;
    public GridView mGridView;
    public MediaPlaybackActivity parentActivity;
    public TextView sdErrorMessageView;
    public View sdErrorMessageIcon;
    public BitmapDrawable mDefaultAlbumIcon;
    public int mLastSelectedPosition = -1;
    public String[] mPlaylistMemberCols;
    public String[] mPlaylistMemberCols1;
    public Toolbar mToolbar;
    public int mPlaylistId;
    public Cursor mPlaylistCursor;
    public String[] mCols = new String[]{MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME};

    @SuppressLint("HandlerLeak")
    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getPlaylistCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };
    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mGridView.invalidateViews();
            parentActivity
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

    public PlaylistBrowserFragment() {
    }

    public static String getMediaPath(final Context context, final Uri uri) {

        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {

            // MediaProvider
            if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for MediaStore Uris, and other
     * file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public Activity getParentActivity() {
        return parentActivity;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        parentActivity = (MediaPlaybackActivity) activity;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        parentActivity = (MusicBrowserActivity) getActivity();
        final Intent intent = parentActivity.getIntent();
        final String action = intent.getAction();
        if (getArguments() != null) {
            mCreateShortcut = getArguments().getBoolean("isFromShortcut");
        }
        mPlaylistMemberCols = new String[]{
                MediaStore.Audio.Playlists.Members._ID,
                MediaStore.Audio.Media.ARTIST};
        mPlaylistMemberCols1 = new String[]{MediaStore.Audio.Media.ALBUM_ID};
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
                        parentActivity
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
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        arrangeGridColums(newConfig);
        Fragment fragment = new PlaylistBrowserFragment();
        Bundle args = getArguments();
        fragment.setArguments(args);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_page, fragment, "playlist_fragment")
                .commitAllowingStateLoss();
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
        sdErrorMessageView = rootView.findViewById(R.id.sd_message);
        sdErrorMessageIcon = rootView.findViewById(R.id.sd_icon);
        mToolbar = parentActivity.findViewById(R.id.music_tool_bar);
        mGridView = rootView.findViewById(R.id.album_list);
        mGridView.setTextFilterEnabled(true);
        arrangeGridColums(parentActivity.getResources().getConfiguration());
        mGridView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                PlaylistListAdapter.ViewHolder vh = (PlaylistListAdapter.ViewHolder) view
                        .getTag();
                mToolbar.setTitle(vh.tv.getText().toString());
                Fragment fragment = null;
                mLastSelectedPosition = position;
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

                    parentActivity.setResult(Activity.RESULT_OK, intent);
                    parentActivity.finish();
                    return;
                }
                fragment = new TrackBrowserFragment();
                Bundle args = new Bundle();
                if (id == RECENTLY_ADDED_PLAYLIST) {
                    args.putString("playlist", "recentlyadded");
                    fragment.setArguments(args);
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_page, fragment,
                                    "track_fragment").commitAllowingStateLoss();
                } else if (id == PODCASTS_PLAYLIST) {
                    args.putString("playlist", "podcasts");
                    fragment.setArguments(args);
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_page, fragment,
                                    "track_fragment").commitAllowingStateLoss();
                } else {
                    // Obtain the real id for "My Favorite" Playlist
                    if (id == FAVORITE_PLAYLIST) {
                        id = MusicUtils.idForplaylist(getActivity(), "My Favorite");
                    }
                    args.putBoolean("editValue", true);
                    args.putString("playlist", Long.valueOf(id).toString());
                    fragment.setArguments(args);
                    getFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_page, fragment,
                                    "track_fragment").commitAllowingStateLoss();
                }
                if (MusicUtils.isGroupByFolder()) {
                    MusicUtils.navigatingTabPosition = 4;
                } else {
                    MusicUtils.navigatingTabPosition = 3;
                }

            }
        });
        if (mAdapter == null) {
            mAdapter = new PlaylistListAdapter(parentActivity.getApplication(),
                    this, R.layout.track_list_common_playlist, mPlaylistCursor,
                    new String[]{MediaStore.Audio.Playlists.NAME},
                    new int[]{android.R.id.text1});
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
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        if (mGridView != null) {
            mLastListPosCourse = mGridView.getFirstVisiblePosition();
            View cv = mGridView.getChildAt(0);
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
            mGridView.setSelection(mLastSelectedPosition);
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

        switch (status) {
            case Environment.MEDIA_SHARED:
            case Environment.MEDIA_UNMOUNTED:
                if (Environment.isExternalStorageRemovable()) {
                    title = R.string.sdcard_busy_title;
                    message = R.string.sdcard_busy_message;
                } else {
                    title = R.string.sdcard_busy_title_nosdcard;
                    message = R.string.sdcard_busy_message_nosdcard;
                }
                break;
            case Environment.MEDIA_REMOVED:
                if (Environment.isExternalStorageRemovable()) {
                    title = R.string.sdcard_missing_title;
                    message = R.string.sdcard_missing_message;
                } else {
                    title = R.string.sdcard_missing_title_nosdcard;
                    message = R.string.sdcard_missing_message_nosdcard;
                }
                break;
            case Environment.MEDIA_MOUNTED:
                // The card is mounted, but we didn't get a valid cursor.
                // This probably means the mediascanner hasn't started scanning the
                // card yet (there is a small window of time during boot where this
                // will happen).
                Intent intent = new Intent();
                intent.setClass(parentActivity, ScanningProgress.class);
                parentActivity.startActivityForResult(intent, Defs.SCAN_DONE);
                break;
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
                    if (id == FAVORITE_PLAYLIST) {
                        id = MusicUtils.idForplaylist(getActivity(), "My Favorite");
                    }
                    MusicUtils.playPlaylist(parentActivity, id);
                }
                break;
            case DELETE_PLAYLIST:
                // it may not convenient to users when delete new or exist
                // playlist without any notification.
                // show a dialog to confirm deleting this playlist.
                // get playlist name
                if ("My recordings".contentEquals(mTitle)) {
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

            case ADD_BY_FILEMANAGER:
                mPlaylistId = id;
                Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                innerIntent.setType("audio/*");
                innerIntent.addCategory("android.intent.category.ADD_MUSIC");
                startActivityForResult(innerIntent, REQUEST_CODE_FILE);
                break;
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == Activity.RESULT_CANCELED) {
                    parentActivity.finish();
                } else if (mAdapter != null) {
                    getPlaylistCursor(mAdapter.getQueryHandler(), null);
                }
                break;
            case REQUEST_CODE_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = intent.getData();
                    if (getUriFromPath(parentActivity, uri) != 0) {
                        long[] selectSong = new long[1];
                        selectSong[0] = getUriFromPath(parentActivity, uri);
                        int id = mPlaylistId;
                        if (mPlaylistId == FAVORITE_PLAYLIST) {
                            id = MusicUtils.idForplaylist(getActivity(), "My Favorite");
                        }
                        MusicUtils.addToPlaylist(parentActivity.getApplicationContext(),
                                selectSong, id);
                    } else {
                        Toast.makeText(parentActivity, R.string.add_file_failed, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    private long getUriFromPath(Context context, Uri uri) {
        String path = getMediaPath(parentActivity, uri);
        String filetype = null;
        long songid = 0;
        ContentResolver resolver = context.getContentResolver();
        Cursor c = null;
        try {
            c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,
                    MediaStore.Audio.Media.DATA + "=?", new String[]{
                            path
                    }, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                songid = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
        return songid;
    }

    private void playRecentlyAdded() {
        // do a query for all songs added in the last X weeks
        int X = MusicUtils.getIntPref(parentActivity, "numweeks", 2)
                * (3600 * 24 * 7);
        final String[] ccols = new String[]{MediaStore.Audio.Media._ID};
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
        final String[] ccols = new String[]{MediaStore.Audio.Media._ID};
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

    public Cursor getPlaylistCursor(AsyncQueryHandler async,
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

    public Cursor mergedCursor(Cursor c) {
        if (c == null || !isAdded()) {
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
        ArrayList<Object> favorite = new ArrayList<Object>(2);
        favorite.add(FAVORITE_PLAYLIST);
        favorite.add(getResources().getString(R.string.favorite_playlist));
        autoplaylistscursor.addRow(favorite);

        // check if there are any podcasts
        Cursor counter = MusicUtils.query(parentActivity,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{"count(*)"}, "is_podcast=1", null, null);
        if (counter != null && counter.getCount() > 0) {
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

        if (c.getCount() > 0) {
            c.moveToFirst();
            while (!c.isAfterLast()) {
                String name = c.getString(1);
                if (name.equals("My Favorite")) {
                    c.moveToNext();
                    continue;
                }
                ArrayList<Object> playlist = new ArrayList<Object>(2);
                playlist.add(c.getInt(0));
                playlist.add(name);
                autoplaylistscursor.addRow(playlist);
                c.moveToNext();
            }
            c.close();
        }

        //Cursor cc = new MergeCursor(new Cursor[] { autoplaylistscursor, c });
        return autoplaylistscursor;
    }
}
