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

import android.Manifest.permission;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.aurora.music.MusicUtils.ServiceToken;
import com.aurora.music.custom.FragmentsFactory;
import com.aurora.music.custom.MusicPanelLayout.BoardState;
import com.aurora.music.custom.MusicPanelLayout.ViewHookSlipListener;
import com.aurora.music.custom.PermissionActivity;

import java.util.ArrayList;

public class MusicBrowserActivity extends MediaPlaybackActivity implements
        MusicUtils.Defs {
    private static final String TAG = "MusicBrowserActivity";
    private static final String[] REQUIRED_PERMISSIONS = {
            permission.READ_PHONE_STATE,
            permission.READ_EXTERNAL_STORAGE,
            permission.WRITE_EXTERNAL_STORAGE};
    private static final long RECENTLY_ADDED_PLAYLIST = -1;
    private static final long ALL_SONGS_PLAYLIST = -2;
    private static final long PODCASTS_PLAYLIST = -3;
    private static final long DEFAULT_PLAYLIST = -5;
    //List item height - list item padding ==> 48 - 3 = 45
    //Number of menu items in Non-CMCC mode are 4 (doesn't includes "Folder")
    private static final int LIST_HEIGHT_NON_CMCC_MODE = 45 * 4;
    //number of menu items in CMCC mode are 5 (includes "Folder")
    private static final int LIST_HEIGHT_CMCC_MODE = 45 * 5;
    public static boolean mIsparentActivityFInishing;
    NavigationDrawerListAdapter mNavigationAdapter;
    String mArtistID, mAlbumID;
    String mIntentAction;
    long mPlaylistId = DEFAULT_PLAYLIST;
    private ServiceToken mToken;
    private ListView mDrawerListView;
    private DrawerLayout mDrawerLayout;
    private ArrayList<Fragment> mMusicFragments;
    private int activeTab;
    private Thread mFavoritePlaylistThread = new Thread() {
        @Override
        public void run() {
            if (checkSelfPermission(permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED)
                createFavoritePlaylist();
        }
    };
    private ServiceConnection autoshuffle = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            // we need to be able to bind again, so unbind
            try {
                unbindService(this);
            } catch (IllegalArgumentException e) {
            }
            IMediaPlaybackService serv = IMediaPlaybackService.Stub
                    .asInterface(obj);
            if (serv != null) {
                try {
                    serv.setShuffleMode(MediaPlaybackService.SHUFFLE_AUTO);
                } catch (RemoteException ex) {
                }
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
        }
    };

    public MusicBrowserActivity() {
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        if (PermissionActivity.checkAndRequestPermission(this, REQUIRED_PERMISSIONS)) {
            SysApplication.getInstance().exit();
        }
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.music_browser);
        mIntentAction = getIntent().getAction();
        Bundle bundle = getIntent().getExtras();
        try {
            if (bundle != null) {
                mPlaylistId = Long.parseLong(bundle.getString("playlist"));
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Playlist id missing or broken");
        }
        MusicUtils.updateGroupByFolder(this);
        init();
        initView();
        mFavoritePlaylistThread.start();
        String shuf = getIntent().getStringExtra("autoshuffle");
        if ("true".equals(shuf)) {
            mToken = MusicUtils.bindToService(this, autoshuffle);
        }
    }

    private void createFavoritePlaylist() {
        // TODO Auto-generated method stub
        ContentResolver resolver = getContentResolver();
        int id = MusicUtils.idForplaylist(this, "My Favorite");
        Uri uri;
        if (id < 0) {
            ContentValues values = new ContentValues(1);
            values.put(MediaStore.Audio.Playlists.NAME, "My Favorite");
            try {
                uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values);
            } catch (UnsupportedOperationException e) {
                Log.w(TAG, "external storage not ready");
            }
        }
    }

    public void initView() {
        mNowPlayingIcon = findViewById(R.id.nowplay_icon);
        mNowPlayingIcon.setOnClickListener(mPauseListener);
        mDrawerListView = findViewById(R.id.navList);
        int height = MusicUtils.isGroupByFolder() ? LIST_HEIGHT_CMCC_MODE
                : LIST_HEIGHT_NON_CMCC_MODE;
        height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height,
                getResources().getDisplayMetrics());
        mDrawerListView.getLayoutParams().height = height;
        RelativeLayout equalizerLayout = findViewById(R.id.equalizer_btn_view);
        RelativeLayout exitLayout = findViewById(R.id.exit_btn_view);
        if (getApplicationContext().getResources().getBoolean(R.bool.exit_in_drawer)) {
            exitLayout.setVisibility(View.VISIBLE);
        } else {
            exitLayout.setVisibility(View.GONE);
        }

        mDrawerLayout = findViewById(R.id.drawerLayout);
        mNavigationAdapter = new NavigationDrawerListAdapter(this);
        mDrawerListView.setAdapter(mNavigationAdapter);
        activeTab = MusicUtils.getIntPref(this, "activetab", 0);
        mDrawerListView.setOnItemClickListener((parent, view, position, id) -> {
            if (mIsPanelExpanded) {
                getSlidingPanelLayout().setHookState(
                        BoardState.COLLAPSED);

            }
            activeTab = MusicUtils.getIntPref(
                    MusicBrowserActivity.this, "activetab", 0);
            mNavigationAdapter.setClickPosition(position);
            mDrawerLayout.closeDrawer(Gravity.START);
            final int toShowPosition = position;
            mDrawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
                @Override
                public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                }

                @Override
                public void onDrawerOpened(@NonNull View drawerView) {
                }

                @Override
                public void onDrawerClosed(@NonNull View drawerView) {
                    showScreen(toShowPosition);

                }

                @Override
                public void onDrawerStateChanged(int newState) {
                }
            });
        });

        equalizerLayout.setOnClickListener(v -> {
            MusicUtils.startSoundEffectActivity(MusicBrowserActivity.this);
            mDrawerLayout.closeDrawer(Gravity.START);
        });

        exitLayout.setOnClickListener(v -> {
            try {
                MusicUtils.sService.stop();
                SysApplication.getInstance().exit();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });

        if (mToolbar.getMenu() != null) {
            mToolbar.getMenu().clear();
        }
        mToolbar.inflateMenu(R.menu.main);
        mToolbar.setOnMenuItemClickListener(item -> {
            // TODO Auto-generated method stub
            mIsparentActivityFInishing = true;
            Intent intent = new Intent(MusicBrowserActivity.this,
                    QueryBrowserActivity.class);
            startActivity(intent);
            return false;
        });
        mToolbar.setNavigationContentDescription("drawer");
        mToolbar.setNavigationOnClickListener(v -> {
            if (mToolbar.getNavigationContentDescription().equals("drawer")) {
                mDrawerLayout.openDrawer(Gravity.START);
            } else {
                showScreen(MusicUtils.navigatingTabPosition);
                mToolbar.setNavigationContentDescription("drawer");
                mToolbar.setNavigationIcon(R.drawable.ic_material_light_navigation_drawer);
            }
        });

        showScreen(activeTab);
    }

    @Override
    public void onNewIntent(Intent intent) {
        // TODO Auto-generated method stub
        super.onNewIntent(intent);
        if (getIntent() != null) {
            mArtistID = getIntent().getStringExtra("artist");
            mAlbumID = getIntent().getStringExtra("album");
            Bundle bundle = getIntent().getExtras();
            try {
                if (bundle != null) {
                    mPlaylistId = Long.parseLong(bundle.getString("playlist"));
                    if (mPlaylistId != DEFAULT_PLAYLIST) {
                        if (mIsPanelExpanded) {
                            getSlidingPanelLayout().setHookState(BoardState.COLLAPSED);
                            mIsPanelExpanded = false;
                            try {
                                Thread.sleep(200);  //This is to let the sliding panel collapse and
                                //not to see graphic corruption.
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Playlist id missing");
            }
            initView();
        }
        mIntentAction = intent.getAction();
    }

    @Override
    public void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }

    public void showScreen(int position) {
        if (position > 5) {
            position = 0;
        }

        if (mPlaylistId != DEFAULT_PLAYLIST) { //coming from widget
            if (mPlaylistId == ALL_SONGS_PLAYLIST) {
                position = 2;   // all songs
            } else {
                if (MusicUtils.isGroupByFolder()) {
                    position = 4;   // playlists index is 4 in CMCC view (with folders)
                } else {
                    position = 3;   // playlists index is 3 in non-CMCC view (without folders)
                }
            }
        }

        int maxTabPosition = mNavigationAdapter.getCount() - 1;
        if (position > maxTabPosition) {
            position = 0;
        }
        mNavigationAdapter.setClickPosition(position);
        mDrawerListView.invalidateViews();
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = null;

        if (MusicUtils.isGroupByFolder()) {
            mToolbar.setTitle(getResources().getStringArray(
                    R.array.title_array_folder)[position]);
        } else {
            mToolbar.setTitle(getResources().getStringArray(
                    R.array.title_array_songs)[position]);
        }
        mDrawerListView.setItemChecked(position, true);
        mDrawerListView.setSelection(position);
        FrameLayout fl = findViewById(R.id.fragment_page);
        fl.setVisibility(View.VISIBLE);
        findViewById(R.id.music_tool_bar).setVisibility(View.VISIBLE);
        MusicUtils.setIntPref(this, "activetab", position);
        if (mArtistID != null) {
            fragment = new AlbumBrowserFragment();
            Bundle bundle = new Bundle();
            bundle.putString("artist", mArtistID);
            fragment.setArguments(bundle);
            mArtistID = null;
        } else if (mAlbumID != null) {
            fragment = new TrackBrowserFragment();
            Bundle bundle = new Bundle();
            bundle.putString("album", mAlbumID);
            bundle.putBoolean("editValue", false);
            fragment.setArguments(bundle);
            mAlbumID = null;
        } else if (mPlaylistId != DEFAULT_PLAYLIST) {
            fragment = new TrackBrowserFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean("editValue", false);
            if (mPlaylistId == RECENTLY_ADDED_PLAYLIST) {
                bundle.putString("playlist", "recentlyadded");
            } else if (mPlaylistId == PODCASTS_PLAYLIST) {
                bundle.putString("playlist", "podcasts");
            } else {
                if (mPlaylistId != ALL_SONGS_PLAYLIST) {
                    bundle.putBoolean("editValue", true);
                    bundle.putString("playlist", Long.valueOf(mPlaylistId)
                            .toString());
                }
            }
            bundle.putBoolean("isFromShortcut", true);
            fragment.setArguments(bundle);
            mPlaylistId = DEFAULT_PLAYLIST;
        } else if (mIntentAction != null
                && mIntentAction
                .equalsIgnoreCase(Intent.ACTION_CREATE_SHORTCUT)) {
            fragment = new PlaylistBrowserFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean("isFromShortcut", true);
            fragment.setArguments(bundle);
        } else {
            fragment = FragmentsFactory.loadFragment(position);
        }
        if (!fragment.isAdded()) {
            fragmentManager.beginTransaction()
                    .replace(R.id.fragment_page, fragment).commit();
        }
    }

    @Override
    public void onStart() {
        // TODO Auto-generated method stub
        super.onStart();

    }

    @Override
    public void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mToken != null) {
            MusicUtils.unbindFromService(mToken);
        }
        MusicUtils.clearAlbumArtCache();
        super.onDestroy();
    }

    /**
     * No-op stubs for {@link PanelSlideListener}. If you only want to implement
     * a subset of the listener methods you can extend this instead of implement
     * the full interface.
     */
    public static class SimplePanelSlideListener implements
            ViewHookSlipListener {

        @Override
        public void onViewSlip(View view, float slipOffset) {
            mIsparentActivityFInishing = true;
        }

        @Override
        public void onViewClosed(View view) {
            getInstance().mIsPanelExpanded = false;
            MusicUtils.updateNowPlaying(getInstance(), false);
            getInstance().closeQueuePanel();
        }

        @Override
        public void onViewOpened(View view) {
            getInstance().mIsPanelExpanded = true;
            MusicUtils.updateNowPlaying(getInstance(), true);
        }

        @Override
        public void onViewMainLined(View panel) {
        }

        @Override
        public void onViewBackStacked(View panel) {
        }
    }

}
