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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialer {
    private static final String TAG = "GB:ModDialer";
    public static final List<String> PACKAGE_NAMES = new ArrayList<String>(Arrays.asList(
        "com.google.android.dialer", "com.android.dialer"));

    private static final String CLASS_GLOWPAD_WRAPPER = "com.android.incallui.GlowPadWrapper";
    private static final String CLASS_CALL_CARD_FRAGMENT = "com.android.incallui.CallCardFragment";
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

        try {
            final Class<?> classCallCardFragment = XposedHelpers.findClass(CLASS_CALL_CARD_FRAGMENT, classLoader);

            XposedHelpers.findAndHookMethod(classCallCardFragment, "setDrawableToImageView",
                    ImageView.class, Drawable.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_CALLER_UNKNOWN_PHOTO_ENABLE, false) ||
                            param.args[1] != null) return;

                    final Context context = ((View) param.args[0]).getContext();
                    final Context gbContext = context.createPackageContext(GravityBox.PACKAGE_NAME, 0);
                    final String path = gbContext.getFilesDir() + "/caller_photo";
                    File f = new File(path);
                    if (f.exists() && f.canRead()) {
                        Bitmap b = BitmapFactory.decodeFile(path);
                        if (b != null) {
                            param.args[1] = new BitmapDrawable(context.getResources(), b);
                            if (DEBUG) log("Unknow caller photo set");
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
