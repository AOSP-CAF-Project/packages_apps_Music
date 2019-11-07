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

import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.Log;

/**
 * A variant of MergeCursor that sorts the cursors being merged. If decent
 * performance is ever obtained, it can be put back under android.database.
 */
public class SortCursor extends AbstractCursor {
    private static final String TAG = "SortCursor";
    private final int ROWCACHESIZE = 64;
    private Cursor mCursor; // updated in onMove
    private Cursor[] mCursors;
    private int[] mSortColumns;
    private int[] mRowNumCache = new int[ROWCACHESIZE];
    private int[] mCursorCache = new int[ROWCACHESIZE];
    private int[][] mCurRowNumCache;
    private int mLastCacheHit = -1;

    private DataSetObserver mObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            // Reset our position so the optimizations in move-related code
            // don't screw us over
            mPos = -1;
        }

        @Override
        public void onInvalidated() {
            mPos = -1;
        }
    };

    public SortCursor(Cursor[] cursors, String sortcolumn) {
        mCursors = cursors;

        int length = mCursors.length;
        mSortColumns = new int[length];
        for (int i = 0; i < length; i++) {
            if (mCursors[i] == null) continue;

            // Register ourself as a data set observer
            mCursors[i].registerDataSetObserver(mObserver);

            mCursors[i].moveToFirst();

            // We don't catch the exception
            mSortColumns[i] = mCursors[i].getColumnIndexOrThrow(sortcolumn);
        }
        mCursor = null;
        String smallest = "";
        for (int j = 0; j < length; j++) {
            if (mCursors[j] == null || mCursors[j].isAfterLast())
                continue;
            String current = mCursors[j].getString(mSortColumns[j]);
            if (mCursor == null || current.compareToIgnoreCase(smallest) < 0) {
                smallest = current;
                mCursor = mCursors[j];
            }
        }

        for (int i = mRowNumCache.length - 1; i >= 0; i--) {
            mRowNumCache[i] = -2;
        }
        mCurRowNumCache = new int[ROWCACHESIZE][length];
    }

    @Override
    public int getCount() {
        int count = 0;
        int length = mCursors.length;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] != null) {
                count += mCursors[i].getCount();
            }
        }
        return count;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (oldPosition == newPosition)
            return true;

        /* Find the right cursor
         * Because the client of this cursor (the listadapter/view) tends
         * to jump around in the cursor somewhat, a simple cache strategy
         * is used to avoid having to search all cursors from the start.
         * TODO: investigate strategies for optimizing random access and
         * reverse-order access.
         */

        int cache_entry = newPosition % ROWCACHESIZE;

        if (mRowNumCache[cache_entry] == newPosition) {
            int which = mCursorCache[cache_entry];
            mCursor = mCursors[which];
            if (mCursor == null) {
                Log.w(TAG, "onMove: cache results in a null cursor.");
                return false;
            }
            mCursor.moveToPosition(mCurRowNumCache[cache_entry][which]);
            mLastCacheHit = cache_entry;
            return true;
        }

        mCursor = null;
        int length = mCursors.length;

        if (mLastCacheHit >= 0) {
            for (int i = 0; i < length; i++) {
                if (mCursors[i] == null) continue;
                mCursors[i].moveToPosition(mCurRowNumCache[mLastCacheHit][i]);
            }
        }

        if (newPosition < oldPosition || oldPosition == -1) {
            for (int i = 0; i < length; i++) {
                if (mCursors[i] == null) continue;
                mCursors[i].moveToFirst();
            }
            oldPosition = 0;
        }
        if (oldPosition < 0) {
            oldPosition = 0;
        }

        // search forward to the new position
        int smallestIdx = -1;
        for (int i = oldPosition; i <= newPosition; i++) {
            String smallest = "";
            smallestIdx = -1;
            for (int j = 0; j < length; j++) {
                if (mCursors[j] == null || mCursors[j].isAfterLast()) {
                    continue;
                }
                String current = mCursors[j].getString(mSortColumns[j]);
                if (smallestIdx < 0 || current.compareToIgnoreCase(smallest) < 0) {
                    smallest = current;
                    smallestIdx = j;
                }
            }
            if (i == newPosition) break;
            if (mCursors[smallestIdx] != null) {
                mCursors[smallestIdx].moveToNext();
            }
        }
        mCursor = mCursors[smallestIdx];
        mRowNumCache[cache_entry] = newPosition;
        mCursorCache[cache_entry] = smallestIdx;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] != null) {
                mCurRowNumCache[cache_entry][i] = mCursors[i].getPosition();
            }
        }
        mLastCacheHit = -1;
        return true;
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public int getType(int column) {
        return mCursor.getType(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    @Override
    public byte[] getBlob(int column) {
        return mCursor.getBlob(column);
    }

    @Override
    public String[] getColumnNames() {
        if (mCursor != null) {
            return mCursor.getColumnNames();
        } else {
            // All of the cursors may be empty, but they can still return
            // this information.
            int length = mCursors.length;
            for (int i = 0; i < length; i++) {
                if (mCursors[i] != null) {
                    return mCursors[i].getColumnNames();
                }
            }
            throw new IllegalStateException("No cursor that can return names");
        }
    }

    @Override
    public void deactivate() {
        int length = mCursors.length;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] == null) continue;
            mCursors[i].deactivate();
        }
    }

    @Override
    public void close() {
        int length = mCursors.length;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] == null) continue;
            mCursors[i].close();
        }
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        int length = mCursors.length;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] != null) {
                mCursors[i].registerDataSetObserver(observer);
            }
        }
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        int length = mCursors.length;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] != null) {
                mCursors[i].unregisterDataSetObserver(observer);
            }
        }
    }

    @Override
    public boolean requery() {
        int length = mCursors.length;
        for (int i = 0; i < length; i++) {
            if (mCursors[i] == null) continue;

            if (mCursors[i].requery() == false) {
                return false;
            }
        }

        return true;
    }
}
