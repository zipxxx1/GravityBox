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

import com.ceco.oreo.gravitybox.BroadcastSubReceiver;
import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.GravityBoxSettings;
import com.ceco.oreo.gravitybox.ModLockscreen;
import com.ceco.oreo.gravitybox.ModStatusBar;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarStateChangedListener;
import com.ceco.oreo.gravitybox.managers.BatteryInfoManager;
import com.ceco.oreo.gravitybox.managers.SysUiManagers;
import com.ceco.oreo.gravitybox.managers.BatteryInfoManager.BatteryData;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
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
    private static final boolean DEBUG = false;

    private static final String CLASS_STATUSBAR_WINDOW_VIEW = "com.android.systemui.statusbar.phone.StatusBarWindowView";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private VisualizerView mView;
    private int mCurrentDrawableHash = -1;
    private boolean mDynamicColorEnabled;
    private int mColor;
    private int mOpacityPercent;
    private boolean mActiveMode;
    private boolean mDimEnabled;
    private int mDimLevelPercent;
    private boolean mDimInfoEnabled;

    public VisualizerController(ClassLoader cl, XSharedPreferences prefs) {
        mDynamicColorEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DYNAMIC_COLOR, true);
        mColor = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_COLOR, Color.WHITE);
        mOpacityPercent = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_OPACITY, 50);
        mActiveMode = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_ACTIVE_MODE, false);
        mDimEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM, true);
        mDimLevelPercent = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_LEVEL, 80);
        mDimInfoEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DIM_INFO, true);

        createHooks(cl);
    }

    private void createHooks(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(CLASS_STATUSBAR_WINDOW_VIEW, cl,
                    "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    createView((ViewGroup) param.thisObject);
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(ModStatusBar.CLASS_STATUSBAR, cl,
                    "updateMediaMetaData", boolean.class, boolean.class,
                        new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mView != null) {
                        updateMediaMetaData(param.thisObject,
                                (boolean) param.args[0]);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(ModLockscreen.CLASS_KGVIEW_MEDIATOR, cl,
                    "userActivity", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onUserActivity();
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createView(ViewGroup parent) {
        // find suitable position, put as last if failed
        int pos = parent.getChildCount();
        int resId = parent.getResources().getIdentifier("status_bar_container", "id",
                parent.getContext().getPackageName());
        if (resId != 0) {
            View v = parent.findViewById(resId);
            if (v != null) {
                pos = parent.indexOfChild(v);
            }
        }
        if (DEBUG) log("Computed view position: " + pos);

        mView = new VisualizerView(parent.getContext(), pos);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM);
        mView.setLayoutParams(lp);
        parent.addView(mView, pos);

        mView.setOpacityPercent(mOpacityPercent);
        mView.setDefaultColor(mColor);
        mView.setActiveMode(mActiveMode);
        mView.setDimEnabled(mDimEnabled);
        mView.setDimLevelPercent(mDimLevelPercent);
        mView.setDimInfoEnabled(mDimInfoEnabled);

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

        if (playing) {
            mView.setText(getTextFromMetaData(mc.getMetadata()));
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
                mCurrentDrawableHash = 0;
            }
        } else {
            mView.setText(null);
            mView.setBitmap(null);
            mCurrentDrawableHash = 0;
        }
    }

    private String getTextFromMetaData(MediaMetadata data) {
        if (data == null)
            return null;

        String out = "";
        if (data.containsKey(MediaMetadata.METADATA_KEY_ARTIST)) {
            out += data.getString(MediaMetadata.METADATA_KEY_ARTIST);
        }
        if (data.containsKey(MediaMetadata.METADATA_KEY_TITLE)) {
            if (!out.isEmpty()) out += " - ";
            out += data.getString(MediaMetadata.METADATA_KEY_TITLE);
        }
        return out;
    }

    private void onUserActivity() {
        if (mView != null) {
            mView.onUserActivity();
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
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_ACTIVE_MODE)) {
                mActiveMode = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_ACTIVE_MODE, false);
                if (mView != null) mView.setActiveMode(mActiveMode);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM)) {
                mDimEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM, true);
                if (mView != null) mView.setDimEnabled(mDimEnabled);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_LEVEL)) {
                mDimLevelPercent = intent.getIntExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_LEVEL, 80);
                if (mView != null) mView.setDimLevelPercent(mDimLevelPercent);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_INFO)) {
                mDimInfoEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_DIM_INFO, true);
                if (mView != null) mView.setDimInfoEnabled(mDimInfoEnabled);
            }
        }
    }
}
