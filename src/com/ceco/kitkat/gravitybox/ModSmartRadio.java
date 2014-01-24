/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModSmartRadio {
    private static final String TAG = "GB:SmartRadio";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static enum State { UNKNOWN, NORMAL, POWER_SAVING };

    private static Context mContext;
    private static int mNormalMode;
    private static int mPowerSavingMode;
    private static ConnectivityManager mConnManager;
    private static boolean mWasMobileDataEnabled;
    private static boolean mWasMobileNetworkAvailable;
    private static State mCurrentState = State.UNKNOWN;
    private static boolean mIsScreenOff;
    private static boolean mPowerSaveWhenScreenOff;
    private static boolean mIgnoreWhileLocked;
    private static NetworkModeChanger mNetworkModeChanger;
    private static int mModeChangeDelay;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_SMART_RADIO_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_NORMAL_MODE)) {
                    setNewModeValue(State.NORMAL, 
                            intent.getIntExtra(GravityBoxSettings.EXTRA_SR_NORMAL_MODE, -1));
                    if (DEBUG) log("mNormalMode = " + mNormalMode);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_POWER_SAVING_MODE)) {
                    setNewModeValue(State.POWER_SAVING,
                            intent.getIntExtra(GravityBoxSettings.EXTRA_SR_POWER_SAVING_MODE, -1));
                    if (DEBUG) log("mPowerSavingMode = " + mPowerSavingMode);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_SCREEN_OFF)) {
                    mPowerSaveWhenScreenOff = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SR_SCREEN_OFF, false);
                    if (DEBUG) log("mPowerSaveWhenScreenOff = " + mPowerSaveWhenScreenOff);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_IGNORE_LOCKED)) {
                    mIgnoreWhileLocked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SR_IGNORE_LOCKED, true);
                    if (DEBUG) log("mIgnoreWhileLocked = " + mIgnoreWhileLocked);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_MODE_CHANGE_DELAY)) {
                    mModeChangeDelay = intent.getIntExtra(GravityBoxSettings.EXTRA_SR_MODE_CHANGE_DELAY, 5);
                    if (DEBUG) log("mModeChangeDelay = " + mModeChangeDelay);
                }
            } else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                boolean mobileDataEnabled = isMobileDataEnabled(); 
                boolean mobileNetworkAvailable = isMobileNetworkAvailable();
                int nwType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
                if (nwType == -1) return;
                NetworkInfo nwInfo = mConnManager.getNetworkInfo(nwType);
                if (nwType == ConnectivityManager.TYPE_WIFI) {
                    if (DEBUG) log("Network type: WIFI; connected: " + nwInfo.isConnected());
                    if (nwInfo.isConnected()) {
                        switchToState(State.POWER_SAVING);
                    } else if (mobileDataEnabled && !(mIsScreenOff && mPowerSaveWhenScreenOff)) {
                        switchToState(State.NORMAL);
                    }
                } else if (nwType == ConnectivityManager.TYPE_MOBILE) {
                    if (DEBUG) log("Network type: MOBILE; connected: " + nwInfo.isConnected());
                    boolean wifiConnected = isWifiConnected();
                    if (!mWasMobileDataEnabled && mobileDataEnabled && !wifiConnected) {
                        if (DEBUG) log("Mobile data got enabled and wifi not connected");
                        switchToState(State.NORMAL);
                    } else if (mWasMobileDataEnabled && !mobileDataEnabled) {
                        if (DEBUG) log("Mobile data got disabled");
                        switchToState(State.POWER_SAVING);
                    } else if (!mWasMobileNetworkAvailable && mobileNetworkAvailable) {
                        if (mobileDataEnabled && !wifiConnected) {
                            if (DEBUG) log("Mobile network got available and data active");
                            switchToState(State.NORMAL, true);
                        } else {
                            if (DEBUG) log("Mobile network got available but data not active");
                            switchToState(State.POWER_SAVING, true);
                        }
                    } else if (mWasMobileNetworkAvailable && !mobileNetworkAvailable) {
                        if (DEBUG) log("Mobile network unavailable. Resetting state to POWER_SAVING.");
                        switchToState(State.POWER_SAVING);
                    }
                    mWasMobileDataEnabled = mobileDataEnabled;
                    mWasMobileNetworkAvailable = mobileNetworkAvailable;
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (DEBUG) log("Screen turning off");
                if (mPowerSaveWhenScreenOff) {
                    switchToState(State.POWER_SAVING, true, true);
                }
                mIsScreenOff = true;
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (DEBUG) log("Screen turning on");
                if (!mIgnoreWhileLocked && isMobileDataEnabled() && !isWifiConnected()) {
                    switchToState(State.NORMAL);
                }
                mIsScreenOff = false;
            } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                if (DEBUG) log("Keyguard unlocked");
                if (mIgnoreWhileLocked && isMobileDataEnabled() && !isWifiConnected()) {
                    switchToState(State.NORMAL);
                }
            }
        }
    };

    private static boolean isMobileDataEnabled() {
        try {
            return (Boolean) XposedHelpers.callMethod(mConnManager, "getMobileDataEnabled");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMobileNetworkAvailable() {
        try {
            return mConnManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isWifiConnected() {
        try {
            return mConnManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void switchToState(State newState) {
        switchToState(newState, false, false);
    }

    private static void switchToState(State newState, boolean force) {
        switchToState(newState, force, false);
    }

    private static void switchToState(State newState, boolean force, boolean withWakeLock) {
        if (mCurrentState == newState && !force) {
            if (DEBUG) log("switchToState: new state == previous state - ignoring");
            return;
        } else if (!isMobileNetworkAvailable()) {
            // force power saving state no matter what so we start with it when mobile network is available again
            if (DEBUG) log("switchToState: mobile network unavailable - resetting to POWER_SAVING state");
            mWasMobileNetworkAvailable = false;
            newState = State.POWER_SAVING;
        } else if (DEBUG) {
            log("Switching to state: " + newState);
        }

        try {
            int networkMode = -1;
            switch (newState) {
                case NORMAL: networkMode = mNormalMode; break;
                case POWER_SAVING: networkMode = mPowerSavingMode; break;
                default: break;
            }
            mNetworkModeChanger.changeNetworkMode(networkMode, withWakeLock);
            mCurrentState = newState;
        } catch (Throwable t) {
            log("switchToState: " + t.getMessage());
        }
    }

    private static void setNewModeValue(State state, int mode) {
        int currentMode = state == State.NORMAL ? mNormalMode : mPowerSavingMode;
        if (mode != currentMode) {
            if (state == State.NORMAL) {
                mNormalMode = mode; 
            } else {
                mPowerSavingMode = mode;
            }
            if (mCurrentState == state) {
                switchToState(state, true);
            }
        }
    }

    private static class NetworkModeChanger implements Runnable {
        private Context mContext;
        private Handler mHandler;
        private int mNextNetworkMode;
        private int mCurrentNetworkMode;
        private WakeLock mWakeLock;

        public NetworkModeChanger(Context context) {
            mContext = context;
            mHandler = new Handler();
            mNextNetworkMode = -1;
            mCurrentNetworkMode = -1;

            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GB:SmartRadio");
        }

        @Override
        public void run() {
            if (mContext == null || mNextNetworkMode == -1) return;
            if (DEBUG) log("NetworkModeChanger: sending intent");
            Intent intent = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
            intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mNextNetworkMode);
            mContext.sendBroadcast(intent);
            mCurrentNetworkMode = mNextNetworkMode;
            releaseWakeLockIfHeld();
        }

        public void changeNetworkMode(int networkMode, boolean withWakeLock) {
            mHandler.removeCallbacks(this);
            releaseWakeLockIfHeld();
            if (networkMode == -1 || networkMode == mCurrentNetworkMode) return;
            mNextNetworkMode = networkMode;
            if (mModeChangeDelay == 0) {
                run();
            } else {
                if (DEBUG) log("NetworkModeChanger: scheduling network mode change");
                if (withWakeLock) {
                    mWakeLock.acquire(mModeChangeDelay*1000+1000);
                    if (DEBUG) log("NetworkModeChanger: Wake Lock acquired");
                }
                mHandler.postDelayed(this, mModeChangeDelay*1000);
            }
        }

        private void releaseWakeLockIfHeld() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
                if (DEBUG) log("NetworkModeChanger: Wake Lock released");
            }
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classSystemUIService = XposedHelpers.findClass(
                    "com.android.systemui.SystemUIService", classLoader);

            mNormalMode = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_NORMAL_MODE, -1);
            mPowerSavingMode = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_POWER_SAVING_MODE, -1);
            mPowerSaveWhenScreenOff = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_SCREEN_OFF, false);
            mIgnoreWhileLocked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SMART_RADIO_IGNORE_LOCKED, true);
            mModeChangeDelay = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_MODE_CHANGE_DELAY, 5);

            XposedHelpers.findAndHookMethod(classSystemUIService, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mContext = (Context) param.thisObject;
                    if (mContext != null) {
                        if (DEBUG) log("Initializing SmartRadio");

                        mConnManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                        mNetworkModeChanger = new NetworkModeChanger(mContext);

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_SMART_RADIO_CHANGED);
                        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
                        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    } 
}
