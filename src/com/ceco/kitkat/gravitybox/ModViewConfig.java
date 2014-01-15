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

package com.ceco.kitkat.gravitybox;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModViewConfig {
    private static final String CLASS_VIEW_CONFIG = "android.view.ViewConfiguration";

    public static void initZygote(final XSharedPreferences prefs) {
        try {
            final Class<?> classViewConfig = XposedHelpers.findClass(CLASS_VIEW_CONFIG, null);

            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_FORCE_OVERFLOW_MENU_BUTTON, false)) {
                XposedHelpers.findAndHookMethod(classViewConfig, "hasPermanentMenuKey",
                        XC_MethodReplacement.returnConstant(false));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
