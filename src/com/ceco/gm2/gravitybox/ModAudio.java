/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.gm2.gravitybox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XResources;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudio {
    private static final String TAG = "GB:ModAudio";
    private static final String CLASS_AUDIO_SERVICE = "android.media.AudioService";
    private static final int STREAM_MUSIC = 3;
    private static final int VOLUME_STEPS = 30;
    private static final boolean DEBUG = false;

    private static boolean mSafeMediaVolumeEnabled;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_SAFE_MEDIA_VOLUME_CHANGED)) {
                mSafeMediaVolumeEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_SAFE_MEDIA_VOLUME_ENABLED, false);
                if (DEBUG) log("Safe headset media volume set to: " + mSafeMediaVolumeEnabled);
            }
        }
    };

    public static void initZygote(final XSharedPreferences prefs) {
        try {
            final Class<?> classAudioService = XposedHelpers.findClass(CLASS_AUDIO_SERVICE, null);

            XposedBridge.hookAllConstructors(classAudioService, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        int[] maxStreamVolume = (int[])
                                XposedHelpers.getStaticObjectField(classAudioService, "MAX_STREAM_VOLUME");
                        maxStreamVolume[STREAM_MUSIC] = VOLUME_STEPS;
                        if (DEBUG) log("MAX_STREAM_VOLUME for music stream set to " + VOLUME_STEPS);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {    
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    if (context != null) {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_SAFE_MEDIA_VOLUME_CHANGED);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("AudioService constructed. Broadcast receiver registered");
                    }
                }
            });

            XResources.setSystemWideReplacement("android", "bool", "config_safe_media_volume_enabled", true);
            mSafeMediaVolumeEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME, false);
            if (DEBUG) log("Safe headset media volume set to: " + mSafeMediaVolumeEnabled);
            XposedHelpers.findAndHookMethod(classAudioService, "enforceSafeMediaVolume", new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mSafeMediaVolumeEnabled) {
                        param.setResult(null);
                        return;
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classAudioService, "checkSafeMediaVolume", 
                    int.class, int.class, int.class, new XC_MethodHook() {
    
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mSafeMediaVolumeEnabled) {
                        param.setResult(true);
                        return;
                    }
                }
            });
        } catch(Throwable t) {
            XposedBridge.log(t);
        }
    }
}
