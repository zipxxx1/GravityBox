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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;

public class ModLauncher {
    public static final List<String> PACKAGE_NAMES = new ArrayList<String>(Arrays.asList(
            "com.android.launcher3", "com.google.android.googlequicksearchbox"));
    private static final String TAG = "GB:ModLauncher";

    private static final List<DynamicGrid> CLASS_DYNAMIC_GRID; 
    private static final List<ShowAllApps> METHOD_SHOW_ALL_APPS;
    private static final String CLASS_LAUNCHER = "com.android.launcher3.Launcher";
    private static final String CLASS_APP_WIDGET_HOST_VIEW = "android.appwidget.AppWidgetHostView";
    private static final boolean DEBUG = false;

    public static final String ACTION_SHOW_APP_DRAWER = "gravitybox.launcher.intent.action.SHOW_APP_DRAWER";

    private static final class DynamicGrid {
        Class<?> clazz;
        String className;
        String fProfile;
        String fNumRows;
        String fNumCols;
        public DynamicGrid(String cN, String fp, String fnr, String fnc) {
            className = cN;
            fProfile = fp;
            fNumRows = fnr;
            fNumCols = fnc;
        }
    }

    private static final class ShowAllApps {
        String methodName;
        Object[] paramTypes;
        Object[] paramValues;
        public ShowAllApps(String mName, Object[] pTypes, Object[] pValues) {
            methodName = mName;
            paramTypes = pTypes;
            paramValues = pValues;
        }
    }

    static {
        CLASS_DYNAMIC_GRID = new ArrayList<DynamicGrid>();
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.DynamicGrid", "mProfile", "numRows", "numColumns"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("nw", "Bq", "yx", "yy"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("rf", "DU", "AW", "AX"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("sg", "Ez", "BB", "BC"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("ur", "Gi", "Dg", "Dh"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("wd", "Fe", "Ce", "Cf"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.cn", "KA", "Hz", "HA"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.cn", "Kz", "Hy", "Hz"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.cn", "KE", "HD", "HE"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.co", "KK", "HJ", "HK"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.co", "KV", "HU", "HV"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.bf", "KU", "HT", "HU"));
        CLASS_DYNAMIC_GRID.add(new DynamicGrid("com.android.launcher3.bf", "Mf", "Je", "Jf"));

        METHOD_SHOW_ALL_APPS = new ArrayList<ShowAllApps>();
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("onClickAllAppsButton",
                new Object[] { View.class },
                new Object[] { null } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "tk", boolean.class },
                new Object[] { false, "xJ", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.q", boolean.class },
                new Object[] { false, "Dc", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.q", boolean.class },
                new Object[] { false, "Db", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.q", boolean.class },
                new Object[] { false, "Dg", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.s", boolean.class },
                new Object[] { false, "Dm", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.s", boolean.class },
                new Object[] { false, "Dx", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.h", boolean.class },
                new Object[] { false, "Dw", false } ));
        METHOD_SHOW_ALL_APPS.add(new ShowAllApps("a",
                new Object[] { boolean.class, "com.android.launcher3.h", boolean.class },
                new Object[] { false, "EH", false } ));
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static boolean mShouldShowAppDrawer;
    private static boolean mReceiverRegistered;
    private static Method mShowAllAppsMethod;
    private static Object[] mShowAllAppsParams;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.putExtra("showAppDrawer", true);
            context.startActivity(i);
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        boolean dynamicGridFound = false;
        for (DynamicGrid dg : CLASS_DYNAMIC_GRID) {
            final DynamicGrid dynamicGrid = dg;
            try {
                Class<?> cls = XposedHelpers.findClass(dg.className, classLoader);
                Field profile = cls.getDeclaredField(dg.fProfile);
                Class<?> profileClass = profile.getType();
                Field numRows = profileClass.getDeclaredField(dg.fNumRows);
                if (!numRows.getType().isAssignableFrom(float.class))
                    throw new Exception("numRows doesn't seem to be of float type");
                Field numCols = profileClass.getDeclaredField(dg.fNumCols);
                if (!numCols.getType().isAssignableFrom(float.class))
                    throw new Exception("numCols doesn't seem to be of float type");
                if (DEBUG) log("Probably found DynamicGrid class as: " + dg.className +
                        "; numRows=" + dg.fNumRows + "; numCols=" + dg.fNumCols);
                dg.clazz = cls;
            } catch (Throwable t) { 
                if (DEBUG) log("search for dynamic grid " + dg.className + ": " + t.getMessage());
                continue; 
            }

            dynamicGridFound = true;
            try {
                XposedBridge.hookAllConstructors(dynamicGrid.clazz, new XC_MethodHook() { 
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        prefs.reload();
                        Object profile = XposedHelpers.getObjectField(param.thisObject, dynamicGrid.fProfile);
                        if (profile != null) {
                            final int rows = Integer.valueOf(prefs.getString(
                                    GravityBoxSettings.PREF_KEY_LAUNCHER_DESKTOP_GRID_ROWS, "0"));
                            if (rows != 0) {
                                XposedHelpers.setIntField(profile, dynamicGrid.fNumRows, rows);
                                if (DEBUG) log("Launcher rows set to: " + rows);
                            }
                            final int cols = Integer.valueOf(prefs.getString(
                                    GravityBoxSettings.PREF_KEY_LAUNCHER_DESKTOP_GRID_COLS, "0"));
                            if (cols != 0) {
                                XposedHelpers.setIntField(profile, dynamicGrid.fNumCols, cols);
                                if (DEBUG) log("Launcher cols set to: " + cols);
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            break;
        }

        if (!dynamicGridFound) {
            log("Couldn't find dynamic grid. Incompatible Google Search?");
        }

        try {
            Class<?> classLauncher = null;
            try {
                classLauncher = XposedHelpers.findClass(CLASS_LAUNCHER, classLoader);
            } catch (ClassNotFoundError e) { 
                log("Launcher3.Launcher not found");
            }

            if (classLauncher != null) {
                XposedHelpers.findAndHookMethod(classLauncher, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        IntentFilter intentFilter = new IntentFilter(ACTION_SHOW_APP_DRAWER);
                        ((Activity)param.thisObject).registerReceiver(mBroadcastReceiver, intentFilter);
                        mReceiverRegistered = true;
                    }
                });
    
                XposedHelpers.findAndHookMethod(classLauncher, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mReceiverRegistered) {
                            ((Activity)param.thisObject).unregisterReceiver(mBroadcastReceiver);
                            mReceiverRegistered = false;
                        }
                    }
                });
    
                XposedHelpers.findAndHookMethod(classLauncher, "onNewIntent", Intent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        Intent i = (Intent) param.args[0];
                        mShouldShowAppDrawer = (i != null && i.hasExtra("showAppDrawer"));
                    }
                });
    
                XposedHelpers.findAndHookMethod(classLauncher, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mShouldShowAppDrawer) {
                            mShouldShowAppDrawer = false;
                            if (mShowAllAppsMethod != null) {
                                mShowAllAppsMethod.invoke(param.thisObject, mShowAllAppsParams);
                            } else {
                                for (ShowAllApps sapm : METHOD_SHOW_ALL_APPS) {
                                    try {
                                        for (int i = 0; i < sapm.paramTypes.length; i++) {
                                            if (sapm.paramTypes[i] instanceof String) {
                                                sapm.paramTypes[i] = XposedHelpers.findClass(
                                                    (String) sapm.paramTypes[i], classLoader);
                                            }
                                            if (sapm.paramValues[i] instanceof String) {
                                                Object type = XposedHelpers.getStaticObjectField(
                                                        (Class<?>) sapm.paramTypes[i],
                                                        (String) sapm.paramValues[i]);
                                                if (!"Applications".equals(type.toString()))
                                                    continue;
                                                sapm.paramValues[i] = type;
                                            }
                                        }
                                        Class<?> clazz = param.thisObject.getClass();
                                        if (clazz.getName().equals(CLASS_LAUNCHER)) {
                                            mShowAllAppsMethod = XposedHelpers.findMethodExact(clazz,
                                                    sapm.methodName, sapm.paramTypes);
                                        } else if (clazz.getSuperclass().getName().equals(CLASS_LAUNCHER)) {
                                            mShowAllAppsMethod = XposedHelpers.findMethodExact(clazz.getSuperclass(),
                                                    sapm.methodName, sapm.paramTypes);
                                        }
                                        mShowAllAppsParams = sapm.paramValues;
                                        mShowAllAppsMethod.invoke(param.thisObject, mShowAllAppsParams);
                                        break;
                                    } catch (Throwable t) {
                                        if (DEBUG) log("Method name " + sapm.methodName + 
                                                " not found: " + t.getMessage());
                                    }
                                }
                                if (mShowAllAppsMethod == null) {
                                    log("Couldn't find method for opening app dawer. Incompatible Google Search?");
                                }
                            }
                        }
                    }
                });
            }

            Class<?> classAppWidgetHostView = null;
            try {
                classAppWidgetHostView = XposedHelpers.findClass(CLASS_APP_WIDGET_HOST_VIEW, classLoader);
            } catch (ClassNotFoundError e) {
                log("AppWidgetHostView not found");
            }

            if (classAppWidgetHostView != null) {
                XposedHelpers.findAndHookMethod(classAppWidgetHostView, "getAppWidgetInfo", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (prefs.getBoolean(
                                GravityBoxSettings.PREF_KEY_LAUNCHER_RESIZE_WIDGET, false)) {
                            Object info = XposedHelpers.getObjectField(param.thisObject, "mInfo");
                            if (info != null) {
                                XposedHelpers.setIntField(info, "resizeMode", 3);
                                XposedHelpers.setIntField(info, "minResizeWidth", 40);
                                XposedHelpers.setIntField(info, "minResizeHeight", 40);
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
