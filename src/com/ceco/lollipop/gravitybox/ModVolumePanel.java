/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.lollipop.gravitybox;

import com.ceco.lollipop.gravitybox.ledcontrol.QuietHours;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHoursActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModVolumePanel {
    private static final String TAG = "GB:ModVolumePanel";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String CLASS_VOLUME_PANEL = "com.android.systemui.volume.VolumePanel";
    private static final String CLASS_STREAM_CONTROL = "com.android.systemui.volume.VolumePanel$StreamControl";
    private static final boolean DEBUG = false;

    private static final int MSG_TIMEOUT = 5;

    private static Object mVolumePanel;
    private static boolean mVolumeAdjustMuted;
    private static boolean mVolumeAdjustVibrateMuted;
    private static boolean mExpandable;
    private static boolean mAutoExpand;
    private static int mTimeout;
    //private static View mPanel;
    private static XSharedPreferences mQhPrefs;
    private static QuietHours mQuietHours;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBrodcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_EXPANDABLE)) {
                    mExpandable = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_EXPANDABLE, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_AUTOEXPAND)) {
                    mAutoExpand = intent.getBooleanExtra(GravityBoxSettings.EXTRA_AUTOEXPAND, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_MUTED)) {
                    mVolumeAdjustMuted = intent.getBooleanExtra(GravityBoxSettings.EXTRA_MUTED, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED)) {
                    mVolumeAdjustVibrateMuted = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_TIMEOUT)) {
                    mTimeout = intent.getIntExtra(GravityBoxSettings.EXTRA_TIMEOUT, 0);
                }
            } else if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQhPrefs.reload();
                mQuietHours = new QuietHours(mQhPrefs);
            }
        }
        
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePanel = XposedHelpers.findClass(CLASS_VOLUME_PANEL, classLoader);

            mQhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
            mQuietHours = new QuietHours(mQhPrefs);

            mVolumeAdjustMuted = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ADJUST_MUTE, false);
            mVolumeAdjustVibrateMuted = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ADJUST_VIBRATE_MUTE, false);
            mExpandable = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_EXPANDABLE, false);
            mAutoExpand = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_AUTOEXPAND, false);

            XposedBridge.hookAllConstructors(classVolumePanel, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mVolumePanel = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mVolumePanel, "mContext");
                    if (DEBUG) log("VolumePanel constructed; mVolumePanel set");

                    mTimeout = 0;
                    try {
                        mTimeout = Integer.valueOf(prefs.getString(
                                GravityBoxSettings.PREF_KEY_VOLUME_PANEL_TIMEOUT, "0"));
                    } catch (NumberFormatException nfe) {
                        log("Invalid value for PREF_KEY_VOLUME_PANEL_TIMEOUT preference");
                    }

//                    Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(classVolumePanel, "STREAMS");
//                    XposedHelpers.setBooleanField(streams[1], "show", 
//                            (Boolean) XposedHelpers.getBooleanField(param.thisObject, "mVoiceCapable"));
//                    XposedHelpers.setBooleanField(streams[5], "show", true);

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED);
                    intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                    context.registerReceiver(mBrodcastReceiver, intentFilter);

                    //mPanel = (View) XposedHelpers.getObjectField(param.thisObject, "mPanel");
                }
            });

//            XposedHelpers.findAndHookMethod(classVolumePanel, "createSliders", new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
//                    final boolean voiceCapableOrig = XposedHelpers.getBooleanField(param.thisObject, "mVoiceCapable");
//                    if (DEBUG) log("createSliders: original mVoiceCapable = " + voiceCapableOrig);
//                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "mGbVoiceCapableOrig", voiceCapableOrig);
//                    XposedHelpers.setBooleanField(param.thisObject, "mVoiceCapable", false);
//                }
//                @Override
//                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
//                    final Boolean voiceCapableOrig =  (Boolean)XposedHelpers.getAdditionalInstanceField(
//                            param.thisObject, "mGbVoiceCapableOrig");
//                    if (voiceCapableOrig != null) {
//                        if (DEBUG) log("createSliders: restoring original mVoiceCapable");
//                        XposedHelpers.setBooleanField(param.thisObject, "mVoiceCapable", voiceCapableOrig);
//                    }
//                }
//            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "onPlaySound",
                    int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mVolumeAdjustMuted || 
                            mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.VOLUME_ADJUST)) {
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "resetTimeout", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mTimeout != 0) {
                        Handler h = (Handler) param.thisObject;
                        h.removeMessages(MSG_TIMEOUT);
                        h.sendMessageDelayed(h.obtainMessage(MSG_TIMEOUT), mTimeout);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "onVibrate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mVolumeAdjustVibrateMuted) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

//    private static void hideNotificationSliderIfLinked() {
//        if (mVolumePanel != null &&
//                mVolumesLinked && 
//                XposedHelpers.getBooleanField(mVolumePanel, "mVoiceCapable")) {
//            @SuppressWarnings("unchecked")
//            Map<Integer, Object> streamControls = 
//                    (Map<Integer, Object>) XposedHelpers.getObjectField(mVolumePanel, "mStreamControls");
//            if (streamControls == null) return;
//    
//            for (Object o : streamControls.values()) {
//                if ((Integer) XposedHelpers.getIntField(o, "streamType") == STREAM_NOTIFICATION) {
//                    View v = (View) XposedHelpers.getObjectField(o, "group");
//                    if (v != null) {
//                        v.setVisibility(View.GONE);
//                        if (DEBUG) log("Notification volume slider hidden");
//                        break;
//                    }
//                }
//            }
//        }
//    }
}