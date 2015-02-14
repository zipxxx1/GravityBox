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

package com.ceco.lollipop.gravitybox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModTelephony {
    private static final String TAG = "GB:ModTelephony";

    private static final String CLASS_GSM_SERVICE_STATE_TRACKER = 
            "com.android.internal.telephony.gsm.GsmServiceStateTracker";
    private static final String CLASS_SERVICE_STATE = "android.telephony.ServiceState";
    private static final String CLASS_SERVICE_STATE_EXT = "com.mediatek.op.telephony.ServiceStateExt";
    private static final boolean DEBUG = false;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    private static boolean mNationalRoamingEnabled;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_TELEPHONY_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_TELEPHONY_NATIONAL_ROAMING)) {
                    mNationalRoamingEnabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_TELEPHONY_NATIONAL_ROAMING, false);
                    if (DEBUG) log("mNationalRoamingEnabled: " + mNationalRoamingEnabled);
                }
            }
        }
    };

    public static void initZygote(final XSharedPreferences prefs) {
        try {
            final Class<?> classGsmServiceStateTracker = 
                    XposedHelpers.findClass(CLASS_GSM_SERVICE_STATE_TRACKER, null);

            mNationalRoamingEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NATIONAL_ROAMING, false);

            XposedBridge.hookAllConstructors(classGsmServiceStateTracker, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object phone = XposedHelpers.getObjectField(param.thisObject, "mPhone");
                    if (phone != null) {
                        Context context = (Context) XposedHelpers.callMethod(phone, "getContext");
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_TELEPHONY_CHANGED);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("GsmServiceStateTracker constructed; broadcast receiver registered");
                    }
                }
            });

            if (Utils.hasGeminiSupport()) {
                XposedHelpers.findAndHookMethod(CLASS_SERVICE_STATE_EXT, null, "ignoreDomesticRoaming", 
                        new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        if (DEBUG) log("ignoreDomesticRoaming: " + mNationalRoamingEnabled);
                        return mNationalRoamingEnabled;
                    }
                });
            } else {
                XposedHelpers.findAndHookMethod(classGsmServiceStateTracker, "isOperatorConsideredNonRoaming",
                        CLASS_SERVICE_STATE, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!mNationalRoamingEnabled) return;

                        String simNumeric = Utils.SystemProp.get("gsm.sim.operator.numeric", "");
                        String operatorNumeric = (String) XposedHelpers.callMethod(
                                param.args[0], "getOperatorNumeric");

                        boolean equalsMcc = false;
                        try {
                            equalsMcc = simNumeric.substring(0, 3).equals(operatorNumeric.substring(0, 3));
                            if (DEBUG) log("isOperatorConsideredNonRoaming: simNumeric=" + simNumeric +
                                    "; operatorNumeric=" + operatorNumeric + "; equalsMcc=" + equalsMcc);
                        } catch (Exception e) { }

                        boolean result = (Boolean) param.getResult();
                        result = result || equalsMcc;
                        param.setResult(result);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
