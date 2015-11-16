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

package com.ceco.marshmallow.gravitybox;

import com.ceco.marshmallow.gravitybox.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.media.AudioManager;
import android.os.Handler;
import android.util.SparseArray;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModVolumePanel {
    private static final String TAG = "GB:ModVolumePanel";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String CLASS_VOLUME_PANEL = "com.android.systemui.volume.VolumePanel";
    private static final boolean DEBUG = false;

    private static final int MSG_TIMEOUT = 5;

    private static Object mVolumePanel;
    private static boolean mVolumeAdjustVibrateMuted;
    private static boolean mAutoExpand;
    private static int mTimeout;
    private static boolean mVolumesLinked;
    private static Context mGbContext;
    private static int mIconRingerAudibleId;
    private static int mIconRingerAudibleIdOrig;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBrodcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_AUTOEXPAND)) {
                    mAutoExpand = intent.getBooleanExtra(GravityBoxSettings.EXTRA_AUTOEXPAND, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED)) {
                    mVolumeAdjustVibrateMuted = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_TIMEOUT)) {
                    mTimeout = intent.getIntExtra(GravityBoxSettings.EXTRA_TIMEOUT, 0);
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED)) {
                mVolumesLinked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_LINKED, true);
                updateRingerIcon();
            }
        }
        
    };

    public static void initResources(XSharedPreferences prefs, InitPackageResourcesParam resparam) {
        XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);

        mIconRingerAudibleId = 0;
        mIconRingerAudibleId = XResources.getFakeResId(modRes, R.drawable.ic_ringer_audible);
        resparam.res.setReplacement(mIconRingerAudibleId, modRes.fwd(R.drawable.ic_ringer_audible));
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePanel = XposedHelpers.findClass(CLASS_VOLUME_PANEL, classLoader);

            mVolumeAdjustVibrateMuted = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ADJUST_VIBRATE_MUTE, false);
            mAutoExpand = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_AUTOEXPAND, false);
            mVolumesLinked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true);

            XposedBridge.hookAllConstructors(classVolumePanel, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mVolumePanel = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mVolumePanel, "mContext");
                    mGbContext = Utils.getGbContext(context);
                    if (DEBUG) log("VolumePanel constructed; mVolumePanel set");

                    mTimeout = 0;
                    try {
                        mTimeout = Integer.valueOf(prefs.getString(
                                GravityBoxSettings.PREF_KEY_VOLUME_PANEL_TIMEOUT, "0"));
                    } catch (NumberFormatException nfe) {
                        log("Invalid value for PREF_KEY_VOLUME_PANEL_TIMEOUT preference");
                    }

                    Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(classVolumePanel, "STREAMS");
                    XposedHelpers.setBooleanField(streams[1], "show", true);
                    mIconRingerAudibleIdOrig = XposedHelpers.getIntField(streams[1], "iconRes");
                    XposedHelpers.setBooleanField(streams[2], "show", true);
                    XposedHelpers.setBooleanField(streams[5], "show", true);
                    updateRingerIcon();

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED);
                    context.registerReceiver(mBrodcastReceiver, intentFilter);
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
      
            XposedHelpers.findAndHookMethod(classVolumePanel, "isNotificationOrRing", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (XposedHelpers.getBooleanField(mVolumePanel, "mVoiceCapable")) {
                        int streamType = (int) param.args[0];
                        boolean result = streamType == AudioManager.STREAM_RING ||
                                (mVolumesLinked && streamType == AudioManager.STREAM_NOTIFICATION);
                        param.setResult(result);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void updateRingerIcon() {
        if (mIconRingerAudibleId == 0) return;
        try {
            int iconResId = mVolumesLinked ? mIconRingerAudibleIdOrig : mIconRingerAudibleId;
            Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(
                    mVolumePanel.getClass(), "STREAMS");
            XposedHelpers.setIntField(streams[1], "iconRes", iconResId);
            SparseArray<?> streamControls = (SparseArray<?>) XposedHelpers.getObjectField(
                    mVolumePanel, "mStreamControls");
            if (streamControls != null) {
                Object sc = streamControls.get(AudioManager.STREAM_RING);
                if (sc != null) {
                    XposedHelpers.setIntField(sc, "iconRes", iconResId);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
