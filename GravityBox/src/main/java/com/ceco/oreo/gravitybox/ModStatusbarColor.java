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

import com.ceco.oreo.gravitybox.managers.StatusBarIconManager;
import com.ceco.oreo.gravitybox.managers.SysUiManagers;
import com.ceco.oreo.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.oreo.gravitybox.managers.StatusBarIconManager.IconManagerListener;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XCallback;

public class ModStatusbarColor {
    private static final String TAG = "GB:ModStatusbarColor";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String CLASS_STATUSBAR = "com.android.systemui.statusbar.phone.StatusBar";
    private static final String CLASS_STATUSBAR_ICON_VIEW = "com.android.systemui.statusbar.StatusBarIconView";
    private static final String CLASS_STATUSBAR_ICON = "com.android.internal.statusbar.StatusBarIcon";
    private static final String CLASS_SB_TRANSITIONS = "com.android.systemui.statusbar.phone.PhoneStatusBarTransitions";
    private static final String CLASS_SB_DARK_ICON_DISPATCHER = "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl";
    private static final boolean DEBUG = false;

    public static final String ACTION_PHONE_STATUSBAR_VIEW_MADE = "gravitybox.intent.action.PHONE_STATUSBAR_VIEW_MADE";

    private static Object mStatusBar;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    // in process hooks
    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> statusbarClass = XposedHelpers.findClass(CLASS_STATUSBAR, classLoader);
            XposedHelpers.findAndHookMethod(statusbarClass, 
                    "makeStatusBarView", new XC_MethodHook(XCallback.PRIORITY_LOWEST) {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) {
                    mStatusBar = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");

                    if (SysUiManagers.IconManager != null) {
                        SysUiManagers.IconManager.registerListener(mIconManagerListener);
                    }

                    Intent i = new Intent(ACTION_PHONE_STATUSBAR_VIEW_MADE);
                    context.sendBroadcast(i);
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            final Class<?> statusbarIconViewClass = XposedHelpers.findClass(CLASS_STATUSBAR_ICON_VIEW, classLoader);
            XposedHelpers.findAndHookMethod(statusbarIconViewClass, "getIcon",
                    CLASS_STATUSBAR_ICON, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (SysUiManagers.IconManager != null && SysUiManagers.IconManager.isColoringEnabled() &&
                            XposedHelpers.getObjectField(param.thisObject, "mNotification") == null) {
                        final String iconPackage = 
                                (String) XposedHelpers.getObjectField(param.args[0], "pkg");
                        if (DEBUG) log("statusbarIconView.getIcon: iconPackage=" + iconPackage);
                        Drawable d = getColoredDrawable(((View)param.thisObject).getContext(),
                                iconPackage, (Icon) XposedHelpers.getObjectField(param.args[0], "icon"));
                        if (d != null) {
                            param.setResult(d);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            final Class<?> sbTransitionsClass = XposedHelpers.findClass(CLASS_SB_TRANSITIONS, classLoader);
            XposedHelpers.findAndHookMethod(sbTransitionsClass, "applyMode",
                    int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (SysUiManagers.IconManager != null) {
                        final float signalClusterAlpha = (Float) XposedHelpers.callMethod(
                                param.thisObject, "getNonBatteryClockAlphaFor", (Integer) param.args[0]);
                        final float textAndBatteryAlpha = (Float) XposedHelpers.callMethod(
                                param.thisObject, "getBatteryClockAlpha", (Integer) param.args[0]);
                        SysUiManagers.IconManager.setIconAlpha(signalClusterAlpha, textAndBatteryAlpha);
                        if (DEBUG) log("SbTransitions: applyMode: signalClusterAlpha=" + signalClusterAlpha +
                                "; textAndBatteryAlpha=" + textAndBatteryAlpha);
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }

        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLASS_SB_DARK_ICON_DISPATCHER, classLoader),
                    "setIconTintInternal", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (SysUiManagers.IconManager != null) {
                        SysUiManagers.IconManager.setIconTint(
                                XposedHelpers.getIntField(param.thisObject, "mIconTint"));
                        if (DEBUG) log("DarkIconDispatcher: setIconTintInternal: iconTint = " + Integer.toHexString(
                                XposedHelpers.getIntField(param.thisObject, "mIconTint")));
                    }
                }
            });
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static IconManagerListener mIconManagerListener = new IconManagerListener() {
        @Override
        public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
            if ((flags & (StatusBarIconManager.FLAG_ICON_COLOR_CHANGED |
                    StatusBarIconManager.FLAG_ICON_STYLE_CHANGED)) != 0) {
                updateStatusIconsStatusBar();
                updateStatusIconsKeyguard();
                //updateSettingsButton();
            }
        }
    };

    private static Drawable getColoredDrawable(Context ctx, String pkg, Icon icon) {
        if (icon == null) return null;

        Drawable d = null;
        if (pkg == null || PACKAGE_NAME.equals(pkg)) {
            final int iconId = (int) XposedHelpers.callMethod(icon, "getResId");
            d = SysUiManagers.IconManager.getBasicIcon(iconId);
            if (d != null) {
                return d;
            }
        }
        d = icon.loadDrawable(ctx);
        if (d != null) {
            if (SysUiManagers.IconManager.isColoringEnabled()) {
                d = SysUiManagers.IconManager.applyColorFilter(d.mutate(),
                        PorterDuff.Mode.SRC_IN);
            } else {
                d.clearColorFilter();
            }
        }
        return d;
    }

    private static void updateStatusIconsStatusBar() {
        try {
            Object view = XposedHelpers.getObjectField(mStatusBar, "mStatusBarView");
            if (view != null) {
                Object bt = XposedHelpers.getObjectField(view, "mBarTransitions");
                ViewGroup vg = (ViewGroup) XposedHelpers.getObjectField(bt, "mStatusIcons");
                updateStatusIcons(vg);
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void updateStatusIconsKeyguard() {
        try {
            ViewGroup kg = (ViewGroup) XposedHelpers.getObjectField(mStatusBar, "mKeyguardStatusBar");
            int resId = kg.getResources().getIdentifier("statusIcons", "id", PACKAGE_NAME);
            ViewGroup vg = kg.findViewById(resId);
            updateStatusIcons(vg);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void updateStatusIcons(ViewGroup container) {
        final int childCount = container.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (!container.getChildAt(i).getClass().getName().equals(CLASS_STATUSBAR_ICON_VIEW)) {
                continue;
            }
            ImageView v = (ImageView) container.getChildAt(i);
            final Object sbIcon = XposedHelpers.getObjectField(v, "mIcon");
            if (sbIcon != null) {
                final String iconPackage =
                        (String) XposedHelpers.getObjectField(sbIcon, "pkg");
                Drawable d = getColoredDrawable(v.getContext(), iconPackage,
                        (Icon) XposedHelpers.getObjectField(sbIcon, "icon"));
                if (d != null) {
                    v.setImageDrawable(d);
                }
            }
        }
    }

    private static void updateSettingsButton() {
        if (mStatusBar == null || SysUiManagers.IconManager == null) return;
        try {
            Object header = XposedHelpers.getObjectField(mStatusBar, "mHeader");
            ImageView settingsButton = (ImageView) XposedHelpers.getObjectField(
                    header, "mSettingsButton");
            if (SysUiManagers.IconManager.isColoringEnabled()) {
                settingsButton.setColorFilter(SysUiManagers.IconManager.getIconColor(),
                        PorterDuff.Mode.SRC_IN);
            } else {
                settingsButton.clearColorFilter();
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
