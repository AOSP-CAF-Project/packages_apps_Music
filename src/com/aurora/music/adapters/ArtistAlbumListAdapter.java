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
import android.database.CursorWrapper;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.TextView;

import com.aurora.music.ArtistAlbumBrowserFragment;
import com.aurora.music.MediaPlaybackActivity;
import com.aurora.music.MusicAlphabetIndexer;
import com.aurora.music.MusicUtils;
import com.aurora.music.R;

import static com.aurora.music.ArtistAlbumBrowserFragment.mSub;
import static com.aurora.music.MusicUtils.Defs.ADD_TO_PLAYLIST;
import static com.aurora.music.MusicUtils.Defs.DELETE_ITEM;
import static com.aurora.music.MusicUtils.Defs.PLAY_SELECTION;

public class ArtistAlbumListAdapter extends SimpleCursorTreeAdapter
        implements SectionIndexer {

    private final BitmapDrawable mDefaultArtistIcon;
    private final BitmapDrawable mDefaultAlbumIcon;
    private final Context mContext;
    private final Resources mResources;
    private final String mAlbumSongSeparator;
    private final String mUnknownAlbum;
    private final StringBuilder mBuffer = new StringBuilder();
    private final Object[] mFormatArgs = new Object[1];
    private final Object[] mFormatArgs3 = new Object[3];
    Cursor mAlbumCursor;
    private Drawable mNowPlayingOverlay;
    private int mGroupArtistIdIdx;
    private int mGroupArtistIdx;
    private int mGroupAlbumIdx;
    private int mGroupSongIdx;
    private MusicAlphabetIndexer mIndexer;
    private ArtistAlbumBrowserFragment mFragment;
    private AsyncQueryHandler mQueryHandler;
    private String mConstraint = null;
    private String mUnknownArtist;
    private boolean mConstraintIsValid = false;

    public ArtistAlbumListAdapter(Context context,
                                  ArtistAlbumBrowserFragment fragment, Cursor cursor,
                                  int glayout, String[] gfrom, int[] gto, int clayout,
                                  String[] cfrom, int[] cto) {
        super(context, cursor, glayout, gfrom, gto, clayout, cfrom, cto);
        mQueryHandler = new QueryHandler(context.getContentResolver());
        mFragment = fragment;
        Resources r = context.getResources();
        mNowPlayingOverlay = r
                .getDrawable(R.drawable.indicator_ic_mp_playing_list);
        mDefaultArtistIcon = (BitmapDrawable) r
                .getDrawable(R.drawable.unknown_artists);
        mDefaultAlbumIcon = (BitmapDrawable) r
                .getDrawable(R.drawable.unknown_albums);
        // no filter or dither, it's a lot faster and we can't tell the
        // difference
        mDefaultAlbumIcon.setFilterBitmap(false);
        mDefaultAlbumIcon.setDither(false);
        mContext = context;
        getColumnIndices(cursor);
        mResources = context.getResources();
        mAlbumSongSeparator = context
                .getString(R.string.albumsongseparator);
        mUnknownAlbum = context.getString(R.string.unknown_album_name);
        mUnknownArtist = context.getString(R.string.unknown_artist_name);
    }

    private void getColumnIndices(Cursor cursor) {
        if (cursor != null) {

            mGroupArtistIdIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Artists._ID);
            mGroupArtistIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST);
            mGroupAlbumIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS);
            mGroupSongIdx = cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS);

            if (mIndexer != null) {
                mIndexer.setCursor(cursor);
            } else {
                mIndexer = new MusicAlphabetIndexer(cursor,
                        mGroupArtistIdx,
                        mResources.getString(R.string.fast_scroll_alphabet));
            }
        }
    }

    public void setFragment(ArtistAlbumBrowserFragment newFragment) {
        mFragment = newFragment;
    }

    public AsyncQueryHandler getQueryHandler() {
        return mQueryHandler;
    }

    @Override
    public View newGroupView(Context context, Cursor cursor,
                             boolean isExpanded, ViewGroup parent) {
        View v = super.newGroupView(context, cursor, isExpanded, parent);
        ViewHolder vh = new ViewHolder();
        vh.line1 = v.findViewById(R.id.line1);
        vh.line2 = v.findViewById(R.id.line2);
        vh.play_indicator = v.findViewById(R.id.play_indicator);
        ((MediaPlaybackActivity) mFragment.getParentActivity())
                .setTouchDelegate(vh.play_indicator);
        vh.mImgHolder = v.findViewById(R.id.iconTabbedView);
        vh.mChild1 = v.findViewById(R.id.img1);
        vh.mChild2 = v.findViewById(R.id.img2);
        vh.mChild3 = v.findViewById(R.id.img3);
        vh.mChild4 = v.findViewById(R.id.img4);
        v.setTag(vh);
        return v;
    }

    @Override
    public View newChildView(Context context, Cursor cursor,
                             boolean isLastChild, ViewGroup parent) {
        View v = super.newChildView(context, cursor, isLastChild, parent);
        ViewHolder vh = new ViewHolder();
        vh.line1 = v.findViewById(R.id.line1);
        vh.line2 = v.findViewById(R.id.line2);
        vh.play_indicator = v
                .findViewById(R.id.play_indicator_child);
        ((MediaPlaybackActivity) mFragment.getParentActivity())
                .setTouchDelegate(vh.play_indicator);
        vh.mImgHolder = v.findViewById(R.id.iconTabbedView);
        vh.icon = v.findViewById(R.id.icon);
        vh.icon.setBackgroundDrawable(mDefaultAlbumIcon);
        v.setTag(vh);
        return v;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void bindGroupView(View view, Context context, Cursor cursor,
                              boolean isexpanded) {

        final ViewHolder vh = (ViewHolder) view.getTag();
        ImageView[] layout = new ImageView[]{vh.mChild1, vh.mChild2,
                vh.mChild3, vh.mChild4};
        for (ImageView img : layout) img.setVisibility(View.GONE);

        vh.mImgHolder.setBackgroundResource(R.drawable.unknown_artists);
        String artist = cursor.getString(mGroupArtistIdx);
        vh.mCurrentArtistID = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Artists._ID));
        String displayartist = artist;
        vh.mCurrentArtistName = displayartist;
        boolean unknown = artist == null
                || artist.equals(MediaStore.UNKNOWN_STRING);
        mUnknownArtist = mResources.getString(R.string.unknown_artist_name);
        int numalbums = cursor.getInt(mGroupAlbumIdx);
        int numsongs = cursor.getInt(mGroupSongIdx);
        if (unknown) {
            displayartist = mUnknownArtist;
            vh.mImgHolder.setBackgroundDrawable(mDefaultArtistIcon);
        } else {
            Bitmap[] albumArts = MusicUtils.getFromLruCache(
                    displayartist, MusicUtils.mArtCache);
            if (albumArts == null) {
            } else {
                if (numalbums == 1) {
                    for (int i = 0; i < layout.length; i++) {
                        layout[i].setVisibility(View.GONE);
                    }
                    if (albumArts[0] != null) {
                        vh.mImgHolder.setBackground(new BitmapDrawable(
                                mFragment.getResources(), albumArts[0]));
                    } else {
                        vh.mImgHolder.setBackground(mDefaultAlbumIcon);
                    }
                } else if (numalbums > 1) {
                    vh.mImgHolder.setBackground(null);
                    if (numalbums == 2) {
                        for (int i = 0; i < layout.length; i++) {
                            layout[i].setVisibility(View.VISIBLE);
                        }
                        if (albumArts != null && albumArts.length == 2) {
                            layout[0].setImageBitmap(albumArts[0]);
                            layout[1].setImageBitmap(albumArts[1]);
                            layout[2].setImageBitmap(albumArts[1]);
                            layout[3].setImageBitmap(albumArts[0]);
                        } else {
                            for (int i = 0; i < layout.length; i++) {
                                layout[i].setImageBitmap(mDefaultAlbumIcon
                                        .getBitmap());
                            }
                        }

                    } else {
                        if (numalbums == 3) {
                            layout[3].setVisibility(View.GONE);
                        }
                        for (int i = 0; i < albumArts.length; i++) {
                            layout[i].setVisibility(View.VISIBLE);
                            if (albumArts[i] != null) {
                                layout[i].setImageBitmap(albumArts[i]);
                            } else {
                                layout[i].setImageBitmap(mDefaultAlbumIcon
                                        .getBitmap());
                            }
                        }
                    }
                }
            }
        }
        vh.line1.setText(displayartist);
        String songs_albums = MusicUtils.makeAlbumsLabel(context,
                numalbums, numsongs, unknown);

        vh.line2.setText(songs_albums);
        // We set different icon according to different play state
        if (MusicUtils.isPlaying()) {
            mNowPlayingOverlay = mResources
                    .getDrawable(R.drawable.indicator_ic_mp_playing_list);
        } else {
            mNowPlayingOverlay = mResources
                    .getDrawable(R.drawable.indicator_ic_mp_pause_list);
        }
        vh.play_indicator.setOnClickListener(new View.OnClickListener() {

            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                if (mFragment.mPopupMenu != null) {
                    mFragment.mPopupMenu.dismiss();
                }
                // TODO Auto-generated method stub
                mFragment.mCurrentArtistId = vh.mCurrentArtistID;
                mFragment.mCurrentArtistName = vh.mCurrentArtistName;
                PopupMenu popup = new PopupMenu(mFragment
                        .getParentActivity(), vh.play_indicator);
                popup.getMenu().add(0, PLAY_SELECTION, 0,
                        R.string.play_selection);
                mSub = popup.getMenu().addSubMenu(0,
                        ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
                MusicUtils.makePlaylistMenu(mFragment.getParentActivity(),
                        mSub);
                popup.getMenu()
                        .add(0, DELETE_ITEM, 0, R.string.delete_item);
                popup.show();
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // TODO Auto-generated method stub
                        mFragment.onContextItemSelected(item);
                        return true;
                    }
                });
                mFragment.mPopupMenu = popup;
            }
        });
    }

    @Override
    public void bindChildView(View view, Context context, Cursor cursor,
                              boolean islast) {

        final ViewHolder vh = (ViewHolder) view.getTag();
        vh.mCurrentArtistID = cursor.getString(mGroupArtistIdIdx);
        String name = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));
        String displayname = name;
        boolean unknown = name == null
                || name.equals(MediaStore.UNKNOWN_STRING);
        if (unknown) {
            displayname = mUnknownAlbum;
        }
        vh.mCurrentArtistName = cursor.getString(mGroupArtistIdx);
        vh.mCurrentAlbumID = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));
        vh.line1.setText(displayname);
        vh.mCurrentAlbumName = displayname;
        int numsongs = cursor
                .getInt(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS));
        int numartistsongs = cursor
                .getInt(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS_FOR_ARTIST));

        //final StringBuilder builder = mBuffer;
        //builder.delete(0, builder.length());
        if (unknown) {
            numsongs = numartistsongs;
        }
        String albumSongNoString = MusicUtils.makeArtistAlbumsSongsLabel(context, numartistsongs);
        vh.line2.setText(albumSongNoString);

        ImageView iv = vh.icon;
        // We don't actually need the path to the thumbnail file,
        // we just use it to see if there is album art or not
        String art = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART));
        if (unknown || art == null || art.length() == 0) {
            vh.icon.setBackgroundDrawable(mDefaultAlbumIcon);
            iv.setImageDrawable(null);
        } else {
            long artIndex = cursor.getLong(0);
            Drawable d = MusicUtils.getCachedArtwork(context, artIndex,
                    mDefaultAlbumIcon);
            vh.icon.setImageDrawable(d);
        }

        long currentalbumid = MusicUtils.getCurrentAlbumId();
        long aid = cursor.getLong(0);
        iv = vh.play_indicator;

        // We set different icon according to different play state
        Resources res = context.getResources();
        if (MusicUtils.isPlaying()) {
            mNowPlayingOverlay = res
                    .getDrawable(R.drawable.indicator_ic_mp_playing_list);
        } else {
            mNowPlayingOverlay = res
                    .getDrawable(R.drawable.indicator_ic_mp_pause_list);
        }

        mFragment.mCurrentAlbumId = cursor.getString(cursor
                .getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));

        iv.setOnClickListener(new View.OnClickListener() {

            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                if (mFragment.mPopupMenu != null) {
                    mFragment.mPopupMenu.dismiss();
                }
                // TODO Auto-generated method stub
                // mFragment.mCurrentArtistId = vh.mCurrentArtistID;
                mFragment.mCurrentArtistName = vh.mCurrentArtistName;
                mFragment.mCurrentAlbumName = vh.mCurrentAlbumName;
                mFragment.mCurrentAlbumId = vh.mCurrentAlbumID;
                mFragment.mCurrentArtistId = null;
                PopupMenu popup = new PopupMenu(mFragment
                        .getParentActivity(), vh.play_indicator);
                popup.getMenu().add(0, PLAY_SELECTION, 0,
                        R.string.play_selection);
                mSub = popup.getMenu().addSubMenu(0,
                        ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
                MusicUtils.makePlaylistMenu(mFragment.getParentActivity(),
                        mSub);
                popup.getMenu()
                        .add(0, DELETE_ITEM, 0, R.string.delete_item);
                popup.show();
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // TODO Auto-generated method stub
                        mFragment.onContextItemSelected(item);
                        return true;
                    }
                });
                mFragment.mPopupMenu = popup;
            }
        });
    }

    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {

        long id = groupCursor.getLong(groupCursor
                .getColumnIndexOrThrow(MediaStore.Audio.Artists._ID));

        String[] cols = new String[]{MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS_FOR_ARTIST,
                MediaStore.Audio.Albums.ALBUM_ART};
        Cursor c = MusicUtils.query(mFragment.getParentActivity(),
                MediaStore.Audio.Artists.Albums.getContentUri("external",
                        id), cols, null, null,
                MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);

        class MyCursorWrapper extends CursorWrapper {
            String mArtistName;
            int mMagicColumnIdx;

            MyCursorWrapper(Cursor c, String artist) {
                super(c);
                mArtistName = artist;
                if (mArtistName == null
                        || mArtistName.equals(MediaStore.UNKNOWN_STRING)) {
                    mArtistName = mUnknownArtist;
                }
                if (c != null)
                    mMagicColumnIdx = c.getColumnCount();
            }

            @Override
            public String getString(int columnIndex) {
                if (columnIndex != mMagicColumnIdx) {
                    return super.getString(columnIndex);
                }
                return mArtistName;
            }

            @Override
            public int getColumnIndexOrThrow(String name) {
                if (MediaStore.Audio.Albums.ARTIST.equals(name)) {
                    return mMagicColumnIdx;
                }
                return super.getColumnIndexOrThrow(name);
            }

            @Override
            public String getColumnName(int idx) {
                if (idx != mMagicColumnIdx) {
                    return super.getColumnName(idx);
                }
                return MediaStore.Audio.Albums.ARTIST;
            }

            @Override
            public int getColumnCount() {
                return super.getColumnCount() + 1;
            }
        }
        return new MyCursorWrapper(c,
                groupCursor.getString(mGroupArtistIdx));
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (mFragment.getParentActivity().isFinishing() && cursor != null) {
            cursor.close();
            cursor = null;
        }
        if (cursor != mFragment.mArtistCursor) {
            if (mFragment.mArtistCursor != null) {
                mFragment.mArtistCursor.close();
                mFragment.mArtistCursor = null;
            }
            mFragment.mArtistCursor = cursor;
            if (cursor == null || !cursor.isClosed()) {
                getColumnIndices(cursor);
                super.changeCursor(cursor);
            }
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
        Cursor c = mFragment.getArtistCursor(null, s);
        mConstraint = s;
        mConstraintIsValid = true;
        return c;
    }

    public Object[] getSections() {
        return mIndexer.getSections();
    }

    public int getPositionForSection(int sectionIndex) {
        return mIndexer.getPositionForSection(sectionIndex);
    }

    public int getSectionForPosition(int position) {
        return 0;
    }

    static class ViewHolder {
        TextView line1;
        TextView line2;
        ImageView play_indicator;
        ImageView mChild1, mChild2, mChild3, mChild4, icon;
        GridLayout mImgHolder;
        boolean isViewAdded;
        String aName;
        View view = null;
        String mCurrentArtistID, mCurrentAlbumID, mCurrentArtistName,
                mCurrentAlbumName;
    }

    class QueryHandler extends AsyncQueryHandler {
        QueryHandler(ContentResolver res) {
            super(res);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie,
                                       Cursor cursor) {
            // Log.i("@@@", "query complete");
            mFragment.init(cursor);
        }
    }
}