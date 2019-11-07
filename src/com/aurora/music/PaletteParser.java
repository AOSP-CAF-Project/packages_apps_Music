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

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.palette.graphics.Palette;

public class PaletteParser {

    private @ColorInt
    int colorPrimary;
    private @ColorInt
    int colorPrimaryDark;
    private @ColorInt
    int colorBackground;
    private @ColorInt
    int colorBackgroundDark;
    private @ColorInt
    int colorAccent;
    private @ColorInt
    int colorText;
    private @ColorInt
    int colorTextPrimary;
    private @ColorInt
    int colorTextSecondary;
    private @ColorInt
    int colorControlActivated;

    private Palette palette;

    public PaletteParser(Palette palette) {
        this.palette = palette;
        extractColors();
    }

    public int getColorControlActivated() {
        return colorControlActivated;
    }

    private void extractColors() {
        Palette.Swatch swatch = palette.getDominantSwatch();

        //Make sure we get a fallback swatch if LightVibrantSwatch is not available
        if (swatch == null)
            swatch = palette.getDarkVibrantSwatch();

        //Make sure we get another fallback swatch if DarkVibrantSwatch is not available
        if (swatch == null)
            swatch = palette.getVibrantSwatch();

        colorTextPrimary = swatch.getBodyTextColor();
        colorTextSecondary = swatch.getTitleTextColor();

        colorPrimary = swatch.getRgb();
        colorPrimaryDark = ColorUtil.manipulateColor(colorPrimary, 0.70f);

        if (ColorUtil.isColorLight(getColorPrimary())) {
            colorControlActivated = Color.BLACK;
            colorText = Color.DKGRAY;
        } else {
            colorControlActivated = Color.WHITE;
            colorText = Color.LTGRAY;
        }

        colorBackground = ColorUtil.manipulateColor(colorPrimary, 0.35f);
        colorBackgroundDark = ColorUtil.manipulateColor(colorBackground, 0.70f);

        colorAccent = palette.getVibrantColor(Color.LTGRAY);
    }

    public int getColorPrimary() {
        return colorPrimary;
    }

    public int getColorPrimaryDark() {
        return colorPrimaryDark;
    }

    public int getColorBackground() {
        return colorBackground;
    }

    public int getColorBackgroundDark() {
        return colorBackgroundDark;
    }

    public int getColorAccent() {
        return colorAccent;
    }

    public int getColorTextPrimary() {
        return colorTextPrimary;
    }

    public int getColorTextSecondary() {
        return colorTextSecondary;
    }
}
