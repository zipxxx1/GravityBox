/*
* Copyright (C) 2015 The CyanogenMod Project
* Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.ceco.oreo.gravitybox.visualizer;

import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarState;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v7.graphics.Palette;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class VisualizerView extends View
        implements Palette.PaletteAsyncListener {

    private static final String TAG = "GB:VisualizerView";
    private static final boolean DEBUG = false;

    private static final long DIM_STATE_DELAY = 10000l;
    private static final float TEXT_SIZE_SP = 16f;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Paint mPaint;
    private TextPaint mTextPaint;
    private Visualizer mVisualizer;
    private ObjectAnimator mVisualizerColorAnimator;

    private ValueAnimator[] mValueAnimators;
    private float[] mFFTPoints;

    private int mStatusBarState;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mPowerSaveMode = false;
    private boolean mDisplaying = false; // the state we're animating to

    private int mDefaultColor;
    private int mColor;
    private int mBgColor;
    private ValueAnimator mBgColorAnimator;
    private int mOpacity;
    private boolean mActiveMode;
    private PowerManager mPowerManager;
    private long mLastUserActivityStamp;
    private int mPosition;
    private boolean mIsDimmed;
    private boolean mDimEnabled;
    private int mDimLevel;
    private boolean mDimInfoEnabled;
    private String mText;
    private StaticLayout mTextLayout;

    private Visualizer.OnDataCaptureListener mVisualizerListener =
            new Visualizer.OnDataCaptureListener() {
        byte rfk, ifk;
        int dbValue;
        float magnitude;

        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            for (int i = 0; i < 32; i++) {
                mValueAnimators[i].cancel();
                rfk = fft[i * 2 + 2];
                ifk = fft[i * 2 + 3];
                magnitude = rfk * rfk + ifk * ifk;
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                        mFFTPoints[3] - (dbValue * 16f));
                mValueAnimators[i].start();
            }

            if (mActiveMode && (SystemClock.elapsedRealtime() - mLastUserActivityStamp) > 4000) {
                mLastUserActivityStamp = SystemClock.elapsedRealtime();
                post(new Runnable() {
                    @Override
                    public void run() {
                        userActivity();
                    }
                });
            }
        }
    };

    private void userActivity() {
        try {
            XposedHelpers.callMethod(mPowerManager, "userActivity",
                    SystemClock.uptimeMillis(), false);
            if (DEBUG) log("Virtual userActivity sent");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                log("+++ mLinkVisualizer run()");
            }

            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                GravityBox.log(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(mVisualizerListener,Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);

            if (mActiveMode && mDimEnabled) {
                postDelayed(mEnterDimStateRunnable, DIM_STATE_DELAY);
            }

            if (DEBUG) {
                log("--- mLinkVisualizer run()");
            }
        }
    };

    private final Runnable mAsyncUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            AsyncTask.execute(mUnlinkVisualizer);
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                log("+++ mUnlinkVisualizer run(), mVisualizer: " + mVisualizer);
            }

            removeCallbacks(mEnterDimStateRunnable);
            if (mIsDimmed) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        setDimState(false);
                    }
                });
            }

            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }
            if (DEBUG) {
                log("--- mUninkVisualizer run()");
            }
        }
    };

    private final Runnable mEnterDimStateRunnable = new Runnable() {
        @Override
        public void run() {
            setDimState(true);
        }
    };

    VisualizerView(Context context, int position) {
        super(context, null, 0);

        mPosition = position;
        mDefaultColor = Color.WHITE;
        mColor = Color.TRANSPARENT;
        mOpacity = 140;
        mBgColor = 0;
        setBackgroundColor(mBgColor);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mDefaultColor);

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(mDefaultColor);
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                TEXT_SIZE_SP, context.getResources().getDisplayMetrics()));
        mTextPaint.setShadowLayer(2f, 2f, 2f, Color.WHITE);

        mFFTPoints = new float[128];
        mValueAnimators = new ValueAnimator[32];
        for (int i = 0; i < 32; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                    postInvalidate();
                }
            });
        }

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

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
            if (targetPos != parent.indexOfChild(VisualizerView.this)) {
                parent.removeView(VisualizerView.this);
                parent.addView(VisualizerView.this, targetPos);
            }
        }

        if (dim) {
            mBgColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mBgColor, Color.argb(mDimLevel, 0, 0, 0));
            mBgColorAnimator.setDuration(1000);
            mBgColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    mBgColor = (int) va.getAnimatedValue();
                    setBackgroundColor(mBgColor);
                }
            });
            mBgColorAnimator.start();
        } else {
            mBgColor = 0;
            setBackgroundColor(mBgColor);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float barUnit = w / 32f;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * 32f / 31f;
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = h;
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mVisualizer != null) {
            canvas.drawLines(mFFTPoints, mPaint);

            if (mIsDimmed && mDimInfoEnabled && mText != null) {
                int textWidth = (int) (canvas.getWidth() - mTextPaint.getTextSize());
                if (mTextLayout == null || !mText.equals(mTextLayout.getText())) {
                    mTextLayout = new StaticLayout(mText, mTextPaint,
                            textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    if (DEBUG) log("Text layout created with new text: " + mText);
                }
                int textHeight = mTextLayout.getHeight();
                float x = (canvas.getWidth() - textWidth)/2;
                float y = (canvas.getHeight() - textHeight)/2;
                canvas.save();
                canvas.translate(x, y);
                mTextLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    void onUserActivity() {
        if (DEBUG) log("onUserActivity");
        removeCallbacks(mEnterDimStateRunnable);
        if (mIsDimmed) {
            post(new Runnable() {
                @Override
                public void run() {
                    setDimState(false);
                }
            });
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

    void setStatusBarState(int statusBarState) {
        if (mStatusBarState != statusBarState) {
            mStatusBarState = statusBarState;
            updateViewVisibility();
        }
    }

    void setBitmap(Bitmap bitmap) {
        if (bitmap != null) {
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

        color = Color.argb(mOpacity, Color.red(color), Color.green(color), Color.blue(color));

        if (mColor != color) {
            mColor = color;

            mTextPaint.setColor(mColor);
            if (mVisualizer != null) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(600);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mPaint.setColor(mColor);
            }
        }
    }

    void setOpacityPercent(int opacityPercent) {
        mOpacity = Math.round(255f * ((float)opacityPercent/100f));
        setColor(mColor);
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

    void setText(String text) {
        mText = text;
    }

    private void checkStateChanged() {
        if (getVisibility() == View.VISIBLE &&
                mVisible && mPlaying && !mPowerSaveMode) {
            if (!mDisplaying) {
                mDisplaying = true;
                AsyncTask.execute(mLinkVisualizer);
                animate()
                        .alpha(1f)
                        .withEndAction(null)
                        .setDuration(800);
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false;
                if (mVisible) {
                    animate()
                            .alpha(0f)
                            .withEndAction(mAsyncUnlinkVisualizer)
                            .setDuration(600);
                } else {
                    animate().
                            alpha(0f)
                            .withEndAction(mAsyncUnlinkVisualizer)
                            .setDuration(0);
                }
            }
        }
    }
}
