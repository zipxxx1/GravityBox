/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.lollipop.gravitybox.managers;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHoursActivity;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;

public class SysUiManagers {
    private static final String TAG = "GB:SysUiManagers";

    public static BatteryInfoManager BatteryInfoManager;
    public static StatusBarIconManager IconManager;
    public static StatusbarQuietHoursManager QuietHoursManager;
    public static AppLauncher AppLauncher;
    public static KeyguardStateMonitor KeyguardMonitor;
    public static NotificationDataMonitor NotifDataMonitor;
    public static GpsStatusMonitor GpsMonitor;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void init(Context context, XSharedPreferences prefs) {
        if (context == null)
            throw new IllegalArgumentException("Context cannot be null");
        if (prefs == null)
            throw new IllegalArgumentException("Prefs cannot be null");

        createKeyguardMonitor(context);

        try {
            BatteryInfoManager = new BatteryInfoManager(context, prefs);
        } catch (Throwable t) {
            log("Error creating BatteryInfoManager: ");
            XposedBridge.log(t);
        }

        try {
            IconManager = new StatusBarIconManager(context, prefs);
        } catch (Throwable t) {
            log("Error creating IconManager: ");
            XposedBridge.log(t);
        }

        try {
            QuietHoursManager = StatusbarQuietHoursManager.getInstance(context);
        } catch (Throwable t) {
            log("Error creating QuietHoursManager: ");
            XposedBridge.log(t);
        }

        try {
            AppLauncher = new AppLauncher(context, prefs);
        } catch (Throwable t) {
            log("Error creating AppLauncher: ");
            XposedBridge.log(t);
        }

        try {
            NotifDataMonitor = new NotificationDataMonitor(context);
        } catch (Throwable t) {
            log("Error creating NotificationDataMonitor: ");
            XposedBridge.log(t);
        }

        try {
            GpsMonitor = new GpsStatusMonitor(context);
        } catch (Throwable t) {
            log("Error creating GpsStatusMonitor: ");
            XposedBridge.log(t);
        }

        IntentFilter intentFilter = new IntentFilter();
        // battery info manager
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_SOUND_CHANGED);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_LOW_BATTERY_WARNING_POLICY_CHANGED);
        intentFilter.addAction(com.ceco.lollipop.gravitybox.managers.BatteryInfoManager.ACTION_POWER_SAVE_MODE_CHANGING);

        // icon manager
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_STATUSBAR_COLOR_CHANGED);

        // quiet hours manager
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);

        // AppLauncher
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_APP_LAUNCHER_CHANGED);
        intentFilter.addAction(com.ceco.lollipop.gravitybox.managers.AppLauncher.ACTION_SHOW_APP_LAUCNHER);

        // GpsStatusMonitor
        if (GpsMonitor != null) {
            intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
        }

        context.registerReceiver(sBroadcastReceiver, intentFilter);
    }

    public static void createKeyguardMonitor(Context ctx) {
        if (KeyguardMonitor != null) return;
        try {
            KeyguardMonitor = new KeyguardStateMonitor(ctx);
        } catch (Throwable t) {
            log("Error creating KeyguardMonitor: ");
            XposedBridge.log(t);
        }
    }

    private static BroadcastReceiver sBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BatteryInfoManager != null) {
                BatteryInfoManager.onBroadcastReceived(context, intent);
            }
            if (IconManager != null) {
                IconManager.onBroadcastReceived(context, intent);
            }
            if (QuietHoursManager != null) {
                QuietHoursManager.onBroadcastReceived(context, intent);
            }
            if (AppLauncher != null) {
                AppLauncher.onBroadcastReceived(context, intent);
            }
            if (GpsMonitor != null) {
                GpsMonitor.onBroadcastReceived(context, intent);
            }
        }
    };
}
