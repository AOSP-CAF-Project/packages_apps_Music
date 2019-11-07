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
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.aurora.music.MusicUtils.Defs;
import com.aurora.music.MusicUtils.ServiceToken;
import com.aurora.music.adapters.FolderListAdapter;

public class FolderBrowserFragment extends Fragment
        implements View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection, OnItemClickListener {
    public static String mCurrentParent;
    public static String mCurrentPath;
    public static int mLastListPosCourse = -1;
    public static int mLastListPosFine = -1;
    public static Cursor mFilesCursor;
    public static SubMenu mSub = null;
    public PopupMenu mPopupMenu;
    public boolean mAdapterSent;
    public TextView mSdErrorMessageView;
    public View mSdErrorMessageIcon;
    public ListView mFolderList;
    public MediaPlaybackActivity mActivity;
    public BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mFolderList.invalidateViews();
            mActivity.updateNowPlaying(mActivity);
        }
    };
    private FolderListAdapter mAdapter;
    private ServiceToken mToken;
    @SuppressLint("HandlerLeak")
    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getFolderCursor(mAdapter.getQueryHandler(), null);
            }
        }
    };
    private BroadcastReceiver mScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.setSpinnerState(mActivity);
            mReScanHandler.sendEmptyMessage(0);
            if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                MusicUtils.clearAlbumArtCache();
            }
        }
    };

    public static String getRootPath(String path) {
        if (path != null) {
            String root = "";
            int pos = path.lastIndexOf("/");
            if (pos >= 0) {
                root = path.substring(0, pos);
            }
            return root;
        } else {
            return "";
        }
    }

   /* @Override
    public Object onRetainNonConfigurationInstance() {
        mAdapterSent = true;
        return mAdapter;
    }*/

    public Activity getParentActivity() {
        return mActivity;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (MediaPlaybackActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(
                R.layout.media_picker_folder, container, false);
        mSdErrorMessageView = rootView.findViewById(R.id.sd_message);
        mSdErrorMessageIcon = rootView.findViewById(R.id.sd_icon);
        mFolderList = rootView.findViewById(R.id.folder_list);
        mFolderList.setTextFilterEnabled(true);
        mFolderList.setOnItemClickListener(this);
        mFolderList.setDividerHeight(0);

        mAdapter = new FolderListAdapter(
                mActivity.getApplicationContext(),
                this,
                R.layout.track_list_item,
                mFilesCursor,
                new String[]{},
                new int[]{});
        mFolderList.setAdapter(mAdapter);
        getFolderCursor(mAdapter.getQueryHandler(), null);
        return rootView;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) {
            mCurrentParent = icicle.getString("selectedParent");
        }
        mToken = MusicUtils.bindToService(mActivity, this);
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putString("selectedParent", mCurrentParent);
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
        if (mFolderList != null) {
            mLastListPosCourse = mFolderList.getFirstVisiblePosition();
            View cv = mFolderList.getChildAt(0);
            if (cv != null) {
                mLastListPosFine = cv.getTop();
            }
        }
        MusicUtils.unbindFromService(mToken);

        if (!mAdapterSent && mAdapter != null) {
            mAdapter.changeCursor(null);
        }

        mFolderList.setAdapter(null);
        mAdapter = null;
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        mActivity.registerReceiver(mTrackListListener, f);
        mTrackListListener.onReceive(null, null);

        // MusicUtils.setSpinnerState(this);
    }

    /*@Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }*/

    @Override
    public void onPause() {
        mActivity.unregisterReceiver(mTrackListListener);
        mReScanHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    public void init(Cursor c) {
        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c);

        if (mFilesCursor == null) {
            displayDatabaseError();
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }

        if (mLastListPosCourse >= 0) {
            mFolderList.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
            mLastListPosCourse = -1;
        }

        hideDatabaseError();
        if (mFilesCursor.getCount() == 0) {
            mSdErrorMessageView.setVisibility(View.VISIBLE);
            mSdErrorMessageView.setText(R.string.no_music_found);
            mFolderList.setVisibility(View.GONE);
        }
        //setTitle();
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
        if (mFolderList != null) {
            mFolderList.setVisibility(View.GONE);
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
        if (mFolderList != null) {
            mFolderList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
        mSub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(mActivity, mSub);
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        mFilesCursor.moveToPosition(mi.position);
        mCurrentParent = mFilesCursor.getString(mFilesCursor
                .getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT));
        mCurrentPath = mFilesCursor.getString(mFilesCursor
                .getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA));
        menu.setHeaderTitle(getRootPath(mCurrentPath));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                long[] list = MusicUtils.getSongListForFolder(mActivity, Long.parseLong(mCurrentParent));
                MusicUtils.playAll(mActivity, list, 0);
                return true;
            }

            case QUEUE: {
                long[] list = MusicUtils.getSongListForFolder(mActivity, Long.parseLong(mCurrentParent));
                MusicUtils.addToCurrentPlaylist(mActivity, list);
                MusicUtils.addToPlaylist(mActivity, list, MusicUtils.getPlayListId());
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(mActivity, CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long[] list = MusicUtils
                        .getSongListForFolder(mActivity, Long.parseLong(mCurrentParent));
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(mActivity, list, playlist);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SCAN_DONE:
                if (resultCode == Activity.RESULT_CANCELED) {
                    mActivity.finish();
                } else {
                    getFolderCursor(mAdapter.getQueryHandler(), null);
                }
                break;

            case NEW_PLAYLIST:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = intent.getData();
                    if (uri != null) {
                        long[] list = MusicUtils.getSongListForFolder(mActivity,
                                Long.parseLong(mCurrentParent));
                        MusicUtils.addToPlaylist(mActivity, list,
                                Long.parseLong(uri.getLastPathSegment()));
                    }
                }
                break;
        }
    }

    public String getAlbumName(String path) {
        String name;
        String mUnknownPath = mActivity.getString(R.string.unknown_folder_name);
        String rootPath = getRootPath(path);
        if (path == null || path.trim().equals("")) {
            name = mUnknownPath;
        } else {
            String[] split = rootPath.split("/");
            name = split[split.length - 1];
        }
        return name;
    }

    @Override
    public void onItemClick(AdapterView<?> parentview, View view, int position,
                            long id) {
        //Intent intent = new Intent(Intent.ACTION_PICK);
        //intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track");
        Cursor c = (Cursor) mAdapter.getItem(position);
        int index = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT);
        int parent = c.getInt(index);
        //intent.putExtra("parent", parent);
        index = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
        String path = c.getString(index);
        //  intent.putExtra("rootPath", getRootPath(path));
        // startActivity(intent);

        Fragment fragment = new TrackBrowserFragment();
        Bundle args = new Bundle();
        args.putInt("parent", parent);
        args.putString("rootPath", getRootPath(path));
        args.putString("folder_name", getAlbumName(path));

        fragment.setArguments(args);
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_page, fragment, "folder_fragment")
                .commit();
        MusicUtils.navigatingTabPosition = 3;
    }

    public Cursor getFolderCursor(AsyncQueryHandler async, String filter) {
        Cursor ret = null;
        String uriString = "content://media/external/file";
        String selection = "is_music = 1 & 1=1 ) GROUP BY (parent";
        String[] projection = {"count(*)", "_id", "_data", "parent"};
        Uri uri = Uri.parse(uriString);
        if (async != null) {
            async.startQuery(0, null, uri, projection, selection, null, null);
        } else {
            ret = MusicUtils.query(mActivity, uri, projection, selection, null, null);
        }
        return ret;
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        mActivity
                .updateNowPlaying(getActivity());
    }

    public void onServiceDisconnected(ComponentName name) {
        mActivity.finish();
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
        Fragment fragment = new FolderBrowserFragment();
        Bundle args = getArguments();
        fragment.setArguments(args);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_page, fragment, "folder_fragment")
                .commitAllowingStateLoss();
    }
}
