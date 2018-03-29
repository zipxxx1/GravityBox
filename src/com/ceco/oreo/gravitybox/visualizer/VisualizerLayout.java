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

import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarState;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v7.graphics.Palette;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class VisualizerLayout extends FrameLayout
        implements Palette.PaletteAsyncListener, View.OnClickListener {

    private static final String TAG = "GB:VisualizerLayout";
    private static final boolean DEBUG = false;

    private static final long DIM_STATE_DELAY = 10000l;

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
    private ValueAnimator mColorAnimator;
    private int mBgColor;
    private ValueAnimator mBgColorAnimator;
    private int mOpacity;
    private boolean mActiveMode;
    private int mPosition;
    private boolean mIsDimmed;
    private boolean mDimEnabled;
    private int mDimLevel;
    private boolean mDimInfoEnabled;
    private boolean mDimHeaderEnabled;
    private boolean mDimControlsEnabled;
    private boolean mDimArtworkEnabled;
    private PowerManager mPowerManager;
    private AudioManager mAudioManager;

    private View mScrim;
    private TextClock mClock;
    private TextView mBattery;
    private VisualizerView mVisualizerView;
    private TextView mArtist;
    private TextView mTitle;
    private ImageView mArtwork;
    private ViewGroup mHeaderGroup;
    private ViewGroup mInfoGroup;
    private ViewGroup mControlsGroup;
    private ImageView mControlNext;
    private ImageView mControlStop;
    private ImageView mControlPrev;

    public VisualizerLayout(Context context, int position) throws Throwable {
        super(context, null, 0);

        mPosition = position;
        mDefaultColor = Color.WHITE;
        mColor = Color.TRANSPARENT;
        mOpacity = 140;
        mBgColor = 0;

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        inflateLayout();
    }

    private void inflateLayout() throws Throwable {
        LayoutInflater inflater = LayoutInflater.from(Utils.getGbContext(getContext(),
                getContext().getResources().getConfiguration()));
        inflater.inflate(R.layout.visualizer, this);
        mVisualizerView = new VisualizerView(getContext());
        int idx = indexOfChild(findViewById(R.id.visualizer));
        removeViewAt(idx);
        addView(mVisualizerView, idx);

        mScrim = findViewById(R.id.scrim);
        mScrim.setBackgroundColor(mBgColor);
        mClock = findViewById(R.id.clock);
        mBattery = findViewById(R.id.battery);
        mArtist = findViewById(R.id.artist);
        mTitle = findViewById(R.id.title);
        mArtwork = findViewById(R.id.artwork);

        mHeaderGroup = findViewById(R.id.header);
        mInfoGroup = findViewById(R.id.info);

        mControlsGroup = findViewById(R.id.media_controls);
        mControlPrev = findViewById(R.id.control_prev);
        mControlPrev.setOnClickListener(this);
        mControlStop = findViewById(R.id.control_stop);
        mControlStop.setOnClickListener(this);
        mControlNext = findViewById(R.id.control_next);
        mControlNext.setOnClickListener(this);
    }

    private void userActivity() {
        try {
            XposedHelpers.callMethod(mPowerManager, "userActivity",
                    SystemClock.uptimeMillis(), false);
            if (DEBUG) log("Virtual userActivity sent");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private final Runnable mUserActivityRunnable = new Runnable() {
        @Override
        public void run() {
            userActivity();
            if (mActiveMode && mDisplaying) {
                postDelayed(this, 4000);
            }
        }
    };

    private final Runnable mEnterDimStateRunnable = new Runnable() {
        @Override
        public void run() {
            setDimState(true);
        }
    };

    private final Runnable mExitDimStateRunnable = new Runnable() {
        @Override
        public void run() {
            setDimState(false);
        }
    };

    private void updateViewVisibility() {
        final int curVis = getVisibility();
        final int newVis = mStatusBarState != StatusBarState.SHADE ?
                 View.VISIBLE : View.GONE;
        if (curVis != newVis) {
            setVisibility(newVis);
            checkStateChanged();
        }
    }

    private void setDimState(final boolean dim) {
        mIsDimmed = dim;

        if (mBgColorAnimator != null) {
            mBgColorAnimator.cancel();
            mBgColorAnimator = null;
        }

        if (isAttachedToWindow()) {
            ViewGroup parent = (ViewGroup) getParent();
            int targetPos = dim ? parent.getChildCount()-1 : mPosition;
            if (targetPos != parent.indexOfChild(this)) {
                parent.removeView(this);
                parent.addView(this, targetPos);
            }
        }

        if (dim) {
            if (mDimInfoEnabled) {
                mArtist.setVisibility(View.VISIBLE);
                mTitle.setVisibility(View.VISIBLE);
            }
            if (mDimControlsEnabled) {
                mControlsGroup.setVisibility(View.VISIBLE);
            }
            if (mDimArtworkEnabled) {
                mArtwork.setVisibility(View.VISIBLE);
                mArtwork.animate().setDuration(1200).setStartDelay(600).alpha(1f);
            }

            if (mDimHeaderEnabled) {
                mHeaderGroup.setVisibility(View.VISIBLE);
                mHeaderGroup.animate().setDuration(1200).setStartDelay(600).alpha(1f);
            }
            if (mDimInfoEnabled || mDimControlsEnabled) {
                mInfoGroup.setVisibility(View.VISIBLE);
                mInfoGroup.animate().setDuration(1200).setStartDelay(600).alpha(1f);
            }

            mBgColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mBgColor, Color.argb(mDimLevel, 0, 0, 0));
            mBgColorAnimator.setDuration(1200);
            mBgColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    mBgColor = (int) va.getAnimatedValue();
                    mScrim.setBackgroundColor(mBgColor);
                }
            });
            mBgColorAnimator.start();
        } else {
            mBgColor = 0;
            mScrim.setBackgroundColor(mBgColor);
            mArtist.setVisibility(View.GONE);
            mTitle.setVisibility(View.GONE);
            mArtwork.setVisibility(View.GONE);
            mControlsGroup.setVisibility(View.GONE);
            mHeaderGroup.setVisibility(View.GONE);
            mInfoGroup.setVisibility(View.GONE);
            mArtwork.setAlpha(0f);
            mHeaderGroup.setAlpha(0f);
            mInfoGroup.setAlpha(0f);
        }
    }

    void onUserActivity() {
        if (DEBUG) log("onUserActivity");
        removeCallbacks(mEnterDimStateRunnable);
        if (mIsDimmed) {
            post(mExitDimStateRunnable);
        }
        if (mDisplaying && mActiveMode && mDimEnabled) {
            postDelayed(mEnterDimStateRunnable, DIM_STATE_DELAY);
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

    void setBatteryLevel(int level) {
        mBattery.setText(String.format("%d%%", level));
    }

    void setStatusBarState(int statusBarState) {
        if (mStatusBarState != statusBarState) {
            mStatusBarState = statusBarState;
            updateViewVisibility();
        }
    }

    void setBitmap(Bitmap bitmap, boolean extractColor) {
        mArtwork.setImageBitmap(bitmap);
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

        setColor(color);
    }

    void setDefaultColor(int color) {
        mDefaultColor = color;
        setColor(mDefaultColor);
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
                    mVisualizerView.setColor(c);
                    mClock.setTextColor(c);
                    mBattery.setTextColor(c);
                    mArtist.setTextColor(c);
                    mTitle.setTextColor(c);
                    mControlPrev.setImageTintList(ColorStateList.valueOf(c));
                    mControlStop.setImageTintList(ColorStateList.valueOf(c));
                    mControlNext.setImageTintList(ColorStateList.valueOf(c));
                }
            });
            mColorAnimator.start();
        }
    }

    void setOpacityPercent(int opacityPercent) {
        mOpacity = Math.round(255f * ((float)opacityPercent/100f));
        mVisualizerView.setColor(Color.argb(mOpacity, Color.red(mColor),
                Color.green(mColor), Color.blue(mColor)));
    }

    void setActiveMode(boolean active) {
        mActiveMode = active;
    }

    void setDimEnabled(boolean enabled) {
        mDimEnabled = enabled;
    }

    void setDimLevelPercent(int dimLevelPercent) {
        mDimLevel = Math.round(255f * ((float)dimLevelPercent/100f));
    }

    void setDimInfoEnabled(boolean enabled) {
        mDimInfoEnabled = enabled;
    }

    void setDimHeaderEnabled(boolean enabled) {
        mDimHeaderEnabled = enabled;
    }

    void setDimControlsEnabled(boolean enabled) {
        mDimControlsEnabled = enabled;
    }

    void setDimArtworkEnabled(boolean enabled) {
        mDimArtworkEnabled = enabled;
    }

    void setArtist(String text) {
        mArtist.setText(text);
    }

    void setTitle(String text) {
        mTitle.setText(text);
    }

    private void checkStateChanged() {
        if (getVisibility() == View.VISIBLE &&
                mVisible && mPlaying && !mPowerSaveMode) {
            if (!mDisplaying) {
                mDisplaying = true;
                if (mActiveMode) {
                    postDelayed(mUserActivityRunnable, 4000);
                    if (mDimEnabled) {
                        postDelayed(mEnterDimStateRunnable, DIM_STATE_DELAY);
                    }
                }
                mVisualizerView.setPlaying(true);
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false;
                removeCallbacks(mEnterDimStateRunnable);
                removeCallbacks(mUserActivityRunnable);
                if (mIsDimmed) {
                    post(mExitDimStateRunnable);
                }
                mVisualizerView.setPlaying(false);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mControlPrev) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (v == mControlStop) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PAUSE);
        } else if (v == mControlNext) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    private void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();
        Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent keyEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        mAudioManager.dispatchMediaKeyEvent(keyEvent);

        keyEvent = KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        mAudioManager.dispatchMediaKeyEvent(keyEvent);
    }
}
