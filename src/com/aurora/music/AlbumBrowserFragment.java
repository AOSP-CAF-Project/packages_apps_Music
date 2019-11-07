/*
 * Copyright (c) 2019, The OneOS Project
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
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.aurora.music.MusicUtils.Defs;
import com.aurora.music.MusicUtils.ServiceToken;
import com.aurora.music.adapters.AlbumListAdapter;

public class AlbumBrowserFragment extends Fragment implements MusicUtils.Defs,
        ServiceConnection {
    private final static int SEARCH = CHILD_MENU_BASE;
    public static SubMenu mSub = null;
    private static int mLastListPosCourse = -1;
    @SuppressWarnings("unused")
    private static int mLastListPosFine = -1;
    private static int mLastSelectedPosition = -1;
    public PopupMenu mPopupMenu;
    public String mCurrentAlbumId;
    public String mCurrentAlbumName;
    public String mCurrentArtistNameForAlbum;
    public AlbumListAdapter mAdapter;
    public boolean mAdapterSent;
    public ServiceToken mToken;
    public GridView mAlbumList;
    public TextView mSdErrorMessageView;
    public View mSdErrorMessageIcon;
    public MediaPlaybackActivity mParentActivity;
    public Cursor mAlbumCursor;
    public String mArtistId;
    public boolean mIsUnknownArtist;
    public boolean mIsUnknownAlbum;

    @SuppressLint("HandlerLeak")
    public Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getAlbumCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.setSpinnerState(mParentActivity);
            mReScanHandler.sendEmptyMessage(0);
            if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                MusicUtils.clearAlbumArtCache();
            }
        }
    };

    public AlbumBrowserFragment() {
    }

    public Activity getParentActivity() {
        return mParentActivity;
    }

    @Override
    public void onAttach(Activity activity) {
        // TODO Auto-generated method stub
        super.onAttach(activity);
        mParentActivity = (MediaPlaybackActivity) activity;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mToken = MusicUtils.bindToService(mParentActivity, this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addDataScheme("file");
        mParentActivity.registerReceiver(mScanListener, f);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.media_picker_fragment_album,
                container, false);
        mSdErrorMessageView = rootView.findViewById(R.id.sd_message);
        mSdErrorMessageIcon = rootView.findViewById(R.id.sd_icon);
        if (getArguments() != null) {
            mArtistId = getArguments().getString("artist");
        }
        mAlbumList = rootView.findViewById(R.id.album_list);
        arrangeGridColums(mParentActivity.getResources().getConfiguration());
        mAlbumList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                enterAlbum(id);
            }
        });
        mAlbumList.setTextFilterEnabled(true);
        mAdapter = new AlbumListAdapter(mParentActivity.getApplication(), this,
                R.layout.track_list_item_album, mAlbumCursor, new String[]{},
                new int[]{});
        mAlbumList.setAdapter(mAdapter);
        /*AlbumList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_FLING) {
                    mAdapter.mFastscroll = true;
                } else {
                    mAdapter.mFastscroll = false;
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });*/
        getAlbumCursor(mAdapter.getQueryHandler(), null);
        return rootView;
    }

    public void enterAlbum(long albumID) {
        mParentActivity.findViewById(R.id.music_tool_bar)
                .setVisibility(View.GONE);
        Fragment fragment = new TrackBrowserActivityFragment();
        mLastSelectedPosition = mAlbumList.getSelectedItemPosition();
        Bundle args = new Bundle();
        args.putString("artist", mArtistId);
        args.putString("album", Long.valueOf(albumID).toString());
        fragment.setArguments(args);

        if (!mParentActivity.isFinishing() && !mParentActivity.isDestroyed()) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_page, fragment, "track_fragment")
                    .commit();
        }

        MusicUtils.navigatingTabPosition = 1;
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString("selectedalbum", mCurrentAlbumId);
        outcicle.putString("artist", mArtistId);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        if (mSub != null) {
            mSub.close();
        }
        arrangeGridColums(newConfig);
        Fragment fragment = new AlbumBrowserFragment();
        Bundle args = getArguments();
        fragment.setArguments(args);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_page, fragment, "album_fragment")
                .commitAllowingStateLoss();
    }

    public void arrangeGridColums(Configuration newConfig) {
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mAlbumList.setNumColumns(3);
        } else {
            mAlbumList.setNumColumns(2);
        }
    }

    @Override
    public void onDestroy() {
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        if (mSub != null) {
            mSub.close();
        }
        if (mAlbumList != null) {
            mLastListPosCourse = mAlbumList.getFirstVisiblePosition();
            View cv = mAlbumList.getChildAt(0);
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
        mAlbumList.setAdapter(null);
        mAdapter = null;
        mParentActivity.unregisterReceiver(mScanListener);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        MusicUtils.setSpinnerState(mParentActivity);
    }

    @Override
    public void onPause() {
        mReScanHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    public void init(Cursor c) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c); // also sets mAlbumCursor

        if (mAlbumCursor == null) {
            displayDatabaseError();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

        // restore previous position
        if (mLastListPosCourse >= 0) {
            mAlbumList.setSelection(mLastSelectedPosition);
            mLastListPosCourse = -1;
        }

        hideDatabaseError();
        if (mAlbumCursor.getCount() == 0) {
            mSdErrorMessageView.setVisibility(View.VISIBLE);
            mSdErrorMessageView.setText(R.string.no_music_found);
            mAlbumList.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the selected album
                long[] list = MusicUtils.getSongListForAlbum(mParentActivity,
                        Long.parseLong(mCurrentAlbumId));
                MusicUtils.playAll(mParentActivity, list, 0);
                return true;
            }

            case QUEUE: {
                long[] list = MusicUtils.getSongListForAlbum(mParentActivity,
                        Long.parseLong(mCurrentAlbumId));
                MusicUtils.addToCurrentPlaylist(mParentActivity, list);
                MusicUtils.addToPlaylist(mParentActivity, list,
                        MusicUtils.getPlayListId());
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(mParentActivity, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long[] list = MusicUtils.getSongListForAlbum(mParentActivity,
                        Long.parseLong(mCurrentAlbumId));
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(mParentActivity, list, playlist);
                return true;
            }

            case DELETE_ITEM: {
                long[] list = MusicUtils.getSongListForAlbum(mParentActivity,
                        Long.parseLong(mCurrentAlbumId));
                String f = getString(R.string.delete_album_desc);
                String desc = String.format(f, mCurrentAlbumName);
                Bundle b = new Bundle();
                b.putString("description", desc);
                b.putLongArray("items", list);
                Intent intent = new Intent();
                intent.setClass(mParentActivity, DeleteItems.class);
                intent.putExtras(b);
                startActivityForResult(intent, -1);
                return true;
            }

            case SEARCH:
                doSearch();
                return true;

        }
        return super.onContextItemSelected(item);
    }

    void doSearch() {
        CharSequence title = null;
        String query = "";

        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        title = "";
        if (!mIsUnknownAlbum) {
            query = mCurrentAlbumName;
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentAlbumName);
            title = mCurrentAlbumName;
        }
        if (!mIsUnknownArtist) {
            query = query + " " + mCurrentArtistNameForAlbum;
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST,
                    mCurrentArtistNameForAlbum);
            title = title + " " + mCurrentArtistNameForAlbum;
        }
        // Since we hide the 'search' menu item when both album and artist are
        // unknown, the query and title strings will have at least one of those.
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS,
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE);
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);
        startActivity(Intent.createChooser(i, title));
    }

    @SuppressWarnings("static-access")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == mParentActivity.RESULT_CANCELED) {
                    mParentActivity.finish();
                } else {
                    getAlbumCursor(mAdapter.getQueryHandler(), null);
                }
                break;

            case NEW_PLAYLIST:
                if (resultCode == mParentActivity.RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long[] list = MusicUtils.getSongListForAlbum(
                                mParentActivity, Long.parseLong(mCurrentAlbumId));
                        MusicUtils.addToPlaylist(mParentActivity, list,
                                Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    public Cursor getAlbumCursor(AsyncQueryHandler async, String filter) {
        String[] cols = new String[]{MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ALBUM_ART};

        Cursor ret = null;
        if (mArtistId != null) {
            Uri uri = MediaStore.Audio.Artists.Albums.getContentUri("external",
                    Long.valueOf(mArtistId));
            if (!TextUtils.isEmpty(filter)) {
                uri = uri.buildUpon()
                        .appendQueryParameter("filter", Uri.encode(filter))
                        .build();
            }
            if (async != null) {
                async.startQuery(0, null, uri, cols, null, null,
                        MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
            } else {
                ret = MusicUtils.query(mParentActivity, uri, cols, null, null,
                        MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
            }
        } else {
            Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
            if (!TextUtils.isEmpty(filter)) {
                uri = uri.buildUpon()
                        .appendQueryParameter("filter", Uri.encode(filter))
                        .build();
            }
            if (async != null) {
                async.startQuery(0, null, uri, cols, null, null,
                        MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
            } else {
                ret = MusicUtils.query(mParentActivity, uri, cols, null, null,
                        MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
            }
        }
        return ret;
    }

    @SuppressWarnings("unused")
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
                intent.setClass(mParentActivity, ScanningProgress.class);
                mParentActivity.startActivityForResult(intent, Defs.SCAN_DONE);
                break;
        }
        if (mSdErrorMessageView != null) {
            mSdErrorMessageView.setVisibility(View.VISIBLE);
        }
        if (mSdErrorMessageIcon != null) {
            mSdErrorMessageIcon.setVisibility(View.VISIBLE);
        }
        if (mAlbumList != null) {
            mAlbumList.setVisibility(View.GONE);
        }
        mSdErrorMessageView.setText(message);
    }

    public void hideDatabaseError() {
        if (mSdErrorMessageView != null) {
            mSdErrorMessageView.setVisibility(View.GONE);
        }
        if (mSdErrorMessageIcon != null) {
            mSdErrorMessageIcon.setVisibility(View.GONE);
        }
        if (mAlbumList != null) {
            mAlbumList.setVisibility(View.VISIBLE);
        }
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        mParentActivity
                .updateNowPlaying(getParentActivity());
    }

    public void onServiceDisconnected(ComponentName name) {
        mParentActivity.finish();
    }
}
