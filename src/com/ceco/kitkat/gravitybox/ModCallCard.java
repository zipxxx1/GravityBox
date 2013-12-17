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

package com.ceco.kitkat.gravitybox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModCallCard {
    private static final String TAG = "GB:ModCallCard";
    public static final List<String> PACKAGE_NAMES = new ArrayList<String>(Arrays.asList(
        "com.google.android.dialer", "com.android.dialer"));

    private static final String CLASS_GLOWPAD_WRAPPER = "com.android.incallui.GlowPadWrapper";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void init(final XSharedPreferences prefs, ClassLoader classLoader) {
        try {
            final Class<?> glowpadWrapperClass = XposedHelpers.findClass(CLASS_GLOWPAD_WRAPPER, classLoader);

            XposedHelpers.findAndHookMethod(glowpadWrapperClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    prefs.reload();
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_CALLER_FULLSCREEN_PHOTO, false)) {
                        final View v = (View) param.thisObject;
                        float alpha = (float) prefs.getInt(
                                GravityBoxSettings.PREF_KEY_CALLER_PHOTO_VISIBILITY, 50) / 100f;
                        int iAlpha = alpha == 0 ? 255 : (int) ((1-alpha)*255);
                        ColorDrawable cd = new ColorDrawable(Color.argb(iAlpha, 0, 0, 0));
                        v.setBackground(cd);
                        if (DEBUG) log("GlowPadWrapper onFinishInflate: background color set");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
