/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.view.View;

public class UsbTetherTile extends QsTile {
    public static final String ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED";
    public static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    public static final String ACTION_MEDIA_UNSHARED = "android.intent.action.MEDIA_UNSHARED";
    public static final String USB_CONNECTED = "connected";
    public static final int TETHER_ERROR_NO_ERROR = 0;

    private boolean mUsbTethered = false;
    private boolean mUsbConnected = false;
    private boolean mMassStorageActive = false;
    private String[] mUsbRegexs;
    private boolean mIsReceiving;

    public UsbTetherTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log(getKey() + ": onReceive: " + intent);
            if (intent.getAction().equals(ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(USB_CONNECTED, false);
            }
            if (intent.getAction().equals(Intent.ACTION_MEDIA_SHARED)) {
                mMassStorageActive = true;
            }
            if (intent.getAction().equals(ACTION_MEDIA_UNSHARED)) {
                mMassStorageActive = false;
            }
            refreshState();
        }
    };

    private void registerReceiver() {
        if (!mIsReceiving) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_TETHER_STATE_CHANGED);
            intentFilter.addAction(ACTION_USB_STATE);
            intentFilter.addAction(ACTION_MEDIA_UNSHARED);
            intentFilter.addAction(Intent.ACTION_MEDIA_SHARED);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter);
            mIsReceiving = true;
            if (DEBUG) log(getKey() + ": receiver registered");
        }
    }

    private void unregisterReceiver() {
        if (mIsReceiving) {
            mContext.unregisterReceiver(mBroadcastReceiver);
            mIsReceiving = false;
            if (DEBUG) log(getKey() + ": receiver unregistered");
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerReceiver();
            updateState();
        } else {
            unregisterReceiver();
        }
    }

    private void updateState() {
        try {
            ConnectivityManager cm = 
                    (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            mUsbRegexs = (String[]) XposedHelpers.callMethod(cm, "getTetherableUsbRegexs");
            String[] available = (String[]) XposedHelpers.callMethod(cm, "getTetherableIfaces");
            String[] tethered = (String[]) XposedHelpers.callMethod(cm, "getTetheredIfaces");
            String[] errored = (String[]) XposedHelpers.callMethod(cm, "getTetheringErroredIfaces");
            updateState(available, tethered, errored);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void updateState(String[] available, String[] tethered, String[] errored) {
        updateUsbState(available, tethered, errored);
    }


    private void updateUsbState(String[] available, String[] tethered, String[] errored) {
        mUsbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) mUsbTethered = true;
            }
        }
    }

    private void setUsbTethering(boolean enabled) {
        try {
            ConnectivityManager cm = 
                    (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            XposedHelpers.callMethod(cm, "setUsbTethering", enabled);
            refreshState();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        if (mUsbConnected && !mMassStorageActive) {
            if (mUsbTethered) {
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_usb_tether_on);
                mState.label = mGbContext.getString(R.string.quick_settings_usb_tether_on);
            } else {
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_usb_tether_connected);
                mState.label = mGbContext.getString(R.string.quick_settings_usb_tether_connected);
            }
        } else {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_usb_tether_off);
            mState.label = mGbContext.getString(R.string.quick_settings_usb_tether_off);
        }

        mState.visible = mUsbConnected;
        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        if (mUsbConnected) {
            setUsbTethering(!mUsbTethered);
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick(View view) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        startSettingsActivity(intent);
        return true;
    }
}
