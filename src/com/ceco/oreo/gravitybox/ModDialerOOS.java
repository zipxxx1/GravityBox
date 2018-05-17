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

package com.ceco.oreo.gravitybox;

import java.lang.reflect.Method;

import android.content.Context;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialerOOS {
    public static final String PACKAGE_NAME_IN_CALL_UI = "com.android.incallui";
    private static final String TAG = "GB:ModDialerOOS";
    private static final boolean DEBUG = false;

    private static final String[] CLASS_PHONE_UTILS = new String[] {
            "com.android.incallui.oneplus.OPPhoneUtils", "com.android.incallui.oneplus.s"
    };

    private static final String[] METHOD_SUPPORTS_CALL_RECORDING = new String[] {
            "isSupportCallRecorder", "AN"
    };

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static XSharedPreferences mPrefs;
    private static long mLastPrefReloadMs;

    public static void initInCallUi(final XSharedPreferences prefs, final ClassLoader classLoader) {
        if (DEBUG) log("initInCallUi");
        mPrefs = prefs;

        Method method = null;
        for (String cName : CLASS_PHONE_UTILS) {
            Class<?> clazz = XposedHelpers.findClassIfExists(cName, classLoader);
            if (clazz != null) {
                for (String mName : METHOD_SUPPORTS_CALL_RECORDING) {
                    method = XposedHelpers.findMethodExactIfExists(
                            cName, classLoader, mName, Context.class);
                    if (method != null) {
                        XposedBridge.hookMethod(method, supportsCallRecordingHook);
                        if (DEBUG) log("isSupportCallRecorder found in " + cName + " as " + mName);
                    }
                }
            }
        }

        if (method == null) {
            GravityBox.log(TAG, "Unable to identify isSupportCallRecorder method");
        }
    }

    private static XC_MethodHook supportsCallRecordingHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            reloadPrefsIfExpired();
            if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_OOS_CALL_RECORDING, false)) {
                param.setResult(true);
                if (DEBUG) log("isSupportCallRecorder: forced to return true");
            }
        }
    };

    private static void reloadPrefsIfExpired() {
        if (SystemClock.uptimeMillis() - mLastPrefReloadMs > 10000) {
            mLastPrefReloadMs = SystemClock.uptimeMillis();
            mPrefs.reload();
            if (DEBUG) log("Expired prefs reloaded");
        }
    }
}
