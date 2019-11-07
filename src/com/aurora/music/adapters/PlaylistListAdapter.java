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

package com.aurora.music.adapters;

import android.annotation.SuppressLint;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.aurora.music.MusicUtils;
import com.aurora.music.PlaylistBrowserFragment;
import com.aurora.music.R;

import static com.aurora.music.MusicUtils.Defs.PLAY_SELECTION;
import static com.aurora.music.PlaylistBrowserFragment.ADD_BY_FILEMANAGER;
import static com.aurora.music.PlaylistBrowserFragment.ALL_SONGS_PLAYLIST;
import static com.aurora.music.PlaylistBrowserFragment.DELETE_PLAYLIST;
import static com.aurora.music.PlaylistBrowserFragment.EDIT_PLAYLIST;
import static com.aurora.music.PlaylistBrowserFragment.FAVORITE_PLAYLIST;
import static com.aurora.music.PlaylistBrowserFragment.RECENTLY_ADDED_PLAYLIST;
import static com.aurora.music.PlaylistBrowserFragment.RENAME_PLAYLIST;
import static com.aurora.music.PlaylistBrowserFragment.playlistMap;

public class PlaylistListAdapter extends SimpleCursorAdapter {
    public BitmapDrawable mDefaultAlbumIcon;
    public AsyncQueryHandler mQueryHandler;
    public String mConstraint = null;
    public boolean mConstraintIsValid = false;
    private PlaylistBrowserFragment mFragment = null;
    private int mTitleIdx;
    private int mIdIdx;
    private Cursor mCursor;

    public PlaylistListAdapter(Context context,
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
        vh.albumArtIcon1 = v.findViewById(R.id.icon);
        vh.albumArtIcon2 = v.findViewById(R.id.icon1);
        vh.albumArtIcon3 = v.findViewById(R.id.icon2);
        vh.albumArtIcon4 = v.findViewById(R.id.icon3);

        vh.tv = v.findViewById(R.id.line1);
        Resources r = context.getResources();
        mDefaultAlbumIcon = (BitmapDrawable) r
                .getDrawable(R.drawable.album_cover);
        v.setTag(vh);
        return v;
    }

    private void updateAlbumArray(int id, ViewHolder vh) {
        Bitmap[] albumartArray = null;
        albumartArray = playlistMap.get(id);
        ImageView[] imageViews = new ImageView[]{vh.albumArtIcon1,
                vh.albumArtIcon2, vh.albumArtIcon3, vh.albumArtIcon4};
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

        final int favoriteId = MusicUtils.idForplaylist(context, "My Favorite");

        Bitmap[] albumartArray = null;

        if (id == FAVORITE_PLAYLIST) {
            albumartArray = playlistMap.get(favoriteId);
        } else {
            albumartArray = playlistMap.get(id);
        }

        if (albumartArray == null) {
            if (id == FAVORITE_PLAYLIST) {
                new DownloadAlbumArt(favoriteId, vh).execute();
            } else {
                new DownloadAlbumArt(id, vh).execute();
            }
        } else {
            if (id == FAVORITE_PLAYLIST) {
                updateAlbumArray(favoriteId, vh);
            } else {
                updateAlbumArray(id, vh);
            }
        }

        final ImageView menu = view
                .findViewById(R.id.play_indicator);
        menu.setTag(id);
        menu.setOnClickListener(v -> {
            if (mFragment.mPopupMenu != null) {
                mFragment.mPopupMenu.dismiss();
            }
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
            if (mFragment.getParentActivity().getResources()
                    .getBoolean(R.bool.add_playlist_by_filemanager)) {
                if (id != RECENTLY_ADDED_PLAYLIST && id != ALL_SONGS_PLAYLIST) {
                    popup.getMenu().add(0, ADD_BY_FILEMANAGER, 0,
                            R.string.add_by_filemanager);
                }
            }
            popup.show();
            popup.setOnMenuItemClickListener(item -> {
                if (!mFragment.isDetached()) {
                    mFragment.onContextItemSelected(item,
                            Integer.parseInt(menu.getTag().toString()));
                }
                return true;
            });
            mFragment.mPopupMenu = popup;
        });
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (mFragment.getParentActivity().isFinishing() && cursor != null) {
            cursor.close();
            cursor = null;
        }
        if (cursor != mFragment.mPlaylistCursor) {
            if (mFragment.mPlaylistCursor != null) {
                mFragment.mPlaylistCursor.close();
                mFragment.mPlaylistCursor = null;
            }
            mFragment.mPlaylistCursor = cursor;
            if (cursor == null || !cursor.isClosed()) {
                super.changeCursor(cursor);
                getColumnIndices(cursor);
            }
        }
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String s = constraint.toString();
        if (mConstraintIsValid && s.equals(mConstraint)) {
            return getCursor();
        }
        Cursor c = mFragment.getPlaylistCursor(null, s);
        mConstraint = s;
        mConstraintIsValid = true;
        return c;
    }

    public class ViewHolder {
        public TextView tv;
        ImageView albumArtIcon1, albumArtIcon2, albumArtIcon3,
                albumArtIcon4;
        CharSequence mTitle;
    }

    @SuppressLint("HandlerLeak")
    public class QueryHandler extends AsyncQueryHandler {
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

    @SuppressLint("StaticFieldLeak")
    private class DownloadAlbumArt extends AsyncTask<Void, Void, Void> {
        String[] mPlaylistMemberCols1 = new String[]{
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST};
        int id;
        Bitmap[] mAlbumArtArray;
        ViewHolder vh;

        public DownloadAlbumArt(int id, ViewHolder vh) {
            this.id = id;
            this.vh = vh;
        }

        @Override
        protected void onPostExecute(Void result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);
            if (mAlbumArtArray != null && !MusicUtils.mIsScreenOff) {
                updateAlbumArray(id, vh);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (id != -1) {
                    Uri uri = MediaStore.Audio.Playlists.Members
                            .getContentUri("external", (long) id);
                    try (Cursor ret = mFragment.getParentActivity().getContentResolver().query(
                            uri, mPlaylistMemberCols1, null, null, null)) {
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
                                        mFragment.getParentActivity(), albumId,
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
                    } catch (SQLiteException e) {
                    }
                } else {
                    int X = MusicUtils.getIntPref(mFragment.getParentActivity(),
                            "numweeks", 2) * (3600 * 24 * 7);
                    final String[] ccols = new String[]{MediaStore.Audio.Media.ALBUM_ID};
                    String where1 = MediaStore.MediaColumns.DATE_ADDED
                            + ">" + (System.currentTimeMillis() / 1000 - X);
                    try (Cursor cursor = MusicUtils.query(mFragment.getParentActivity(),
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            ccols, where1, null,
                            MediaStore.Audio.Media.DEFAULT_SORT_ORDER)) {
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
                                        mFragment.getParentActivity(), albumId,
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
                if (mAlbumArtArray != null)
                    playlistMap.put(id, mAlbumArtArray);
            } catch (Exception e) {
                System.out.println("Exception caught" + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }

    }
}