/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.oreo.gravitybox.visualizer;

import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarState;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.graphics.Palette;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import de.robv.android.xposed.XposedBridge;

import static android.support.v4.graphics.ColorUtils.HSLToColor;
import static android.support.v4.graphics.ColorUtils.LABToColor;
import static android.support.v4.graphics.ColorUtils.calculateContrast;
import static android.support.v4.graphics.ColorUtils.colorToHSL;
import static android.support.v4.graphics.ColorUtils.colorToLAB;

public class NavbarVisualizerLayout extends FrameLayout
        implements Palette.PaletteAsyncListener {

    private static final String TAG = "GB:NavbarVisualizerLayout";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private int mStatusBarState;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mPowerSaveMode = false;
    private boolean mDisplaying = false; // the state we're animating to

    private int mDefaultColor;
    private int mColor;
    private int mLightColor;
    private int mDarkColor;
    private boolean mLightNavbar;
    private ValueAnimator mColorAnimator;
    private int mOpacity;

    private NavbarVisualizerView mNavbarVisualizerView;

    public NavbarVisualizerLayout(Context context) throws Throwable {
        super(context, null, 0);

        mDefaultColor = Color.WHITE;
        mColor = Color.TRANSPARENT;
        mOpacity = 140;

        inflateLayout();
    }

    private void inflateLayout() throws Throwable {
        LayoutInflater inflater = LayoutInflater.from(Utils.getGbContext(getContext(),
                getContext().getResources().getConfiguration()));
        inflater.inflate(R.layout.navbarvisualizer, this);
        mNavbarVisualizerView = new NavbarVisualizerView(getContext());
        int idx = indexOfChild(findViewById(R.id.navbarvisualizer));
        removeViewAt(idx);
        addView(mNavbarVisualizerView, idx);
    }

    private void updateViewVisibility() {
        final int curVis = getVisibility();
        final int newVis = mStatusBarState == StatusBarState.SHADE ?
                View.VISIBLE : View.GONE;
        if (curVis != newVis) {
            setVisibility(newVis);
            checkStateChanged();
        }
    }

    void setVisible(boolean visible) {
        if (mVisible != visible) {
            if (DEBUG) {
                log("setVisible() called with visible = [" + visible + "]");
            }
            mVisible = visible;
            checkStateChanged();
        }
    }

    void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            if (DEBUG) {
                log("setPlaying() called with playing = [" + playing + "]");
            }
            mPlaying = playing;
            checkStateChanged();
        }
    }

    void setPowerSaveMode(boolean powerSaveMode) {
        if (mPowerSaveMode != powerSaveMode) {
            if (DEBUG) {
                log("setPowerSaveMode() called with powerSaveMode = [" + powerSaveMode + "]");
            }
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    void setStatusBarState(int statusBarState) {
        if (mStatusBarState != statusBarState) {
            mStatusBarState = statusBarState;
            updateViewVisibility();
        }
    }

    void setBitmap(Bitmap bitmap, boolean extractColor) {
        if (bitmap != null && extractColor) {
            Palette.from(bitmap).generate(this);
        } else {
            setColor(Color.TRANSPARENT);
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        int color = Color.TRANSPARENT;

        color = palette.getVibrantColor(color);
        if (color == Color.TRANSPARENT) {
            color = palette.getLightVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
            }
        }

        if (color != Color.TRANSPARENT) {
            mDarkColor = findContrastColor(color, Color.WHITE, true, 2);
            mLightColor = findContrastColorAgainstDark(color, Color.BLACK, true, 2);
        } else {
            mLightColor = Color.WHITE;
            mDarkColor = Color.BLACK;
        }

        setColor();
    }

    void setDefaultColor(int color) {
        mDefaultColor = color;
        setColor(mDefaultColor);
    }

    void setLeftInLandscape(boolean isLeft) {
        mNavbarVisualizerView.setLeftInLandscape(isLeft);
    }

    void setLightNavbar(boolean isLight) {
        if (mLightNavbar != isLight) {
            mLightNavbar = isLight;
            int color = isLight ? mDarkColor : mLightColor;
            mNavbarVisualizerView.setColor(Color.argb(
                    mOpacity, Color.red(color),
                    Color.green(color), Color.blue(color)));
        }
    }

    private void setColor() {
        int color = mLightNavbar ? mDarkColor : mLightColor;
        setColor(color);
    }

    private void setColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = mDefaultColor;
        }

        if (mColor != color) {
            final int oldColor = mColor;
            mColor = Color.argb(mOpacity, Color.red(color),
                    Color.green(color), Color.blue(color));
            if (mColorAnimator != null) {
                mColorAnimator.cancel();
            }
            mColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                    oldColor, mColor);
            mColorAnimator.setDuration(1200);
            mColorAnimator.setStartDelay(600);
            mColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    int c = (int) va.getAnimatedValue();
                    mNavbarVisualizerView.setColor(c);
                }
            });
            mColorAnimator.start();
        }
    }

    void setOpacityPercent(int opacityPercent) {
        mOpacity = Math.round(255f * ((float)opacityPercent/100f));
        mNavbarVisualizerView.setColor(Color.argb(mOpacity, Color.red(mColor),
                Color.green(mColor), Color.blue(mColor)));
    }

    private void checkStateChanged() {
        if (getVisibility() == View.VISIBLE &&
                mVisible && mPlaying && !mPowerSaveMode) {
            if (!mDisplaying) {
                mDisplaying = true;
                mNavbarVisualizerView.setPlaying(true, mVisible);
                }
        } else {
            if (mDisplaying) {
                mDisplaying = false;
                mNavbarVisualizerView.setPlaying(false, mVisible);
            }
        }
    }

    private int findContrastColor(int color, int other, boolean findFg, double minRatio) {
        int fg = findFg ? color : other;
        int bg = findFg ? other : color;
        if (calculateContrast(fg, bg) >= minRatio) {
            return color;
        }

        double[] lab = new double[3];
        colorToLAB(findFg ? fg : bg, lab);

        double low = 0, high = lab[0];
        final double a = lab[1], b = lab[2];
        for (int i = 0; i < 15 && high - low > 0.00001; i++) {
            final double l = (low + high) / 2;
            if (findFg) {
                fg = LABToColor(l, a, b);
            } else {
                bg = LABToColor(l, a, b);
            }
            if (calculateContrast(fg, bg) > minRatio) {
                low = l;
            } else {
                high = l;
            }
        }
        return LABToColor(low, a, b);
    }

    private int findContrastColorAgainstDark(int color, int other, boolean findFg,
                                                   double minRatio) {
        int fg = findFg ? color : other;
        int bg = findFg ? other : color;
        if (calculateContrast(fg, bg) >= minRatio) {
            return color;
        }

        float[] hsl = new float[3];
        colorToHSL(findFg ? fg : bg, hsl);

        float low = hsl[2], high = 1;
        for (int i = 0; i < 15 && high - low > 0.00001; i++) {
            final float l = (low + high) / 2;
            hsl[2] = l;
            if (findFg) {
                fg = HSLToColor(hsl);
            } else {
                bg = HSLToColor(hsl);
            }
            if (calculateContrast(fg, bg) > minRatio) {
                high = l;
            } else {
                low = l;
            }
        }
        return findFg ? fg : bg;
    }
}
