/*
 * Copyright (C) 2014 The CyanogenMod Project
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModPower {
    private static final String TAG = "GB:ModPower";
    private static final String CLASS_PM_SERVICE = "com.android.server.power.PowerManagerService";
    private static final String CLASS_PM_HANDLER = "com.android.server.power.PowerManagerService$PowerManagerHandler";
    private static final boolean DEBUG = false;

    private static final int MSG_WAKE_UP = 100;
    private static final int MAX_PROXIMITY_WAIT = 500;

    private static Context mContext;
    private static Handler mHandler;
    private static SensorManager mSensorManager;
    private static Sensor mProxSensor;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE)) {
                toggleWakeUpWithProximityFeature(intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE, false));
            }
        }
    };

    public static void initZygote(final XSharedPreferences prefs) {
        // wake up with proximity feature
        try {
            Class<?> pmServiceClass = XposedHelpers.findClass(CLASS_PM_SERVICE, null);
            Class<?> pmHandlerClass = XposedHelpers.findClass(CLASS_PM_HANDLER, null);

            XposedBridge.hookAllMethods(pmServiceClass, "init", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
                    toggleWakeUpWithProximityFeature(prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_POWER_PROXIMITY_WAKE, false));

                    IntentFilter intentFilter = new IntentFilter(GravityBoxSettings.ACTION_PREF_POWER_CHANGED);
                    mContext.registerReceiver(mBroadcastReceiver, intentFilter);
                }
            });

            XposedHelpers.findAndHookMethod(pmServiceClass, "wakeUpInternal",
                    long.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (!hasWakeUpWithProximityFeature()) return;

                    if (mHandler.hasMessages(MSG_WAKE_UP)) {
                        if (DEBUG) log("wakeUpInternal: Wake up message already queued");
                        param.setResult(null);
                        return;
                    }

                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            final long ident = Binder.clearCallingIdentity();
                            try {
                                if (DEBUG) log("Waking up...");
                                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                            } catch (Throwable t) {
                            } finally {
                                Binder.restoreCallingIdentity(ident);
                            }
                        }
                    };
                    runWithProximityCheck(r);
                    param.setResult(null);
                }
            });

            XposedHelpers.findAndHookMethod(pmHandlerClass, "handleMessage",
                    Message.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    Message msg = (Message) param.args[0];
                    if (msg.what == MSG_WAKE_UP) {
                        ((Runnable) msg.obj).run();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleWakeUpWithProximityFeature(boolean enabled) {
        try {
            if (enabled) {
                mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
                mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            } else {
                mProxSensor = null;
                mSensorManager = null;
            }
            if (DEBUG) log("toggleWakeUpWithProximityFeature: " + enabled);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean hasWakeUpWithProximityFeature() {
        return (mSensorManager != null && mProxSensor != null);
    }

    private static void runWithProximityCheck(Runnable r) {
        if (mHandler.hasMessages(MSG_WAKE_UP)) {
            if (DEBUG) log("runWithProximityCheck: Wake up message already queued");
            return;
        }
        Message msg = mHandler.obtainMessage(MSG_WAKE_UP);
        msg.obj = r;
        mHandler.sendMessageDelayed(msg, MAX_PROXIMITY_WAIT);
        runPostProximityCheck(r);
    }

    private static void runPostProximityCheck(final Runnable r) {
        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                mSensorManager.unregisterListener(this);
                if (!mHandler.hasMessages(MSG_WAKE_UP)) {
                    if (DEBUG) log("Prox sensor status received too late. Wake up already triggered");
                    return;
                }
                mHandler.removeMessages(MSG_WAKE_UP);
                if (event.values[0] >= (mProxSensor.getMaximumRange() * 0.1f)) {
                    r.run();
                } else if (DEBUG) {
                    log("Prox sensor covered: ignoring wake up");
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        }, mProxSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
}
