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
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.aurora.music.MediaPlaybackActivity;
import com.aurora.music.MusicAlphabetIndexer;
import com.aurora.music.MusicUtils;
import com.aurora.music.R;
import com.aurora.music.TrackBrowserFragment;

import java.util.HashMap;

import static com.aurora.music.MusicUtils.Defs.ADD_TO_PLAYLIST;
import static com.aurora.music.MusicUtils.Defs.DELETE_ITEM;
import static com.aurora.music.MusicUtils.Defs.PLAY_SELECTION;
import static com.aurora.music.MusicUtils.mEditMode;
import static com.aurora.music.TrackBrowserFragment.DETAILS;
import static com.aurora.music.TrackBrowserFragment.LOGTAG;
import static com.aurora.music.TrackBrowserFragment.REMOVE;
import static com.aurora.music.TrackBrowserFragment.SHARE;

public class TrackListAdapter extends android.widget.SimpleCursorAdapter
        implements android.widget.SectionIndexer {
    private final BitmapDrawable mDefaultAlbumIcon;
    private final StringBuilder mBuilder = new StringBuilder();
    private final String mUnknownArtist;
    private final String mUnknownAlbum;
    private boolean mIsNowPlaying;
    private boolean mDisableNowPlayingIndicator;
    private int mTitleIdx;
    private int mArtistIdx;
    private int mDurationIdx;
    private int mAudioIdIdx;
    private int mAlbumIdx;
    private int mDataIdx = -1;
    private HashMap<View, Integer> mPositionMap;
    private AlphabetIndexer mIndexer;
    private TrackBrowserFragment mFragment = null;
    private TrackQueryHandler mQueryHandler;
    private String mConstraint = null;
    private boolean mConstraintIsValid = false;

    public TrackListAdapter(Context context,
                            TrackBrowserFragment currentaFragment, int layout,
                            Cursor cursor, String[] from, int[] to, boolean isnowplaying,
                            boolean disablenowplayingindicator) {
        super(context, layout, cursor, from, to);
        mFragment = currentaFragment;
        getColumnIndices(cursor);
        mIsNowPlaying = isnowplaying;
        mDisableNowPlayingIndicator = disablenowplayingindicator;
        mUnknownArtist = context.getString(R.string.unknown_artist_name);
        mUnknownAlbum = context.getString(R.string.unknown_album_name);
        Resources r = context.getResources();
        mDefaultAlbumIcon = (BitmapDrawable) r
                .getDrawable(R.drawable.unknown_albums);
        mQueryHandler = new TrackQueryHandler(context.getContentResolver());
        mPositionMap = new HashMap<View, Integer>();
    }

    public void setActivity(TrackBrowserFragment newfragment) {
        mFragment = newfragment;
    }

    public TrackQueryHandler getQueryHandler() {
        return mQueryHandler;
    }

    private void getColumnIndices(Cursor cursor) {
        if (cursor != null) {
            mTitleIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            mArtistIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            mDurationIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            try {
                mAlbumIdx = cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            } catch (Exception e) {

            }
            try {
                mAudioIdIdx = cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
            } catch (IllegalArgumentException ex) {
                mAudioIdIdx = cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            }

            if (mIndexer != null) {
                mIndexer.setCursor(cursor);
            } else if (!mFragment.mEditMode && mFragment.mAlbumId == null) {
                String alpha = mFragment
                        .getString(R.string.fast_scroll_alphabet);

                mIndexer = new MusicAlphabetIndexer(cursor, mTitleIdx,
                        alpha);
            }
            try {
                mDataIdx = cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            } catch (IllegalArgumentException ex) {
                Log.w(LOGTAG, "_data column not found. Exception : " + ex);
            }
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = super.newView(context, cursor, parent);
        // ImageView iv = (ImageView) v.findViewById(R.id.icon);
        // iv.setVisibility(View.GONE);

        ViewHolder vh = new ViewHolder();
        vh.line1 = v.findViewById(R.id.line1);
        vh.line2 = v.findViewById(R.id.line2);
        // vh.duration = (TextView) v.findViewById(R.id.duration);
        vh.play_indicator = v.findViewById(R.id.play_indicator);
        ((MediaPlaybackActivity) mFragment.getActivity())
                .setTouchDelegate(vh.play_indicator);
        vh.buffer1 = new CharArrayBuffer(100);
        vh.buffer2 = new char[200];
        // vh.drm_icon = (ImageView) v.findViewById(R.id.drm_icon);
        vh.anim_icon = v.findViewById(R.id.animView);
        vh.icon = v.findViewById(R.id.icon);

        v.setTag(vh);
        return v;
    }

    @Override
    public void bindView(final View view, Context context,
                         final Cursor cursor) {

        final ViewHolder vh = (ViewHolder) view.getTag();
        if (!mPositionMap.containsKey(view))
            mPositionMap.put(view, cursor.getPosition());
        cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
        vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);
        vh.mCurrentTrackName = cursor.getString(mTitleIdx);
        vh.mSelectedID = cursor.getLong(mAudioIdIdx);
        int secs = cursor.getInt(mDurationIdx) / 1000;
        vh.mCurrentTrackDuration = MusicUtils.makeTimeString(context, secs)
                + context.getString(R.string.duration_unit);
        final StringBuilder builder = mBuilder;
        builder.delete(0, builder.length());
        String name = cursor.getString(mArtistIdx);
        long aid = cursor.getLong(mAlbumIdx);
        if (MusicUtils.isGroupByFolder() && !mEditMode && mFragment.mParent != -1) {
            long l = cursor.getLong(1);
            if (vh.icon.getTag() != (Integer) vh.icon.getId()) {
                new MusicUtils.FolderBitmapThread(mFragment.mParentActivity, l,
                        mDefaultAlbumIcon, vh.icon).start();
                vh.icon.setTag(vh.icon.getId());
            }
        } else {
            if (vh.icon.getTag() != (Integer) vh.icon.getId()) {
                Drawable drawable = MusicUtils.getsArtCachedDrawable(context, aid);
                if (drawable != null) {
                    vh.icon.setImageDrawable(drawable);
                } else {
                    vh.icon.setImageDrawable(mDefaultAlbumIcon);
                }
                vh.icon.setTag(vh.icon.getId());
            }
        }

        if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
            // Reload the "unknown_artist_name" string in order to
            // avoid that this string doesn't change when user
            // changes the system language setting.
            name = context.getString(R.string.unknown_artist_name);
        }
        builder.append(name);
        vh.mCurrentArtistNameForAlbum = name;

        int len = builder.length();
        if (vh.buffer2.length < len) {
            vh.buffer2 = new char[len];
        }
        builder.getChars(0, len, vh.buffer2, 0);
        vh.line2.setText(vh.buffer2, 0, len);

        // Show DRM lock icon on track list
        if (mDataIdx != -1) {
            String data = cursor.getString(mDataIdx);
            boolean isDrm = !TextUtils.isEmpty(data)
                    && (data.endsWith(".dm") || data.endsWith(".dcf"));
            if (isDrm) {
                // vh.drm_icon.setVisibility(View.VISIBLE);
            } else {
                // vh.drm_icon.setVisibility(View.GONE);
            }
        }

        String albumArtName = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));
        if (albumArtName == null || albumArtName.equals(MediaStore.UNKNOWN_STRING)) {
            albumArtName = context.getString(R.string.unknown_album_name);
        }
        vh.mCurrentAlbumName = albumArtName;

        String trackPath = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        vh.mCurrentTrackPath = trackPath;

        String trackSize = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
        vh.mCurrentTrackSize = getHumanReadableSize(context, Integer.parseInt(trackSize));

        Bitmap albumArt;
        final ImageView iv = vh.play_indicator;
        iv.setTag(cursor.getPosition());
        iv.setOnClickListener(new View.OnClickListener() {

            @SuppressLint("NewApi")
            @Override
            public void onClick(final View v) {
                // TODO Auto-generated method stub
                // mActivity.mSelectedPosition = ;
                if (mFragment.mPopupMenu != null) {
                    mFragment.mPopupMenu.dismiss();
                }
                mFragment.mCurrentTrackName = vh.mCurrentTrackName;
                mFragment.mCurrentArtistNameForAlbum = vh.mCurrentArtistNameForAlbum;
                mFragment.mCurrentAlbumName = vh.mCurrentAlbumName;
                mFragment.mCurrentTrackDuration = vh.mCurrentTrackDuration;
                mFragment.mCurrentTrackPath = vh.mCurrentTrackPath;
                mFragment.mCurrentTrackSize = vh.mCurrentTrackSize;
                mFragment.mSelectedId = vh.mSelectedID;
                PopupMenu popup = new PopupMenu(mFragment.getActivity(), iv);
                popup.getMenu().add(0, PLAY_SELECTION, 0,
                        R.string.play_selection);
                if (mEditMode && !("nowplaying".equals(mFragment.mPlaylist))) {
                    popup.getMenu().add(0, REMOVE, 0,
                            R.string.remove_from_playlist);
                }
                mFragment.mSubMenu = popup.getMenu().addSubMenu(0,
                        ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
                MusicUtils.makePlaylistMenu(mFragment.getActivity(), mFragment.mSubMenu);
                popup.getMenu()
                        .add(0, DELETE_ITEM, 0, R.string.delete_item);
                MusicUtils.addSetRingtonMenu(popup.getMenu());
                popup.getMenu().add(0, SHARE, 0, R.string.share);
                popup.getMenu().add(0, DETAILS, 0, R.string.details);

                popup.show();
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // TODO Auto-generated method stub
                        mFragment.onContextItemSelected(item,
                                Integer.parseInt(v.getTag().toString()));
                        return true;
                    }
                });
                mFragment.mPopupMenu = popup;
            }
        });
        long id = -1;
        if (MusicUtils.sService != null) {
            // TODO: IPC call on each bind??
            try {
                if (mIsNowPlaying) {
                    id = MusicUtils.sService.getQueuePosition();
                } else {
                    id = MusicUtils.sService.getAudioId();
                }
            } catch (RemoteException ex) {
            }
        }

        mFragment.mAnimView = vh.anim_icon;

        // Determining whether and where to show the "now playing indicator
        // is tricky, because we don't actually keep track of where the
        // songs
        // in the current playlist came from after they've started playing.
        //
        // If the "current playlists" is shown, then we can simply match by
        // position,
        // otherwise, we need to match by id. Match-by-id gets a little
        // weird if
        // a song appears in a playlist more than once, and you're in
        // edit-playlist
        // mode. In that case, both items will have the "now playing"
        // indicator.
        // For this reason, we don't show the play indicator at all when in
        // edit
        // playlist mode (except when you're viewing the "current playlist",
        // which is not really a playlist)
        if ((mIsNowPlaying && cursor.getPosition() == id)
                || (!mIsNowPlaying && cursor.getLong(mAudioIdIdx) == id)) {
            // We set different icon according to different play state
            mFragment.mAnimView.setVisibility(View.VISIBLE);
            mFragment.mAnimView.animate(MusicUtils.isPlaying());
        } else {
            mFragment.mAnimView.animate(false);
            mFragment.mAnimView.setVisibility(View.INVISIBLE);
        }
    }

    private String getHumanReadableSize(Context context, int size) {
        // TODO Auto-generated method stub
        Resources res = context.getResources();
        final int[] magnitude = {
                R.string.size_bytes,
                R.string.size_kilobytes,
                R.string.size_megabytes,
                R.string.size_gigabytes
        };

        double aux = size;
        int cc = magnitude.length;
        for (int i = 0; i < cc; i++) {
            if (aux < 1024) {
                double cleanSize = Math.round(aux * 100);
                return cleanSize / 100 +
                        " " + res.getString(magnitude[i]); //$NON-NLS-1$
            } else {
                aux = aux / 1024;
            }
        }
        double cleanSize = Math.round(aux * 100);
        return cleanSize / 100 +
                " " + res.getString(magnitude[cc - 1]); //$NON-NLS-1$
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (mFragment.getParentActivity().isFinishing() && cursor != null) {
            cursor.close();
            cursor = null;
        }
        if (cursor != mFragment.mTrackCursor) {
            if (mFragment.mTrackCursor != null) {
                mFragment.mTrackCursor.close();
                mFragment.mTrackCursor = null;
            }
            mFragment.mTrackCursor = cursor;
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
        Cursor c = mFragment.getTrackCursor(mQueryHandler, s, false);
        mConstraint = s;
        mConstraintIsValid = true;
        return c;
    }

    // SectionIndexer methods
    public Object[] getSections() {
        if (mIndexer != null) {
            return mIndexer.getSections();
        } else {
            return new String[]{" "};
        }
    }

    public int getPositionForSection(int section) {
        if (mIndexer != null) {
            return mIndexer.getPositionForSection(section);
        }
        return 0;
    }

    public int getSectionForPosition(int position) {
        return 0;
    }

    class ViewHolder {
        TextView line1;
        TextView line2;
        TextView duration;
        ImageView play_indicator;
        CharArrayBuffer buffer1;
        char[] buffer2;
        ImageView drm_icon;
        TrackBrowserFragment.WaveView anim_icon;
        ImageView icon;
        String mCurrentTrackName;
        String mCurrentAlbumName;
        String mCurrentArtistNameForAlbum;
        String mCurrentTrackDuration;
        String mCurrentTrackPath;
        String mCurrentTrackSize;
        long mSelectedID;
        int position = -1;
    }

    @SuppressLint("HandlerLeak")
    public class TrackQueryHandler extends AsyncQueryHandler {

        TrackQueryHandler(ContentResolver res) {
            super(res);
        }

        public Cursor doQuery(Uri uri, String[] projection,
                              String selection, String[] selectionArgs, String orderBy,
                              boolean async) {
            if (async) {
                // Get 500 results first, which is enough to allow the user
                // to start scrolling,
                // while still being very fast.
                Uri limituri = uri.buildUpon()
                        .appendQueryParameter("limit", "500").build();
                QueryArgs args = new QueryArgs();
                args.uri = uri;
                args.projection = projection;
                args.selection = selection;
                args.selectionArgs = selectionArgs;
                args.orderBy = orderBy;

                startQuery(0, args, limituri, projection, selection,
                        selectionArgs, orderBy);
                return null;
            }
            return MusicUtils.query(mFragment.getActivity(), uri,
                    projection, selection, selectionArgs, orderBy);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie,
                                       Cursor cursor) {
            // Log.i("@@@", "query complete: " + cursor.getCount() + "   " +
            // mActivity);
            mFragment.init(cursor, cookie != null);
            if (token == 0 && cookie != null && cursor != null
                    && !cursor.isClosed() && cursor.getCount() >= 100) {
                QueryArgs args = (QueryArgs) cookie;
                startQuery(1, null, args.uri, args.projection,
                        args.selection, args.selectionArgs, args.orderBy);
            }
        }

        class QueryArgs {
            public Uri uri;
            public String[] projection;
            public String selection;
            public String[] selectionArgs;
            public String orderBy;
        }
    }
}
