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
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.aurora.music.MusicUtils.Defs;
import com.aurora.music.MusicUtils.ServiceToken;
import com.aurora.music.adapters.ArtistAlbumListAdapter;

public class ArtistAlbumBrowserFragment extends Fragment implements
        View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection,
        OnChildClickListener, OnScrollListener {
    public final static int SEARCH = CHILD_MENU_BASE;
    public static int mLastListPosCourse = -1;
    public static int mLastListPosFine = -1;
    public static SubMenu mSub = null;
    public PopupMenu mPopupMenu;
    public String mCurrentArtistId;
    public String mCurrentArtistName;
    public String mCurrentAlbumId;
    public String mCurrentAlbumName;
    public String mCurrentArtistNameForAlbum;
    public ArtistAlbumListAdapter mAdapter;
    public boolean mAdapterSent;
    public ServiceToken mToken;
    public MediaPlaybackActivity mActivity;
    public TextView mSdErrorMessageView;
    public View mSdErrorMessageIcon;
    public ExpandableListView mExpandableListView;
    public View mPrevView = null;
    public boolean isPaused;
    public int lastVisiblePos = 0;
    public boolean isScrolling = true;
    public Cursor mArtistCursor;
    boolean mIsUnknownArtist;
    boolean mIsUnknownAlbum;
    int mExpandPos = -1;
    boolean isExpanded = false;
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (mAdapter != null)
                mAdapter.notifyDataSetChanged();
        }

    };
    @SuppressLint("HandlerLeak")
    Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getArtistCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };
    BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.setSpinnerState(mActivity);
            mReScanHandler.sendEmptyMessage(0);
            if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                MusicUtils.clearAlbumArtCache();
            }
        }
    };


    OnGroupClickListener onGroupClickListener = new OnGroupClickListener() {
        @SuppressLint("ResourceAsColor")
        @Override
        public boolean onGroupClick(ExpandableListView parent, View v,
                                    int groupPosition, long id) {
            // TODO Auto-generated method stub
            if (isExpanded) {
                isExpanded = false;
                mPrevView.setBackground(null);
                mPrevView.setBackgroundResource(R.color.colorBackground);
                if (mExpandPos == groupPosition) {
                    mExpandableListView.collapseGroup(mExpandPos);
                    return true;
                }
                mExpandableListView.collapseGroup(mExpandPos);
            }
            if (!isExpanded) {
                mExpandableListView.expandGroup(groupPosition, true);
                isExpanded = true;
                mExpandPos = groupPosition;
                mPrevView = v;
                v.setBackground(null);
                v.setBackgroundResource(R.color.appwidget_text);
            }
            return true;
        }
    };

    public Activity getParentActivity() {
        return mActivity;
    }

    @Override
    public void onAttach(Activity activity) {
        // TODO Auto-generated method stub
        super.onAttach(activity);
        mActivity = (MediaPlaybackActivity) activity;
    }

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("ResourceAsColor")
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) {
            mCurrentAlbumId = icicle.getString("selectedalbum");
            mCurrentAlbumName = icicle.getString("selectedalbumname");
            mCurrentArtistId = icicle.getString("selectedartist");
            mCurrentArtistName = icicle.getString("selectedartistname");
        }
        // The fragment is cached in FragmentsFactory, when it is loaded,
        // it may not be a new instance, so its isExpanded may keep old value
        isExpanded = false;
        mToken = MusicUtils.bindToService(mActivity, this);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(
                R.layout.media_picker_activity_expanding, container, false);
        mSdErrorMessageView = rootView.findViewById(R.id.sd_message);
        mSdErrorMessageIcon = rootView.findViewById(R.id.sd_icon);
        mExpandableListView = rootView.findViewById(R.id.artist_list);
        mExpandableListView.setOnScrollListener(this);
        mExpandableListView.setGroupIndicator(null);
        mExpandableListView.setTextFilterEnabled(true);
        mExpandableListView.setOnChildClickListener(this);
        mExpandableListView.setDividerHeight(0);
        mExpandableListView.setOnGroupClickListener(onGroupClickListener);
        mAdapter = new ArtistAlbumListAdapter(mActivity.getApplication(),
                this,
                null, // cursor
                R.layout.track_list_item_group, new String[]{}, new int[]{},
                R.layout.track_list_item_child, new String[]{}, new int[]{});
        mExpandableListView.setAdapter(mAdapter);
        getArtistCursor(mAdapter.getQueryHandler(), null);
        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        outcicle.putString("selectedalbum", mCurrentAlbumId);
        outcicle.putString("selectedalbumname", mCurrentAlbumName);
        outcicle.putString("selectedartist", mCurrentArtistId);
        outcicle.putString("selectedartistname", mCurrentArtistName);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onDestroy() {
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        if (mSub != null) {
            mSub.close();
        }
        if (mExpandableListView != null) {
            mLastListPosCourse = mExpandableListView.getFirstVisiblePosition();
            View cv = mExpandableListView.getChildAt(0);
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
        mAdapter = null;
        mExpandableListView.setAdapter(mAdapter);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPaused)
            new MusicUtils.BitmapDownloadThread(mActivity, handler,
                    lastVisiblePos, lastVisiblePos + 10).start();

        isPaused = false;

    }

    public void init(Cursor c) {
        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c); // also sets mArtistCursor
        if (mArtistCursor == null) {
            displayDatabaseError();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }
        // restore previous position
        if (mLastListPosCourse >= 0) {
            mExpandableListView.setSelectionFromTop(mLastListPosCourse,
                    mLastListPosFine);
            mLastListPosCourse = -1;
        }

        hideDatabaseError();
        if (mArtistCursor.getCount() == 0) {
            mSdErrorMessageView.setVisibility(View.VISIBLE);
            mSdErrorMessageView.setText(R.string.no_music_found);
            mExpandableListView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        mReScanHandler.removeCallbacksAndMessages(null);
        lastVisiblePos = mExpandableListView.getFirstVisiblePosition();
        super.onStop();
        isPaused = true;
        super.onPause();
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
                                int groupPosition, int childPosition, long id) {

        mCurrentAlbumId = Long.valueOf(id).toString();
        MusicUtils.navigatingTabPosition = 0;
        mActivity.findViewById(R.id.music_tool_bar).setVisibility(View.GONE);
        Fragment fragment = null;
        fragment = new TrackBrowserActivityFragment();
        Bundle args = new Bundle();
        Cursor c = null;
        try {
            c = (Cursor) mExpandableListView.getExpandableListAdapter()
                    .getChild(groupPosition, childPosition);
            String album = c.getString(c
                    .getColumnIndex(MediaStore.Audio.Albums.ALBUM));
        } catch (Exception e) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        mArtistCursor.moveToPosition(groupPosition);
        mCurrentArtistId = mArtistCursor.getString(mArtistCursor
                .getColumnIndex(MediaStore.Audio.Artists._ID));
        args.putString("artist", mCurrentArtistId);
        args.putString("album", mCurrentAlbumId);
        fragment.setArguments(args);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_page, fragment, "track_fragment")
                .commit();
        return true;
    }

    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play everything by the selected artist
                long[] list = mCurrentArtistId != null ? MusicUtils
                        .getSongListForArtist(mActivity,
                                Long.parseLong(mCurrentArtistId)) : MusicUtils
                        .getSongListForAlbum(mActivity,
                                Long.parseLong(mCurrentAlbumId));

                MusicUtils.playAll(mActivity, list, 0);
                return true;
            }

            case QUEUE: {
                long[] list = mCurrentArtistId != null ? MusicUtils
                        .getSongListForArtist(mActivity,
                                Long.parseLong(mCurrentArtistId)) : MusicUtils
                        .getSongListForAlbum(mActivity,
                                Long.parseLong(mCurrentAlbumId));
                MusicUtils.addToCurrentPlaylist(mActivity, list);
                MusicUtils.addToPlaylist(mActivity, list,
                        MusicUtils.getPlayListId());
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(mActivity, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long[] list = mCurrentArtistId != null ? MusicUtils
                        .getSongListForArtist(mActivity,
                                Long.parseLong(mCurrentArtistId)) : MusicUtils
                        .getSongListForAlbum(mActivity,
                                Long.parseLong(mCurrentAlbumId));
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(mActivity, list, playlist);
                return true;
            }

            case DELETE_ITEM: {
                long[] list;
                String desc;
                if (mCurrentArtistId != null) {
                    list = MusicUtils.getSongListForArtist(mActivity,
                            Long.parseLong(mCurrentArtistId));
                    String f = getString(R.string.delete_artist_desc);
                    desc = String.format(f, mCurrentArtistName);
                } else {
                    list = MusicUtils.getSongListForAlbum(mActivity,
                            Long.parseLong(mCurrentAlbumId));
                    String f = getString(R.string.delete_album_desc);
                    desc = String.format(f, mCurrentAlbumName);
                }
                Bundle b = new Bundle();
                b.putString("description", desc);
                b.putLongArray("items", list);
                Intent intent = new Intent();
                intent.setClass(mActivity, DeleteItems.class);
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
        String query = null;

        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (mCurrentArtistId != null) {
            title = mCurrentArtistName;
            query = mCurrentArtistName;
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, mCurrentArtistName);
            i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS,
                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE);
        } else {
            if (mIsUnknownAlbum) {
                title = query = mCurrentArtistNameForAlbum;
            } else {
                title = query = mCurrentAlbumName;
                if (!mIsUnknownArtist) {
                    query = query + " " + mCurrentArtistNameForAlbum;
                }
            }
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST,
                    mCurrentArtistNameForAlbum);
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentAlbumName);
            i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS,
                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE);
        }
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == Activity.RESULT_CANCELED) {
                    mActivity.finish();
                } else {
                    getArtistCursor(mAdapter.getQueryHandler(), null);
                }
                break;

            case NEW_PLAYLIST:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long[] list = null;
                        if (mCurrentArtistId != null) {
                            list = MusicUtils.getSongListForArtist(mActivity,
                                    Long.parseLong(mCurrentArtistId));
                        } else if (mCurrentAlbumId != null) {
                            list = MusicUtils.getSongListForAlbum(mActivity,
                                    Long.parseLong(mCurrentAlbumId));
                        }
                        MusicUtils.addToPlaylist(mActivity, list,
                                Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    public Cursor getArtistCursor(AsyncQueryHandler async, String filter) {

        String[] cols = new String[]{MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS};

        Uri uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        if (!TextUtils.isEmpty(filter)) {
            uri = uri.buildUpon()
                    .appendQueryParameter("filter", Uri.encode(filter)).build();
        }

        Cursor ret = null;
        if (async != null) {
            async.startQuery(0, null, uri, cols, null, null,
                    MediaStore.Audio.Artists.ARTIST_KEY);
        } else {
            ret = MusicUtils.query(mActivity, uri, cols, null, null,
                    MediaStore.Audio.Artists.ARTIST_KEY);
        }
        return ret;
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
            // a.setTitle("");
            Intent intent = new Intent();
            intent.setClass(mActivity, ScanningProgress.class);
            mActivity.startActivityForResult(intent, Defs.SCAN_DONE);
        }
        if (mSdErrorMessageView != null) {
            mSdErrorMessageView.setVisibility(View.VISIBLE);
        }
        if (mSdErrorMessageIcon != null) {
            mSdErrorMessageIcon.setVisibility(View.VISIBLE);
        }
        if (mExpandableListView != null) {
            mExpandableListView.setVisibility(View.GONE);
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
        if (mExpandableListView != null) {
            mExpandableListView.setVisibility(View.VISIBLE);
        }
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        mActivity
                .updateNowPlaying(getParentActivity());
    }

    public void onServiceDisconnected(ComponentName name) {
        mActivity.finish();
    }

    public void onScrollStateChanged(final AbsListView view, int scrollState) {
        switch (scrollState) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                isScrolling = false;
                int from = view.getFirstVisiblePosition();
                int to = view.getLastVisiblePosition();
                new MusicUtils.BitmapDownloadThread(getParentActivity(), handler,
                        from, to).start();
                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                isScrolling = true;
                break;
            case OnScrollListener.SCROLL_STATE_FLING:
                isScrolling = true;
                break;
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
                         int visibleItemCount, int totalItemCount) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        if (mSub != null) {
            mSub.close();
        }
        Fragment fragment = new ArtistAlbumBrowserFragment();
        Bundle args = getArguments();
        fragment.setArguments(args);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_page, fragment, "artist_fragment")
                .commitAllowingStateLoss();
    }
}
