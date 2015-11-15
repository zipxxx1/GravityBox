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

import com.ceco.marshmallow.gravitybox.GlowPadHelper.BgStyle;
import com.ceco.marshmallow.gravitybox.managers.SysUiManagers;
import com.ceco.marshmallow.gravitybox.managers.AppLauncher.AppInfo;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModSearchPanel {
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String TAG = "GB:ModSearchPanel";
    private static final boolean DEBUG = false;

    private static final String CLASS_SEARCH_PANEL_VIEW = "com.android.systemui.SearchPanelView";
    private static final String CLASS_SEARCH_PANEL_VIEW_CIRCLE = "com.android.systemui.SearchPanelCircleView";
    private static final int FLAG_EXCLUDE_SEARCH_PANEL = 1 << 0;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static Context mContext;
    private static AppInfo mTargetAppInfo;
    private static BgStyle mTargetBgStyle;
    private static boolean mNavbarLeftHanded;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_NAVBAR_RING_TARGET_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RING_TARGET_APP)) {
                    initTargetAppInfo(intent.getStringExtra(GravityBoxSettings.EXTRA_RING_TARGET_APP));
                    if (DEBUG) log("Target app updated");
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_RING_TARGET_BG_STYLE)) {
                    mTargetBgStyle = BgStyle.valueOf(
                            intent.getStringExtra(GravityBoxSettings.EXTRA_RING_TARGET_BG_STYLE));
                    initTargetAppInfo();
                }
            }
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            mNavbarLeftHanded = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_ENABLE, false) &&
                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_LEFT_HANDED, false);

            XposedBridge.hookAllConstructors(XposedHelpers.findClass(CLASS_SEARCH_PANEL_VIEW, classLoader),
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mContext == null) {
                        mContext = ((View) param.thisObject).getContext();
                        mTargetAppInfo = SysUiManagers.AppLauncher.createAppInfo();
                        mTargetBgStyle = BgStyle.valueOf(
                              prefs.getString(GravityBoxSettings.PREF_KEY_NAVBAR_RING_TARGETS_BG_STYLE, "NONE"));
                        initTargetAppInfo(prefs.getString(GravityBoxSettings.PREF_KEY_NAVBAR_RING_TARGET.get(0), null));
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_NAVBAR_RING_TARGET_CHANGED);
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("Search panel view constructed");
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_SEARCH_PANEL_VIEW, classLoader, "maybeSwapSearchIcon",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mTargetAppInfo == null || mTargetAppInfo.getIntent() == null)
                        return;

                    ImageView logo = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mLogo");
                    logo.setImageDrawable(mTargetAppInfo.getAppIcon());
                    param.setResult(null);
                    if (DEBUG) log("Target logo changed");
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_SEARCH_PANEL_VIEW, classLoader, "startAssistActivity",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mTargetAppInfo == null || mTargetAppInfo.getIntent() == null)
                        return;

                    Object bar = XposedHelpers.getObjectField(param.thisObject, "mBar");
                    XposedHelpers.callMethod(bar, "animateCollapsePanels", FLAG_EXCLUDE_SEARCH_PANEL);
                    Resources res = mContext.getResources();
                    int animEnter = res.getIdentifier("search_launch_enter", "anim", PACKAGE_NAME);
                    int animExit = res.getIdentifier("search_launch_exit", "anim", PACKAGE_NAME);
                    final ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext, animEnter, animExit);
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                SysUiManagers.AppLauncher.startActivity(mContext, mTargetAppInfo.getIntent(), opts);
                            } catch (Exception e) {
                                log("Error starting activity: " + e.getMessage());
                            }
                        }
                    });
                    param.setResult(null);
                }
            });

            if (mNavbarLeftHanded) {
                XposedHelpers.findAndHookMethod(CLASS_SEARCH_PANEL_VIEW_CIRCLE, classLoader,
                        "updateCircleRect", Rect.class, float.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (XposedHelpers.getBooleanField(param.thisObject, "mHorizontal")) {
                            float circleSize = XposedHelpers.getFloatField(param.thisObject,
                                    (boolean) param.args[2] ? "mCircleMinSize" : "mCircleSize");
                            int baseMargin = XposedHelpers.getIntField(param.thisObject, "mBaseMargin");
                            float offset = (float) param.args[1];
                            int left = (int) (-(circleSize / 2) + baseMargin + offset);
                            Rect rect = (Rect) param.args[0];
                            rect.set(left, rect.top, (int) (left + circleSize), rect.bottom);
                            if (DEBUG) log("SearchPanelCircleView rect: " + rect);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void initTargetAppInfo(String appInfoValue) {
        mTargetAppInfo.initAppInfo(appInfoValue);

        int sizePx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60,
                mContext.getResources().getDisplayMetrics()));
        Bitmap bitmap = Utils.drawableToBitmap(mTargetAppInfo.getAppIcon());
        if (bitmap != null) {
            bitmap = GlowPadHelper.createStyledBitmap(bitmap, sizePx, mTargetBgStyle);
            mTargetAppInfo.setAppIcon(bitmap);
        }
    }

    private static void initTargetAppInfo() {
        initTargetAppInfo(mTargetAppInfo.getValue());
    }
}
