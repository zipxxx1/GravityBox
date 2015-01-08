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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.IconManagerListener;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager;
import com.ceco.gm2.gravitybox.managers.SysUiManagers;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalCluster implements BroadcastSubReceiver, IconManagerListener {
    public static final String TAG = "GB:StatusbarSignalCluster";
    protected static final boolean DEBUG = false;

    protected LinearLayout mView;
    protected StatusBarIconManager mIconManager;
    protected Resources mResources;
    private List<String> mErrorsLogged = new ArrayList<String>();

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected void logAndMute(String key, Throwable t) {
        if (!mErrorsLogged.contains(key)) {
            XposedBridge.log(t);
            mErrorsLogged.add(key);
        }
    }

    public static void initResources(XSharedPreferences prefs, InitPackageResourcesParam resparam) {
        XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);

        if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_LOLLIPOP_ICONS, false)) {
            Map<String, Integer> ic_map = new HashMap<String, Integer>();
            ic_map.put("stat_sys_wifi_signal_0", R.drawable.stat_sys_wifi_signal_0_lollipop);
            ic_map.put("stat_sys_wifi_signal_1", R.drawable.stat_sys_wifi_signal_1_lollipop);
            ic_map.put("stat_sys_wifi_signal_1_fully", R.drawable.stat_sys_wifi_signal_1_fully_lollipop);
            ic_map.put("stat_sys_wifi_signal_2", R.drawable.stat_sys_wifi_signal_2_lollipop);
            ic_map.put("stat_sys_wifi_signal_2_fully", R.drawable.stat_sys_wifi_signal_2_fully_lollipop);
            ic_map.put("stat_sys_wifi_signal_3", R.drawable.stat_sys_wifi_signal_3_lollipop);
            ic_map.put("stat_sys_wifi_signal_3_fully", R.drawable.stat_sys_wifi_signal_3_fully_lollipop);
            ic_map.put("stat_sys_wifi_signal_4", R.drawable.stat_sys_wifi_signal_4_lollipop);
            ic_map.put("stat_sys_wifi_signal_4_fully", R.drawable.stat_sys_wifi_signal_4_fully_lollipop);
            ic_map.put("stat_sys_wifi_signal_null", R.drawable.stat_sys_wifi_signal_null_lollipop);
            ic_map.put("stat_sys_signal_0", R.drawable.stat_sys_signal_0_lollipop);
            ic_map.put("stat_sys_signal_0_fully", R.drawable.stat_sys_signal_0_lollipop);
            ic_map.put("stat_sys_signal_1", R.drawable.stat_sys_signal_1_lollipop);
            ic_map.put("stat_sys_signal_1_fully", R.drawable.stat_sys_signal_1_fully_lollipop);
            ic_map.put("stat_sys_signal_2", R.drawable.stat_sys_signal_2_lollipop);
            ic_map.put("stat_sys_signal_2_fully", R.drawable.stat_sys_signal_2_fully_lollipop);
            ic_map.put("stat_sys_signal_3", R.drawable.stat_sys_signal_3_lollipop);
            ic_map.put("stat_sys_signal_3_fully", R.drawable.stat_sys_signal_3_fully_lollipop);
            ic_map.put("stat_sys_signal_4", R.drawable.stat_sys_signal_4_lollipop);
            ic_map.put("stat_sys_signal_4_fully", R.drawable.stat_sys_signal_4_fully_lollipop);
            ic_map.put("stat_sys_signal_null", R.drawable.stat_sys_signal_null_lollipop);
            ic_map.put("stat_sys_gemini_radio_off", R.drawable.stat_sys_signal_off_lollipop);
            ic_map.put("stat_sys_gemini_signal_0", R.drawable.stat_sys_signal_0_lollipop);
            ic_map.put("stat_sys_gemini_signal_1_blue", R.drawable.stat_sys_signal_1_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_1_purple", R.drawable.stat_sys_signal_1_fully_purple_lollipop);
            ic_map.put("stat_sys_gemini_signal_1_orange", R.drawable.stat_sys_signal_1_fully_orange_lollipop);
            ic_map.put("stat_sys_gemini_signal_1_green", R.drawable.stat_sys_signal_1_fully_green_lollipop);
            ic_map.put("stat_sys_gemini_signal_1_white", R.drawable.stat_sys_signal_1_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_2_blue", R.drawable.stat_sys_signal_2_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_2_purple", R.drawable.stat_sys_signal_2_fully_purple_lollipop);
            ic_map.put("stat_sys_gemini_signal_2_orange", R.drawable.stat_sys_signal_2_fully_orange_lollipop);
            ic_map.put("stat_sys_gemini_signal_2_green", R.drawable.stat_sys_signal_2_fully_green_lollipop);
            ic_map.put("stat_sys_gemini_signal_2_white", R.drawable.stat_sys_signal_2_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_3_blue", R.drawable.stat_sys_signal_3_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_3_purple", R.drawable.stat_sys_signal_3_fully_purple_lollipop);
            ic_map.put("stat_sys_gemini_signal_3_orange", R.drawable.stat_sys_signal_3_fully_orange_lollipop);
            ic_map.put("stat_sys_gemini_signal_3_green", R.drawable.stat_sys_signal_3_fully_green_lollipop);
            ic_map.put("stat_sys_gemini_signal_3_white", R.drawable.stat_sys_signal_3_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_4_blue", R.drawable.stat_sys_signal_4_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_4_purple", R.drawable.stat_sys_signal_4_fully_purple_lollipop);
            ic_map.put("stat_sys_gemini_signal_4_orange", R.drawable.stat_sys_signal_4_fully_orange_lollipop);
            ic_map.put("stat_sys_gemini_signal_4_green", R.drawable.stat_sys_signal_4_fully_green_lollipop);
            ic_map.put("stat_sys_gemini_signal_4_white", R.drawable.stat_sys_signal_4_fully_lollipop);
            ic_map.put("stat_sys_gemini_signal_null", R.drawable.stat_sys_signal_null_lollipop);
            ic_map.put("stat_sys_gemini_signal_searching", R.drawable.stat_sys_signal_searching_lollipop);
            for (String key : ic_map.keySet()) {
                try {
                    resparam.res.setReplacement(ModStatusBar.PACKAGE_NAME, "drawable", key,
                            modRes.fwd(ic_map.get(key)));
                } catch (Throwable t) {
                    if (DEBUG) log("Drawable not found: " + key);
                }
            }
        }
    }

    public static StatusbarSignalCluster create(LinearLayout view) {
        if (Utils.isMtkDevice()) {
            return new StatusbarSignalClusterMtk(view);
        } else {
            return new StatusbarSignalCluster(view);
        }
    }

    public StatusbarSignalCluster(LinearLayout view) {
        mView = view;
        mIconManager = SysUiManagers.IconManager;;
        mResources = mView.getResources();

        if (mView != null) {
            try {
                XposedHelpers.findAndHookMethod(mView.getClass(), "apply", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        apply();
                    }
                });
            } catch (Throwable t) {
                log("Error hooking apply() method: " + t.getMessage());
            }
        }

        if (mIconManager != null) {
            mIconManager.registerListener(this);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) { }

    public void initPreferences(XSharedPreferences prefs) { }

    private void update() {
        if (mView != null) {
            try {
                XposedHelpers.callMethod(mView, "apply");
            } catch (Throwable t) {
                log("Error invoking apply() method: " + t.getMessage());
            }
        }
    }

    protected void apply() {
        try {
            if (XposedHelpers.getObjectField(mView, "mWifiGroup") != null) {
                if (mIconManager != null && mIconManager.isColoringEnabled()) {
                    updateWiFiIcon();
                    if (!XposedHelpers.getBooleanField(mView, "mIsAirplaneMode")) {
                        updateMobileIcon();
                    }
                }
                updateAirplaneModeIcon();
            }
        } catch (Throwable t) {
            logAndMute("apply", t);
        }
    }

    protected void updateWiFiIcon() {
        try {
            if (XposedHelpers.getBooleanField(mView, "mWifiVisible") && mIconManager != null &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView wifiIcon = (ImageView) XposedHelpers.getObjectField(mView, "mWifi");
                if (wifiIcon != null) {
                    int resId = XposedHelpers.getIntField(mView, "mWifiStrengthId");
                    Drawable d = mIconManager.getWifiIcon(resId);
                    if (d != null) wifiIcon.setImageDrawable(d);
                }
                ImageView wifiActivity = (ImageView) XposedHelpers.getObjectField(mView, "mWifiActivity");
                if (wifiActivity != null) {
                    try {
                        int resId = XposedHelpers.getIntField(mView, "mWifiActivityId");
                        Drawable d = mResources.getDrawable(resId).mutate();
                        d = mIconManager.applyDataActivityColorFilter(d);
                        wifiActivity.setImageDrawable(d);
                    } catch (Resources.NotFoundException e) {
                        wifiActivity.setImageDrawable(null);
                    }
                }
            }
        } catch (Throwable t) {
            logAndMute("updateWiFiIcon", t);
        }
    }

    protected void updateMobileIcon() {
        try {
            if (XposedHelpers.getBooleanField(mView, "mMobileVisible") && mIconManager != null &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView mobile = (ImageView) XposedHelpers.getObjectField(mView, "mMobile");
                if (mobile != null) {
                    int resId = XposedHelpers.getIntField(mView, "mMobileStrengthId");
                    Drawable d = mIconManager.getMobileIcon(resId);
                    if (d != null) mobile.setImageDrawable(d);
                }
                if (mIconManager.isMobileIconChangeAllowed()) {
                    ImageView mobileActivity = 
                            (ImageView) XposedHelpers.getObjectField(mView, "mMobileActivity");
                    if (mobileActivity != null) {
                        try {
                            int resId = XposedHelpers.getIntField(mView, "mMobileActivityId");
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyDataActivityColorFilter(d);
                            mobileActivity.setImageDrawable(d);
                        } catch (Resources.NotFoundException e) { 
                            mobileActivity.setImageDrawable(null);
                        }
                    }
                    ImageView mobileType = (ImageView) XposedHelpers.getObjectField(mView, "mMobileType");
                    if (mobileType != null) {
                        try {
                            int resId = XposedHelpers.getIntField(mView, "mMobileTypeId");
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyColorFilter(d);
                            mobileType.setImageDrawable(d);
                        } catch (Resources.NotFoundException e) { 
                            mobileType.setImageDrawable(null);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logAndMute("updateMobileIcon", t);
        }
    }

    protected void updateAirplaneModeIcon() {
        try {
            ImageView airplaneModeIcon = (ImageView) XposedHelpers.getObjectField(mView, "mAirplane");
            if (airplaneModeIcon != null) {
                Drawable d = airplaneModeIcon.getDrawable();
                if (mIconManager != null && mIconManager.isColoringEnabled()) {
                    d = mIconManager.applyColorFilter(d);
                } else if (d != null) {
                    d.setColorFilter(null);
                }
                airplaneModeIcon.setImageDrawable(d);
            }
        } catch (Throwable t) {
            logAndMute("updateAirplaneModeIcon", t);
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & (StatusBarIconManager.FLAG_ICON_COLOR_CHANGED |
                StatusBarIconManager.FLAG_DATA_ACTIVITY_COLOR_CHANGED |
                StatusBarIconManager.FLAG_ICON_COLOR_SECONDARY_CHANGED |
                StatusBarIconManager.FLAG_SIGNAL_ICON_MODE_CHANGED)) != 0) {
            update();
        }
    }
}
