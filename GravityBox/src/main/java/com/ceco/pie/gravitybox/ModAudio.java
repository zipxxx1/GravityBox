/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.pie.gravitybox;

import com.ceco.pie.gravitybox.ledcontrol.QuietHours;
import com.ceco.pie.gravitybox.ledcontrol.QuietHoursActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudio {
    private static final String TAG = "GB:ModAudio";
    private static final String CLASS_AUDIO_SERVICE = "com.android.server.audio.AudioService";
    private static final boolean DEBUG = false;

    private static boolean mVolForceMusicControl;
    private static QuietHours mQh;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOL_FORCE_MUSIC_CONTROL_CHANGED)) {
                mVolForceMusicControl = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_VOL_FORCE_MUSIC_CONTROL, false);
                if (DEBUG) log("Force music volume control set to: " + mVolForceMusicControl);
            } else if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQh = new QuietHours(intent.getExtras());
            }
        }
    };

    public static void initAndroid(final XSharedPreferences prefs, final XSharedPreferences qhPrefs, final ClassLoader classLoader) {
        try {
            final Class<?> classAudioService = XposedHelpers.findClass(CLASS_AUDIO_SERVICE, classLoader);

            mQh = new QuietHours(qhPrefs);

            XposedBridge.hookAllConstructors(classAudioService, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        int[] maxStreamVolume = (int[])
                                XposedHelpers.getStaticObjectField(classAudioService, "MAX_STREAM_VOLUME");
                        maxStreamVolume[AudioManager.STREAM_MUSIC] = prefs.getInt(
                                GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS_VALUE, 30);
                        if (DEBUG) log("MAX_STREAM_VOLUME for music stream set to " + 
                                maxStreamVolume[AudioManager.STREAM_MUSIC]);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    if (context != null) {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOL_FORCE_MUSIC_CONTROL_CHANGED);
                        intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("AudioService constructed. Broadcast receiver registered");
                    }
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", 150);
                        if (DEBUG) log("Default mSafeMediaVolumeIndex set to 150");
                    }
                }
            });

            if (Utils.isSamsungRom()) {
                Utils.TriState triState = Utils.TriState.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME, "DEFAULT"));
                if (DEBUG) log(GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME + ": " + triState);
                if (triState == Utils.TriState.DISABLED) {
                    XposedHelpers.findAndHookConstructor("android.media.AudioManager", classLoader, Context.class,
                            new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object objService = XposedHelpers.callMethod(param.thisObject, "getService");
                            Context mApplicationContext = (Context) XposedHelpers.getObjectField(param.thisObject,
                                    "mApplicationContext");
                            if (objService != null && mApplicationContext != null) {
                                XposedHelpers.callMethod(param.thisObject, "disableSafeMediaVolume");
                            }
                        }
                    });
                }
            }
            
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                XposedHelpers.findAndHookMethod(classAudioService, "onConfigureSafeVolume",
                        boolean.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setObjectExtra("gbCurSafeMediaVolIndex",
                                XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex"));
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if ((Integer) param.getObjectExtra("gbCurSafeMediaVolIndex") !=
                                XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex")) {
                            int safeMediaVolIndex = XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex") * 2;
                            XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", safeMediaVolIndex);
                            if (DEBUG) log("onConfigureSafeVolume: mSafeMediaVolumeIndex set to " + safeMediaVolIndex);
                        }
                    }
                });
            }

            mVolForceMusicControl = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_VOL_FORCE_MUSIC_CONTROL, false);
            XposedHelpers.findAndHookMethod(classAudioService, "getActiveStreamType",
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (mVolForceMusicControl) {
                        int activeStreamType = (int) param.getResult();
                        if (activeStreamType == AudioManager.STREAM_RING ||
                                activeStreamType == AudioManager.STREAM_NOTIFICATION) {
                            param.setResult(AudioManager.STREAM_MUSIC);
                            if (DEBUG) log("getActiveStreamType: Forcing STREAM_MUSIC");
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classAudioService, "playSoundEffectVolume",
                    int.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    if (mQh.isSystemSoundMuted(QuietHours.SystemSound.TOUCH)) {
                        param.setResult(false);
                    }
                } 
            });
        } catch(Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
