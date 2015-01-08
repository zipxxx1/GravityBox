package com.ceco.kitkat.gravitybox.managers;

import com.ceco.kitkat.gravitybox.GravityBoxSettings;

import de.robv.android.xposed.XSharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class SysUiManagers {
    public static BatteryInfoManager BatteryInfoManager;

    public static void init(Context context, XSharedPreferences prefs) {
        BatteryInfoManager = new BatteryInfoManager(context, prefs);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_SOUND_CHANGED);
        context.registerReceiver(sBroadcastReceiver, intentFilter);
    }

    private static BroadcastReceiver sBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BatteryInfoManager.onBroadcastReceived(context, intent);
        }
    };
}
