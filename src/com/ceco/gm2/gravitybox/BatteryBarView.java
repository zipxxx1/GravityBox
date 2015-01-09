/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.gm2.gravitybox;

import com.ceco.gm2.gravitybox.StatusbarDownloadProgressView.Mode;
import com.ceco.gm2.gravitybox.managers.BatteryInfoManager.BatteryData;
import com.ceco.gm2.gravitybox.managers.BatteryInfoManager.BatteryStatusListener;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.gm2.gravitybox.managers.SysUiManagers;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;

public class BatteryBarView extends View implements IconManagerListener, 
                                                    BroadcastSubReceiver,
                                                    BatteryStatusListener,
                                                    StatusbarDownloadProgressView.ProgressStateListener {
    private static final String TAG = "GB:BatteryBarView";
    private static final boolean DEBUG = false;

    private static final int ANIM_DURATION = 1500;

    private enum Position { TOP, BOTTOM };

    private boolean mEnabled;
    private Position mPosition;
    private int mMarginPx;
    private int mHeightPx;
    private boolean mAnimateCharge;
    private boolean mDynaColor;
    private int mColor;
    private int mColorLow;
    private int mColorCritical;
    private BatteryData mBatteryData;
    private boolean mIsAnimating;
    private boolean mHiddenByProgressBar;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public BatteryBarView(Context context, XSharedPreferences prefs) {
        super(context);

        mEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_SHOW, false);
        mPosition = Position.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_BAR_POSITION, "TOP"));
        mMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_MARGIN, 0),
                getResources().getDisplayMetrics());
        mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_THICKNESS, 2),
                getResources().getDisplayMetrics());
        mAnimateCharge = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_CHARGE_ANIM, false);
        mDynaColor = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_BAR_DYNACOLOR, true);
        mColor = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR, 0xff0099cc);
        mColorLow = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR_LOW, 0xffffa500);
        mColorCritical = prefs.getInt(GravityBoxSettings.PREF_KEY_BATTERY_BAR_COLOR_CRITICAL, Color.RED);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, mHeightPx);
        setLayoutParams(lp);
        setPivotX(0f);
        setVisibility(View.GONE);
        updatePosition();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mEnabled) {
            setListeners();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unsetListeners();
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        mBatteryData = batteryData;
        if (DEBUG) log("onBatteryStatusChanged: level=" + mBatteryData.level +
                "; charging=" + mBatteryData.charging);
        update();
    }

    private void setListeners() {
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.registerListener(this);
        }
    }

    private void unsetListeners() {
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.unregisterListener(this);
        }
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
    }

    private void update() {
        if (mEnabled && mBatteryData != null && !mHiddenByProgressBar) {
            setVisibility(View.VISIBLE);
            if (mDynaColor) {
                int cappedLevel = Math.min(Math.max(mBatteryData.level, 15), 90);
                float hue = (cappedLevel - 15) * 1.6f;
                setBackgroundColor(Color.HSVToColor(0xff, new float[]{ hue, 1.f, 1.f }));
            } else {
                int color = mColor;
                if (mBatteryData.level <= 5) {
                    color = mColorCritical;
                } else if (mBatteryData.level <= 15) {
                    color = mColorLow;
                }
                setBackgroundColor(color);
            }
            if (mAnimateCharge && mBatteryData.charging && mBatteryData.level < 100) {
                setScaleX(1f);
                startAnimation();
            } else {
                stopAnimation();
                setScaleX(mBatteryData.level/100f);
            }
        } else {
            stopAnimation();
            setVisibility(View.GONE);
        }
    }

    private void startAnimation() {
        if (mIsAnimating) {
            stopAnimation();
        }
        ScaleAnimation a = new ScaleAnimation(mBatteryData.level/100f, 1f, 1f, 1f);
        a.setInterpolator(new AccelerateInterpolator());
        a.setDuration(ANIM_DURATION);
        a.setRepeatCount(Animation.INFINITE);
        startAnimation(a);
        mIsAnimating = true;
    }

    private void stopAnimation() {
        clearAnimation();
        mIsAnimating = false;
    }

    private void updatePosition() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.height = mHeightPx;
        lp.gravity = mPosition == Position.TOP ? (Gravity.TOP | Gravity.START) :
            (Gravity.BOTTOM | Gravity.START);
        lp.setMargins(0, mPosition == Position.TOP ? mMarginPx : 0,
                        0, mPosition == Position.BOTTOM ? mMarginPx : 0);
        setLayoutParams(lp);
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_LOW_PROFILE_CHANGED) != 0) {
            setAlpha(colorInfo.lowProfile ? 127 : 255);
        }
    }

    @Override
    public void onProgressTrackingStarted(boolean isBluetooth, Mode mode) {
        if ((mode == Mode.TOP && mPosition == Position.TOP) ||
                (mode == Mode.BOTTOM && mPosition == Position.BOTTOM)) {
            mHiddenByProgressBar = true;
            update();
        }
    }

    @Override
    public void onProgressTrackingStopped() {
        mHiddenByProgressBar = false;
        update();
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_BAR_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_SHOW)) {
                mEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_SHOW, false);
                if (mEnabled) {
                    setListeners();
                } else {
                    unsetListeners();
                }
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_POSITION)) {
                mPosition = Position.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_BBAR_POSITION));
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_MARGIN)) {
                mMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_MARGIN, 0),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_THICKNESS)) {
                mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_THICKNESS, 2),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_DYNACOLOR)) {
                mDynaColor = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_DYNACOLOR, true);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR)) {
                mColor = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR, 0xff0099cc);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_LOW)) {
                mColorLow = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_LOW, 0xffffa500);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_CRITICAL)) {
                mColorCritical = intent.getIntExtra(GravityBoxSettings.EXTRA_BBAR_COLOR_CRITICAL, Color.RED);
                update();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BBAR_CHARGE_ANIM)) {
                mAnimateCharge = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BBAR_CHARGE_ANIM, false);
                update();
            }
        }
    }
}
