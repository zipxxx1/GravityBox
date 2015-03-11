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

package com.ceco.lollipop.gravitybox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PhoneWrapper {
    private static final String TAG = "GB:PhoneWrapper";
    private static final boolean DEBUG = false;

    public static final int NT_WCDMA_PREFERRED = 0;             // GSM/WCDMA (WCDMA preferred) (2g/3g)
    public static final int NT_GSM_ONLY = 1;                    // GSM Only (2g)
    public static final int NT_WCDMA_ONLY = 2;                  // WCDMA ONLY (3g)
    public static final int NT_GSM_WCDMA_AUTO = 3;              // GSM/WCDMA Auto (2g/3g)
    public static final int NT_CDMA_EVDO = 4;                   // CDMA/EVDO Auto (2g/3g)
    public static final int NT_CDMA_ONLY = 5;                   // CDMA Only (2G)
    public static final int NT_EVDO_ONLY = 6;                   // Evdo Only (3G)
    public static final int NT_LTE_CDMA_EVDO = 8; 
    public static final int NT_LTE_GSM_WCDMA = 9;
    public static final int NT_LTE_CMDA_EVDO_GSM_WCDMA = 10;
    public static final int NT_LTE_ONLY = 11;
    public static final int NT_LTE_WCDMA = 12;
    public static final int NT_MODE_UNKNOWN = 13;

    public static final String PREFERRED_NETWORK_MODE = "preferred_network_mode";

    public static final String ACTION_CHANGE_NETWORK_TYPE = "gravitybox.intent.action.CHANGE_NETWORK_TYPE";
    public static final String EXTRA_NETWORK_TYPE = "networkType";

    private static Class<?> mClsPhoneFactory;
    private static Class<?> mSystemProperties;
    private static Context mContext;
    private static int mSimSlot;
    private static Boolean mHasMsimSupport = null;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public static String getNetworkModeNameFromValue(int networkMode) {
        switch(networkMode) {
            case NT_GSM_ONLY: return "GSM (2G)";
            case NT_WCDMA_PREFERRED: return "GSM/WCDMA Preferred (3G/2G)";
            case NT_GSM_WCDMA_AUTO: return "GSM/WCDMA Auto (2G/3G)";
            case NT_WCDMA_ONLY: return "WCDMA (3G)";
            case NT_CDMA_EVDO: return "CDMA/EvDo Auto";
            case NT_CDMA_ONLY: return "CDMA";
            case NT_EVDO_ONLY: return "EvDo";
            case NT_LTE_CDMA_EVDO: return "LTE (CDMA)";
            case NT_LTE_GSM_WCDMA: return "LTE (GSM)";
            case NT_LTE_CMDA_EVDO_GSM_WCDMA: return "LTE (Global)";
            default: return "Undefined";
        }
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_CHANGE_NETWORK_TYPE) &&
                    intent.hasExtra(EXTRA_NETWORK_TYPE)) {
                int networkType = intent.getIntExtra(EXTRA_NETWORK_TYPE, NT_WCDMA_PREFERRED);
                if (DEBUG) log("received ACTION_CHANGE_NETWORK_TYPE broadcast: networkType = " + networkType);
                setPreferredNetworkType(networkType);
            }
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED)) {
                mSimSlot = intent.getIntExtra(GravityBoxSettings.EXTRA_SIM_SLOT, 0);
                if (DEBUG) log("received ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED broadcast: " +
                                    "mSimSlot = " + mSimSlot);
                setPreferredNetworkType(Settings.Global.getInt(mContext.getContentResolver(), 
                        PREFERRED_NETWORK_MODE, getDefaultNetworkType()));
            }
        }
    };

    private static Class<?> getPhoneFactoryClass() {
        return XposedHelpers.findClass("com.android.internal.telephony.PhoneFactory", null);
    }

    private static String getMakePhoneMethodName() {
        if (Utils.hasGeminiSupport()) {
            return "makeDefaultPhones";
        } else if (hasMsimSupport()) {
            return "makeDefaultPhones";
        } else {
            return "makeDefaultPhone";
        }
    }

    private static Object getPhone() {
        if (mClsPhoneFactory == null) {
            return null;
        } else if (hasMsimSupport()) {
            return XposedHelpers.callStaticMethod(mClsPhoneFactory, "getPhone", mSimSlot);
        } else {
            return XposedHelpers.callStaticMethod(mClsPhoneFactory, "getDefaultPhone");
        }
    }

    public static void initZygote(final XSharedPreferences prefs) {
        if (DEBUG) log("Entering init state");

        try {
            mClsPhoneFactory = getPhoneFactoryClass();
            mSystemProperties = XposedHelpers.findClass("android.os.SystemProperties", null);

            mSimSlot = 0;
            try {
                mSimSlot = Integer.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_QS_NETWORK_MODE_SIM_SLOT, "0"));
            } catch (NumberFormatException nfe) {
                log("Invalid value for SIM Slot preference: " + nfe.getMessage());
            }
            if (DEBUG) log("mSimSlot = " + mSimSlot);

            XposedHelpers.findAndHookMethod(mClsPhoneFactory, getMakePhoneMethodName(), 
                    Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mContext = (Context) param.args[0];
                    if (DEBUG) log("PhoneFactory makeDefaultPhones - phone wrapper initialized");
                    onInitialize();
                }
            });

            if (Utils.isMtkDevice()) {
                XposedHelpers.findAndHookMethod("com.android.internal.telephony.uicc.UiccController", 
                        null, "setNotification", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (DEBUG) log("UiccController.setNotification(" + param.args[0] + ")");
                        param.setResult(null);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void onInitialize() {
        if (mContext != null) {
            IntentFilter intentFilter = new IntentFilter(ACTION_CHANGE_NETWORK_TYPE);
            intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, intentFilter);
        }
    }

    private static void setPreferredNetworkType(int networkType) {
        try {
            Object defPhone = getPhone();
            if (defPhone == null) return;
            if (Utils.hasGeminiSupport()) {
                mSimSlot = (Integer) XposedHelpers.callMethod(defPhone, "get3GSimId");
                if (DEBUG) log("Gemini 3G SIM ID: " + mSimSlot);
                Class<?>[] paramArgs = new Class<?>[3];
                paramArgs[0] = int.class;
                paramArgs[1] = Message.class;
                paramArgs[2] = int.class;
                XposedHelpers.callMethod(defPhone, "setPreferredNetworkTypeGemini", 
                        paramArgs, networkType, null, mSimSlot);
            } else {
                Settings.Global.putInt(mContext.getContentResolver(), PREFERRED_NETWORK_MODE, networkType);
                Class<?>[] paramArgs = new Class<?>[2];
                paramArgs[0] = int.class;
                paramArgs[1] = Message.class;
                XposedHelpers.callMethod(defPhone, "setPreferredNetworkType", paramArgs, networkType, null);
            }
        } catch (Throwable t) {
            log("setPreferredNetworkType failed: " + t.getMessage());
            XposedBridge.log(t);
        }
    }

    public static int getDefaultNetworkType() {
        try {
            int mode = (Integer) XposedHelpers.callStaticMethod(mSystemProperties, 
                "getInt", "ro.telephony.default_network", NT_WCDMA_PREFERRED);
            if (DEBUG) log("getDefaultNetworkMode: mode=" + mode);
            return mode;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return NT_WCDMA_PREFERRED;
        }
    }

    public static boolean isLteNetworkType(int networkType) {
        return (networkType >= NT_LTE_CDMA_EVDO &&
                networkType < NT_MODE_UNKNOWN);
    }

    public static boolean hasMsimSupport() {
        if (mHasMsimSupport != null) return mHasMsimSupport;

        try {
            Object mtm = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.telephony.TelephonyManager", null),
                        "getDefault");
            mHasMsimSupport = (Boolean) XposedHelpers.callMethod(mtm, "isMultiSimEnabled") &&
                    (Integer) XposedHelpers.callMethod(mtm, "getPhoneCount") > 1;
        } catch (Throwable t) {
            if (DEBUG) XposedBridge.log(t);
            mHasMsimSupport = false;
        }

        if (DEBUG) log("hasMsimSupport: " + mHasMsimSupport);
        return mHasMsimSupport;
    }

    public static int getMsimPreferredDataSubscription() {
        try {
            Object mtm = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.telephony.MSimTelephonyManager", null),
                        "getDefault");
            return (Integer) XposedHelpers.callMethod(mtm, "getPreferredDataSubscription");
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean isMsimCardInserted(int slot) {
        try {
            Object mtm = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.telephony.MSimTelephonyManager", null),
                        "getDefault");
            int phoneCount = (Integer) XposedHelpers.callMethod(mtm, "getPhoneCount");
            return ((phoneCount > slot) &&
                    (Boolean) XposedHelpers.callMethod(mtm, "hasIccCard", slot));
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }
}
