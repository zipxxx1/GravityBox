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

package com.ceco.gm2.gravitybox;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModExpandedDesktop {
    private static final String TAG = "GB:ModExpandedDesktop";
    private static final String CLASS_PHONE_WINDOW_MANAGER = "com.android.internal.policy.impl.PhoneWindowManager";
    private static final String CLASS_WINDOW_MANAGER_FUNCS = "android.view.WindowManagerPolicy.WindowManagerFuncs";
    private static final String CLASS_IWINDOW_MANAGER = "android.view.IWindowManager";

    private static final boolean DEBUG = false;

    private static Context mContext;
    private static Object mPhoneWindowManager;
    private static SettingsObserver mSettingsObserver;
    private static boolean mExpandedDesktop;
    private static int mExpandedDesktopMode;
    private static Unhook mStatusbarShowLwHook;
    private static boolean mNavbarOverride;
    private static float mNavbarHeightScaleFactor = 1;
    private static float mNavbarHeightLandscapeScaleFactor = 1;
    private static float mNavbarWidthScaleFactor = 1;

    public static final String SETTING_EXPANDED_DESKTOP_STATE = "gravitybox_expanded_desktop_state";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    static class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    SETTING_EXPANDED_DESKTOP_STATE), false, this);
            updateSettings();
        }

        @Override 
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_EXPANDED_DESKTOP_MODE_CHANGED)
                    && intent.hasExtra(GravityBoxSettings.EXTRA_ED_MODE)) {
                final int expandedDesktopMode = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_ED_MODE, GravityBoxSettings.ED_DISABLED);
                mExpandedDesktopMode = expandedDesktopMode;
                updateSettings();
            } else if (intent.getAction().equals(ModStatusbarColor.ACTION_PHONE_STATUSBAR_VIEW_MADE)) {
                updateSettings();
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_NAVBAR_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_HEIGHT)) {
                    mNavbarHeightScaleFactor = 
                            (float)intent.getIntExtra(GravityBoxSettings.EXTRA_NAVBAR_HEIGHT, 100) / 100f;
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_HEIGHT_LANDSCAPE)) {
                    mNavbarHeightLandscapeScaleFactor = (float)intent.getIntExtra(
                                    GravityBoxSettings.EXTRA_NAVBAR_HEIGHT_LANDSCAPE,  100) / 100f;
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_WIDTH)) {
                    mNavbarWidthScaleFactor = 
                            (float)intent.getIntExtra(GravityBoxSettings.EXTRA_NAVBAR_WIDTH, 100) / 100f;
                }
                updateSettings();
            }
        }
    };

    private static void updateSettings() {
        if (mContext == null || mPhoneWindowManager == null) return;

        try {
            final boolean expandedDesktop = Settings.System.getInt(mContext.getContentResolver(), 
                    SETTING_EXPANDED_DESKTOP_STATE, 0) == 1;
            if (mExpandedDesktopMode == GravityBoxSettings.ED_DISABLED && expandedDesktop) {
                    Settings.System.putInt(mContext.getContentResolver(),
                            SETTING_EXPANDED_DESKTOP_STATE, 0);
                    return;
            }

            if (mExpandedDesktop != expandedDesktop) {
                mExpandedDesktop = expandedDesktop;
            }

            XposedHelpers.callMethod(mPhoneWindowManager, "updateSettings");

            int[] navigationBarWidthForRotation = (int[]) XposedHelpers.getObjectField(
                    mPhoneWindowManager, "mNavigationBarWidthForRotation");
            int[] navigationBarHeightForRotation = (int[]) XposedHelpers.getObjectField(
                    mPhoneWindowManager, "mNavigationBarHeightForRotation");
            final int portraitRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mPortraitRotation");
            final int upsideDownRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mUpsideDownRotation");
            final int landscapeRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mLandscapeRotation");
            final int seascapeRotation = XposedHelpers.getIntField(mPhoneWindowManager, "mSeascapeRotation");

            if (expandedDesktopHidesNavigationBar()) {
                navigationBarWidthForRotation[portraitRotation]
                        = navigationBarWidthForRotation[upsideDownRotation]
                        = navigationBarWidthForRotation[landscapeRotation]
                        = navigationBarWidthForRotation[seascapeRotation]
                        = navigationBarHeightForRotation[portraitRotation]
                        = navigationBarHeightForRotation[upsideDownRotation]
                        = navigationBarHeightForRotation[landscapeRotation]
                        = navigationBarHeightForRotation[seascapeRotation] = 0;
            } else {
                final int resWidthId = mContext.getResources().getIdentifier(
                        "navigation_bar_width", "dimen", "android");
                final int resHeightId = mContext.getResources().getIdentifier(
                        "navigation_bar_height", "dimen", "android");
                final int resHeightLandscapeId = mContext.getResources().getIdentifier(
                        "navigation_bar_height_landscape", "dimen", "android");

                navigationBarHeightForRotation[portraitRotation] =
                navigationBarHeightForRotation[upsideDownRotation] =
                    (int) (mContext.getResources().getDimensionPixelSize(resHeightId)
                    * mNavbarHeightScaleFactor);
                navigationBarHeightForRotation[landscapeRotation] =
                navigationBarHeightForRotation[seascapeRotation] =
                    (int) (mContext.getResources().getDimensionPixelSize(resHeightLandscapeId)
                    * mNavbarHeightLandscapeScaleFactor);

                navigationBarWidthForRotation[portraitRotation] =
                navigationBarWidthForRotation[upsideDownRotation] =
                navigationBarWidthForRotation[landscapeRotation] =
                navigationBarWidthForRotation[seascapeRotation] =
                    (int) (mContext.getResources().getDimensionPixelSize(resWidthId)
                    * mNavbarWidthScaleFactor);
            }

            XposedHelpers.setObjectField(mPhoneWindowManager, "mNavigationBarWidthForRotation", navigationBarWidthForRotation);
            XposedHelpers.setObjectField(mPhoneWindowManager, "mNavigationBarHeightForRotation", navigationBarHeightForRotation);

            XposedHelpers.callMethod(mPhoneWindowManager, "updateRotation", false);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void initZygote(final XSharedPreferences prefs) {
        try {
            final Class<?> classPhoneWindowManager = XposedHelpers.findClass(CLASS_PHONE_WINDOW_MANAGER, null);

            mNavbarOverride = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_OVERRIDE, false);
            if (mNavbarOverride) {
                mNavbarHeightScaleFactor = 
                        (float) prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_HEIGHT, 100) / 100f;
                mNavbarHeightLandscapeScaleFactor = 
                        (float) prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_HEIGHT_LANDSCAPE, 100) / 100f;
                mNavbarWidthScaleFactor = 
                        (float) prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_WIDTH, 100) / 100f;
            }

            mExpandedDesktopMode = GravityBoxSettings.ED_DISABLED;
            try {
                mExpandedDesktopMode = Integer.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_EXPANDED_DESKTOP, "0"));
            } catch (NumberFormatException nfe) {
                log("Invalid value for PREF_KEY_EXPANDED_DESKTOP preference");
            }

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "init",
                Context.class, CLASS_IWINDOW_MANAGER, CLASS_WINDOW_MANAGER_FUNCS, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        mPhoneWindowManager = param.thisObject;

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_EXPANDED_DESKTOP_MODE_CHANGED);
                        intentFilter.addAction(ModStatusbarColor.ACTION_PHONE_STATUSBAR_VIEW_MADE);
                        if (mNavbarOverride) {
                            intentFilter.addAction(GravityBoxSettings.ACTION_PREF_NAVBAR_CHANGED);
                        }
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);

                        mSettingsObserver = new SettingsObserver(
                                (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler"));
                        mSettingsObserver.observe();

                        if (DEBUG) log("Phone window manager initialized");
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "finishPostLayoutPolicyLw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    final Object statusBar = XposedHelpers.getObjectField(param.thisObject, "mStatusBar");
                    if (statusBar == null || !expandedDesktopHidesStatusbar()) return;

                    mStatusbarShowLwHook = XposedHelpers.findAndHookMethod(
                            statusBar.getClass(), "showLw", boolean.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param2) throws Throwable {
                            if (param2.thisObject == statusBar) {
                                if (DEBUG) log("finishPostLayoutPolicyLw: calling hideLw instead of showLw");
                                return XposedHelpers.callMethod(param2.thisObject, "hideLw", true);
                            } else {
                                return XposedBridge.invokeOriginalMethod(param2.method, param2.thisObject, param2.args);
                            }
                        }
                    });
                }
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mStatusbarShowLwHook != null) {
                        mStatusbarShowLwHook.unhook();
                        mStatusbarShowLwHook = null;
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "beginLayoutLw",
                    boolean.class, int.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    try {
                        if (!(Boolean) param.args[0]) return;

                        if (expandedDesktopHidesStatusbar()) {
                            final Object statusBar = XposedHelpers.getObjectField(param.thisObject, "mStatusBar");
                            if (statusBar != null) {
                                if (DEBUG) log("beginLayoutLw: setting mStableTop to 0");
                                XposedHelpers.setIntField(param.thisObject, "mStableTop", 0);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classPhoneWindowManager, "getContentInsetHintLw",
                    WindowManager.LayoutParams.class, Rect.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mExpandedDesktop) {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        return null;
                    }

                    try {
                        final WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) param.args[0];
                        final Rect contentInset = (Rect) param.args[1];
                        final int fl = attrs.flags;
                        final int systemUiVisibility = (attrs.systemUiVisibility |
                                XposedHelpers.getIntField(attrs, "subtreeSystemUiVisibility"));

                        if ((fl & (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR))
                                == (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR)) {
                            int availRight, availBottom;
                            if (shouldHideNavigationBarLw(systemUiVisibility)) {
                                availRight = XposedHelpers.getIntField(param.thisObject, "mUnrestrictedScreenLeft") 
                                        + XposedHelpers.getIntField(param.thisObject, "mUnrestrictedScreenWidth");
                                availBottom = XposedHelpers.getIntField(param.thisObject, "mUnrestrictedScreenTop")
                                        + XposedHelpers.getIntField(param.thisObject, "mUnrestrictedScreenHeight");
                            } else {
                                availRight = XposedHelpers.getIntField(param.thisObject, "mRestrictedScreenLeft")
                                        + XposedHelpers.getIntField(param.thisObject, "mRestrictedScreenWidth");
                                availBottom = XposedHelpers.getIntField(param.thisObject, "mRestrictedScreenTop")
                                        + XposedHelpers.getIntField(param.thisObject, "mRestrictedScreenHeight");
                            }
                            if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
                                if ((fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                                    contentInset.set(XposedHelpers.getIntField(param.thisObject, "mStableFullscreenLeft"),
                                            XposedHelpers.getIntField(param.thisObject, "mStableFullscreenTop"),
                                            availRight - XposedHelpers.getIntField(param.thisObject, "mStableFullscreenRight"),
                                            availBottom - XposedHelpers.getIntField(param.thisObject, "mStableFullscreenBottom"));
                                } else {
                                    contentInset.set(XposedHelpers.getIntField(param.thisObject, "mStableLeft"), 
                                            XposedHelpers.getIntField(param.thisObject, "mStableTop"),
                                            availRight - XposedHelpers.getIntField(param.thisObject, "mStableRight"), 
                                            availBottom - XposedHelpers.getIntField(param.thisObject, "mStableBottom"));
                                }
                            } else if ((fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                                contentInset.setEmpty();
                            } else if ((systemUiVisibility & (View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) == 0) {
                                contentInset.set(XposedHelpers.getIntField(param.thisObject, "mCurLeft"), 
                                        XposedHelpers.getIntField(param.thisObject, "mCurTop"),
                                        availRight - XposedHelpers.getIntField(param.thisObject, "mCurRight"), 
                                        availBottom - XposedHelpers.getIntField(param.thisObject, "mCurBottom"));
                            } else {
                                contentInset.set(XposedHelpers.getIntField(param.thisObject, "mCurLeft"), 
                                        XposedHelpers.getIntField(param.thisObject, "mCurTop"),
                                        availRight - XposedHelpers.getIntField(param.thisObject, "mCurRight"), 
                                        availBottom - XposedHelpers.getIntField(param.thisObject, "mCurBottom"));
                            }
                            return null;
                        }
                        contentInset.setEmpty();
                    } catch(Throwable t) {
                        if (DEBUG) log(t.getMessage());
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    }

                    return null;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean shouldHideNavigationBarLw(int systemUiVisibility) {
        if (expandedDesktopHidesNavigationBar()) {
            return true;
        }

        if (mPhoneWindowManager != null
                && XposedHelpers.getBooleanField(mPhoneWindowManager, "mCanHideNavigationBar")) {
            if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0) {
                return true;
            }
        }

        return false;
    }

    private static boolean expandedDesktopHidesStatusbar() {
        return (mExpandedDesktop
                && (mExpandedDesktopMode & GravityBoxSettings.ED_STATUSBAR) != 0);
    }

    private static boolean expandedDesktopHidesNavigationBar() {
        return (mExpandedDesktop
                && (mExpandedDesktopMode & GravityBoxSettings.ED_NAVBAR) != 0);
    }
}
