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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class DeleteItems extends Activity {
    private static String DELETE_VIDEO_ITEM = "delete.video.file";
    private TextView mPrompt;
    private Button mButton;
    private long[] mItemList;
    private int mItemListHashCode;
    private Uri mPlaylistUri;
    private Uri mVideoUri;
    // Broadcast receiver monitor system language change, if language changed
    // will finsh current activity, same as system alter dialog.
    private BroadcastReceiver mLanguageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                finish();
            }
        }
    };
    private View.OnClickListener mButtonClicked = new View.OnClickListener() {
        public void onClick(View v) {
            // delete the selected video item
            if (mVideoUri != null) {
                getContentResolver().delete(mVideoUri, null, null);
                Toast.makeText(DeleteItems.this,
                        R.string.video_deleted_message,
                        Toast.LENGTH_SHORT).show();
            } else if (mPlaylistUri != null) {
                getContentResolver().delete(mPlaylistUri, null, null);
                Toast.makeText(DeleteItems.this,
                        R.string.playlist_deleted_message,
                        Toast.LENGTH_SHORT).show();
            } else {
                if (mItemListHashCode == mItemList.hashCode()) {
                    return;
                }
                mItemListHashCode = mItemList.hashCode();
                // delete the selected item(s)
                final String where = getItemListString(mItemList);
                new AsyncTask<Void, Void, Void>() {
                    private int mLength;
                    private Cursor mCursor = null;

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        mCursor = MusicUtils.deleteTracksPre(DeleteItems.this, where);
                        mLength = mItemList.length;
                    }

                    @Override
                    protected Void doInBackground(Void... params) {
                        MusicUtils.deleteTracks(DeleteItems.this, mCursor, where);
                        if (mCursor != null) {
                            mCursor.close();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        super.onPostExecute(result);
                        MusicUtils.deleteTracksPost(DeleteItems.this, mLength);
                        DeleteItems.this.finish();
                    }
                }.execute();
                return;
            }
            finish();
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.confirm_delete);
        getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);

        mPrompt = findViewById(R.id.prompt);
        mButton = findViewById(R.id.delete);
        mButton.setOnClickListener(mButtonClicked);

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        Bundle b = getIntent().getExtras();
        String desc = b.getString("description");
        if (DELETE_VIDEO_ITEM.equals(desc)) {
            String videoName = b.getString("videoName");
            desc = videoName;
            mVideoUri = b.getParcelable("videoUri");
        } else {
            mPlaylistUri = b.getParcelable("Playlist");
            if (mPlaylistUri == null) {
                mItemList = b.getLongArray("items");
            }
        }
        mPrompt.setText(desc);

        // Register broadcast receiver can monitor system language change.
        IntentFilter filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mLanguageChangeReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver can monitor system language change.
        unregisterReceiver(mLanguageChangeReceiver);
    }

    @Override
    public void onUserLeaveHint() {
        finish();
        super.onUserLeaveHint();
    }

    private String getItemListString(long[] list) {
        StringBuilder sbWhere = new StringBuilder();
        sbWhere.append(MediaStore.Audio.Media._ID + " IN (");
        for (int i = 0; i < list.length; i++) {
            sbWhere.append(list[i]);
            if (i < list.length - 1) {
                sbWhere.append(",");
            }
        }
        sbWhere.append(")");
        return sbWhere.toString();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

}
