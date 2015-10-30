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

import com.ceco.lollipop.gravitybox.managers.SysUiManagers;
import com.ceco.lollipop.gravitybox.GlowPadHelper.BgStyle;
import com.ceco.lollipop.gravitybox.managers.AppLauncher.AppInfo;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
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
    private static final int FLAG_EXCLUDE_SEARCH_PANEL = 1 << 0;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static Context mContext;
    private static AppInfo mTargetAppInfo;
    private static BgStyle mTargetBgStyle;

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
