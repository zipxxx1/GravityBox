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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudioSettings {
    private static final String TAG = "GB:ModAudioSettings";
    public static final String PACKAGE_NAME = "com.android.settings";
    private static final String CLASS_VOLUME_PREF = "com.android.settings.notification.NotificationSettings";
    private static final boolean DEBUG = false;

    private static final String KEY_NOTIFICATION_VOLUME = "notification_volume";

    private static boolean mVolumesLinked;
    private static Unhook mRemovePrefHook;
    private static Drawable mRingerIcon;

    private static void log (String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePref = XposedHelpers.findClass(CLASS_VOLUME_PREF, classLoader);

            XposedHelpers.findAndHookMethod(classVolumePref, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!Utils.hasTelephonySupport()) return;

                    prefs.reload();
                    mVolumesLinked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true);
                    if (mVolumesLinked) return;

                    mRemovePrefHook = XposedHelpers.findAndHookMethod(PreferenceGroup.class,
                            "removePreference", Preference.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param2) throws Throwable {
                            Preference p = (Preference) param2.args[0];
                            if (KEY_NOTIFICATION_VOLUME.equals(p.getKey())) {
                                if (DEBUG) log("Ignoring notification volume pref removal");
                                param2.setResult(false);
                            }
                        }
                    });
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mRemovePrefHook == null) return;
                    mRemovePrefHook.unhook();
                    mRemovePrefHook = null;

                    // init notification volume preference
                    XposedHelpers.callMethod(param.thisObject, "initVolumePreference",
                            KEY_NOTIFICATION_VOLUME, AudioManager.STREAM_NOTIFICATION);
                    if (DEBUG) log("initVolumePreference for notification stream");

                    // change icon of ringer volume preference
                    Context gbctx = Utils.getGbContext((Context) XposedHelpers.getObjectField(
                            param.thisObject, "mContext"));
                    mRingerIcon = gbctx.getDrawable(R.drawable.ic_audio_ring);
                    Preference p = (Preference) XposedHelpers.getObjectField(
                            param.thisObject, "mRingOrNotificationPreference");
                    p.setIcon(mRingerIcon);
                    if (DEBUG) log("icon for ringer volume preference set");
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePref, "updateRingOrNotificationIcon",
                    int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!Utils.hasTelephonySupport() || mVolumesLinked) return;

                    if ((int)param.args[0] > 0) {
                        Preference p = (Preference) XposedHelpers.getObjectField(
                                param.thisObject, "mRingOrNotificationPreference");
                        ImageView iconView = (ImageView) XposedHelpers.getObjectField(p, "mIconView");
                        iconView.setImageDrawable(mRingerIcon);
                        if (DEBUG) log("icon for ringer volume preference updated");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
