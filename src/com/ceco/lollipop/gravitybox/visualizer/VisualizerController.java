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
package com.ceco.lollipop.gravitybox.visualizer;

import com.ceco.lollipop.gravitybox.BroadcastSubReceiver;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ModStatusBar;
import com.ceco.lollipop.gravitybox.Utils;
import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarStateChangedListener;
import com.ceco.lollipop.gravitybox.managers.BatteryInfoManager;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;
import com.ceco.lollipop.gravitybox.managers.BatteryInfoManager.BatteryData;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class VisualizerController implements StatusBarStateChangedListener,
                                             BatteryInfoManager.BatteryStatusListener,
                                             BroadcastSubReceiver {
    private static final String TAG = "GB:VisualizerController";
    private static final boolean DEBUG = true;

    private static final String CLASS_STATUSBAR_WINDOW_VIEW = "com.android.systemui.statusbar.phone.StatusBarWindowView";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private VisualizerView mView;
    private int mCurrentDrawableHash = -1;
    private boolean mDynamicColorEnabled;
    private int mColor;
    private int mOpacityPercent;

    public VisualizerController(ClassLoader cl, XSharedPreferences prefs) {
        mDynamicColorEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DYNAMIC_COLOR, true);
        mColor = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_COLOR, Color.WHITE);
        mOpacityPercent = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_OPACITY, 50);

        createHooks(cl);
    }

    private void createHooks(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(CLASS_STATUSBAR_WINDOW_VIEW, cl,
                    "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    createView((ViewGroup) param.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(ModStatusBar.CLASS_PHONE_STATUSBAR, cl,
                    "updateMediaMetaData", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mView != null) {
                        updateMediaMetaData(param.thisObject,
                                (boolean) param.args[0]);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void createView(ViewGroup parent) {
        mView = new VisualizerView(parent.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM);
        mView.setLayoutParams(lp);

        // find suitable position, put as last if failed
        int pos = parent.getChildCount();
        int resId = parent.getResources().getIdentifier("status_bar", "id",
                parent.getContext().getPackageName());
        if (resId != 0) {
            View v = parent.findViewById(resId);
            if (v != null) {
                pos = parent.indexOfChild(v);
            }
        }
        if (DEBUG) log("Computed view position: " + pos);
        parent.addView(mView, pos);

        mView.setOpacityPercent(mOpacityPercent);
        mView.setDefaultColor(mColor);

        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.registerListener(this);
            if (SysUiManagers.BatteryInfoManager.getCurrentBatteryData() != null) {
                mView.setPowerSaveMode(SysUiManagers.BatteryInfoManager
                        .getCurrentBatteryData().isPowerSaving);
            }
        }

        if (DEBUG) log("VisualizerView created");
    }

    private void updateMediaMetaData(Object sb, boolean metaDataChanged) {
        MediaController mc = (MediaController) XposedHelpers
                .getObjectField(sb, "mMediaController");
        boolean playing = mc != null && mc.getPlaybackState() != null &&
                mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        mView.setPlaying(playing);

        if (mDynamicColorEnabled) {
            ImageView backDrop = (ImageView) XposedHelpers.getObjectField(sb, "mBackdropBack");
            if (backDrop != null) {
                Bitmap bitmap = null;
                Drawable d = backDrop.getDrawable();
                int hash = (d == null ? 0 : d.hashCode());
                if (hash != mCurrentDrawableHash) {
                    if (d instanceof BitmapDrawable) {
                        bitmap = ((BitmapDrawable)d).getBitmap();
                    } else if (d != null) {
                        bitmap = Utils.drawableToBitmap(d);
                    }
                    mCurrentDrawableHash = hash;
                    mView.setBitmap(bitmap);
                    if (DEBUG) log("updateMediaMetaData: artwork change detected");
                }
            }
        } else {
            mView.setBitmap(null);
        }
    }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mView != null) {
            mView.setStatusBarState(newState);
        }
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        if (mView != null) {
            mView.setPowerSaveMode(batteryData.isPowerSaving);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            if (mView != null) {
                mView.setVisible(true);
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (mView != null) {
                mView.setVisible(false);
            }
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_VISUALIZER_SETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DYNAMIC_COLOR)) {
                mDynamicColorEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_DYNAMIC_COLOR, true);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_COLOR)) {
                mColor = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_COLOR, Color.WHITE);
                if (mView != null) mView.setDefaultColor(mColor);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_OPACITY)) {
                mOpacityPercent = intent.getIntExtra(GravityBoxSettings.EXTRA_VISUALIZER_OPACITY, 50);
                if (mView != null) mView.setOpacityPercent(mOpacityPercent);
            }
        }
    }
}
