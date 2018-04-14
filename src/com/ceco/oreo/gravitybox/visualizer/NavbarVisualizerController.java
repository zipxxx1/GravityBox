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
import com.ceco.oreo.gravitybox.ModStatusBar;
import com.ceco.oreo.gravitybox.ModStatusBar.StatusBarStateChangedListener;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.managers.BatteryInfoManager;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class NavbarVisualizerController implements StatusBarStateChangedListener,
                                             BatteryInfoManager.BatteryStatusListener,
                                             BroadcastSubReceiver {
    private static final String TAG = "GB:NavbarVisualizerController";
    private static final boolean DEBUG = false;

    private static final String CLASS_NAVIGATION_BAR_VIEW = "com.android.systemui.statusbar.phone.NavigationBarView";
    private static final String CLASS_NAVIGATION_BAR_INFLATER_VIEW = "com.android.systemui.statusbar.phone.NavigationBarInflaterView";
    private static final String CLASS_LIGHT_BAR_CONTROLLER = "com.android.systemui.statusbar.phone.LightBarController";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private NavbarVisualizerLayout mNavbarView;
    private boolean mDynamicColorEnabled;
    private int mColor;
    private int mOpacityPercent;

    public NavbarVisualizerController(ClassLoader cl, XSharedPreferences prefs) {
        mDynamicColorEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_DYNAMIC_COLOR, true);
        mColor = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_COLOR, Color.WHITE);
        mOpacityPercent = prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_OPACITY, 50);

        createHooks(cl);
    }

    private void createHooks(ClassLoader cl) {

        try {
            XposedHelpers.findAndHookMethod(CLASS_NAVIGATION_BAR_VIEW, cl,
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
            XposedHelpers.findAndHookMethod(CLASS_NAVIGATION_BAR_INFLATER_VIEW, cl,
                    "setAlternativeOrder", boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            boolean isRight = (boolean) param.args[0];
                            if (mNavbarView != null) {
                                mNavbarView.setLeftInLandscape(!isRight);
                            }
                        }
                    });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_LIGHT_BAR_CONTROLLER, cl,
                    "updateNavigation", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Boolean isLight = (Boolean) XposedHelpers
                                    .getBooleanField(param.thisObject, "mNavigationLight");
                            if (mNavbarView != null) {
                                mNavbarView.setLightNavbar(isLight);
                            }
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
                    if (mNavbarView != null) {
                        updateMediaMetaData(param.thisObject,
                                (boolean) param.args[0]);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createView(ViewGroup parent) throws Throwable {
        // find suitable position, put as last if failed
        int pos = parent.getChildCount();
        int resId = parent.getResources().getIdentifier("navigation_bar", "id",
                parent.getContext().getPackageName());
        if (resId != 0) {
            View v = parent.findViewById(resId);
            if (v != null) {
                pos = parent.indexOfChild(v);
            }
        }
        if (DEBUG) log("Computed view position: " + pos);

        mNavbarView = new NavbarVisualizerLayout(parent.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mNavbarView.setLayoutParams(lp);
        parent.addView(mNavbarView, pos);

        mNavbarView.setOpacityPercent(mOpacityPercent);
        mNavbarView.setDefaultColor(mColor);

        if (DEBUG) log("NavbarVisualizerView created");
    }

    private void updateMediaMetaData(Object sb, boolean metaDataChanged) {
        MediaController mc = (MediaController) XposedHelpers
                .getObjectField(sb, "mMediaController");
        boolean playing = mc != null && mc.getPlaybackState() != null &&
                mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        mNavbarView.setPlaying(playing);

        if (playing && metaDataChanged) {
            MediaMetadata md = mc.getMetadata();
            Bitmap artworkBitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (artworkBitmap == null) {
                artworkBitmap = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            }
            mNavbarView.setBitmap(artworkBitmap, mDynamicColorEnabled);
            if (DEBUG) log("updateMediaMetaData: artwork change detected; bitmap=" + artworkBitmap);
        }
    }

    @Override
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mNavbarView != null) {
            mNavbarView.setStatusBarState(newState);
        }
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        if (mNavbarView != null) {
            mNavbarView.setPowerSaveMode(batteryData.isPowerSaving);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            if (mNavbarView != null) {
                mNavbarView.setVisible(true);
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            if (mNavbarView != null) {
                mNavbarView.setVisible(false);
            }
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_VISUALIZER_SETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_DYNAMIC_COLOR)) {
                mDynamicColorEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_DYNAMIC_COLOR, true);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_COLOR)) {
                mColor = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_VISUALIZER_COLOR, Color.WHITE);
                if (mNavbarView != null) mNavbarView.setDefaultColor(mColor);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_OPACITY)) {
                mOpacityPercent = intent.getIntExtra(GravityBoxSettings.EXTRA_VISUALIZER_OPACITY, 50);
                if (mNavbarView != null) mNavbarView.setOpacityPercent(mOpacityPercent);
            }
        }
    }
}
