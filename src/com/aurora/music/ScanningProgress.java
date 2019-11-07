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
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.Window;
import android.view.WindowManager;

public class ScanningProgress extends Activity {
    private final static int CHECK = 0;
    private final static int EXTERNAL_CHECK_OK = 1;
    private final static int EXTERNAL_CHECK_FAILED = 2;
    private Runnable mCheckExternalMediaThread = new Runnable() {
        @Override
        public void run() {
            int result = isExternalMediaDatabaseReady() ?
                    EXTERNAL_CHECK_OK : EXTERNAL_CHECK_FAILED;
            Message message = mHandler.obtainMessage(result);
            mHandler.sendMessage(message);
        }
    };
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CHECK:
                    String status = Environment.getExternalStorageState();
                    if (!status.equals(Environment.MEDIA_MOUNTED)) {
                        // If the card suddenly got unmounted again, there's
                        // really no need to keep waiting for the media scanner.
                        finish();
                        return;
                    }

                    // new one thread to run the runnable,
                    // the thread will quit it self after running
                    new Thread(mCheckExternalMediaThread).start();

                    break;
                case EXTERNAL_CHECK_OK:
                    setResult(RESULT_OK);
                    finish();
                    return;
                case EXTERNAL_CHECK_FAILED:
                    Message next = obtainMessage(CHECK);
                    sendMessageDelayed(next, 3000);
                    break;
                default:
                    break;
            }
        }
    };

    private boolean isExternalMediaDatabaseReady() {
        boolean isReady = false;
        Cursor cursor = null;
        try {
            cursor = MusicUtils.query(ScanningProgress.this,
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    null, null, null, null);
            isReady = (cursor != null);
        } catch (Exception exception) {
            isReady = false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return isReady;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (android.os.Environment.isExternalStorageRemovable()) {
            setContentView(R.layout.scanning);
        } else {
            setContentView(R.layout.scanning_nosdcard);
        }
        getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        setResult(RESULT_CANCELED);

        Message msg = mHandler.obtainMessage(CHECK);
        mHandler.sendMessageDelayed(msg, 1000);
    }

    @Override
    public void onDestroy() {
        mHandler.removeMessages(CHECK);
        super.onDestroy();
    }
}
