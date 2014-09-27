/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

import com.ceco.gm2.gravitybox.ledcontrol.QuietHours;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.os.Build;

public class ModDialer {
    private static final String TAG = "GB:ModDialer";
    public static final String PACKAGE_NAME = Build.VERSION.SDK_INT > 17 ? 
            "com.android.dialer" : "com.android.contacts";
    private static final String CLASS_DIALPAD_FRAGMENT = PACKAGE_NAME + ".dialpad.DialpadFragment";
    private static final boolean DEBUG = false;

    private static QuietHours mQuietHours;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void init(final ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLASS_DIALPAD_FRAGMENT, classLoader, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param2) throws Throwable {
                    XSharedPreferences qhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
                    mQuietHours = new QuietHours(qhPrefs);
                    if (DEBUG) log("DialpadFragment: onResume()");
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_DIALPAD_FRAGMENT, classLoader, "playTone",
                    int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.DIALPAD)) {
                        param.setResult(null);
                        if (DEBUG) log("DialpadFragment: playTone ignored");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
