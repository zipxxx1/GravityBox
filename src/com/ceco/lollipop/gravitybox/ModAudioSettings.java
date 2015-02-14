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

package com.ceco.lollipop.gravitybox;

import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudioSettings {
    private static final String TAG = "GB:ModAudioSettings";
    public static final String PACKAGE_NAME = "com.android.settings";
    private static final String CLASS_VOLUME_PREF = "com.android.settings.RingerVolumePreference";
    private static final boolean DEBUG = false;

    private static void log (String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePref = XposedHelpers.findClass(CLASS_VOLUME_PREF, classLoader);

            XposedHelpers.findAndHookMethod(classVolumePref, "onBindDialogView", View.class, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    prefs.reload();
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true)) return;

                    View v = (View) param.args[0];
                    Resources res = v.getContext().getResources();
                    int resId = res.getIdentifier(
                            "notification_section", "id", PACKAGE_NAME);
                    View notifView = ((View) param.args[0]).findViewById(resId);
                    if (notifView != null) {
                        notifView.setVisibility(View.VISIBLE);
                        if (DEBUG) log("Notification volume settings enabled");
                    }

                    int ringerTitleId = res.getIdentifier("ring_volume_title", "string", PACKAGE_NAME);
                    if (ringerTitleId != 0) {
                        int rvTextId = res.getIdentifier("ringer_description_text", "id", PACKAGE_NAME);
                        TextView rvText = (TextView) ((View) param.args[0]).findViewById(rvTextId);
                        if (rvText != null) {
                            rvText.setText(ringerTitleId);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
