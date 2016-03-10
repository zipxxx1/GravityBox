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

package com.ceco.gm2.gravitybox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.ceco.gm2.gravitybox.ledcontrol.LedSettings;
import com.ceco.gm2.gravitybox.ledcontrol.LedSettings.ActiveScreenMode;
import com.ceco.gm2.gravitybox.ledcontrol.LedSettings.LedMode;
import com.ceco.gm2.gravitybox.ledcontrol.QuietHours;
import com.ceco.gm2.gravitybox.ledcontrol.QuietHoursActivity;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModLedControl {
    private static final String TAG = "GB:ModLedControl";
    public static final boolean DEBUG = false;
    private static final String CLASS_NOTIFICATION_MANAGER_SERVICE = "com.android.server.NotificationManagerService";
    private static final String CLASS_STATUSBAR_MGR_SERVICE = "com.android.server.StatusBarManagerService";
    private static final String CLASS_VIBRATOR_SERVICE = "com.android.server.VibratorService";
    private static final String PACKAGE_NAME_GRAVITYBOX = "com.ceco.gm2.gravitybox";

    public static final String NOTIF_EXTRAS = "gbExtras";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN_MODE = "gbActiveScreenMode";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE = "gbActiveScreenPocketMode";
    public static final String NOTIF_EXTRA_PROGRESS_TRACKING = "gbProgressTracking";

    public static final String ACTION_CLEAR_NOTIFICATIONS = "gravitybox.intent.action.CLEAR_NOTIFICATIONS";

    private static XSharedPreferences mPrefs;
    private static XSharedPreferences mQhPrefs;
    private static Context mContext;
    private static PowerManager mPm;
    private static SensorManager mSm;
    private static KeyguardManager mKm;
    private static Sensor mProxSensor;
    private static boolean mOnPanelRevealedBlocked;
    private static QuietHours mQuietHours;
    private static Map<String, Long> mNotifTimestamps = new HashMap<String, Long>();
    private static boolean mUserPresent;
    private static Object mNotifManagerService;
    private static boolean mProximityWakeUpEnabled;
    private static AudioManager mAudioManager;

    private static SensorEventListener mProxSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) { 
            try {
                final boolean screenCovered = 
                        event.values[0] != mProxSensor.getMaximumRange();
                if (DEBUG) log("mProxSensorEventListener: " + event.values[0] +
                        "; screenCovered=" + screenCovered);
                if (!screenCovered) {
                    performActiveScreen();
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            } finally {
                try { 
                    mSm.unregisterListener(this, mProxSensor); 
                } catch (Throwable t) {
                    // should never happen
                }
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(LedSettings.ACTION_UNC_SETTINGS_CHANGED)) {
                mPrefs.reload();
                if (intent.hasExtra(LedSettings.EXTRA_UNC_AS_ENABLED)) {
                    toggleActiveScreenFeature(intent.getBooleanExtra(
                            LedSettings.EXTRA_UNC_AS_ENABLED, false));
                }
            } else if (action.equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQhPrefs.reload();
                mQuietHours = new QuietHours(mQhPrefs);
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                if (DEBUG) log("User present");
                mUserPresent = true;
                mOnPanelRevealedBlocked = false;
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mUserPresent = false;
            } else if (action.equals(ACTION_CLEAR_NOTIFICATIONS)) {
                clearNotifications();
            } else if (action.equals(GravityBoxSettings.ACTION_PREF_POWER_CHANGED) &&
                    intent.hasExtra(GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE)) {
                mProximityWakeUpEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_POWER_PROXIMITY_WAKE, false);
            }
        }
    };

    public static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote(final XSharedPreferences mainPrefs) {
        mPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol");
        mPrefs.makeWorldReadable();
        mQhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
        mQhPrefs.makeWorldReadable();
        mQuietHours = new QuietHours(mQhPrefs);

        mProximityWakeUpEnabled = mainPrefs.getBoolean(GravityBoxSettings.PREF_KEY_POWER_PROXIMITY_WAKE, false);

        try {
            final Class<?> nmsClass = XposedHelpers.findClass(CLASS_NOTIFICATION_MANAGER_SERVICE, null);
            XposedBridge.hookAllConstructors(nmsClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mNotifManagerService == null) {
                        mNotifManagerService = param.thisObject;
                        mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(LedSettings.ACTION_UNC_SETTINGS_CHANGED);
                        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
                        intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                        intentFilter.addAction(ACTION_CLEAR_NOTIFICATIONS);
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_POWER_CHANGED);
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);

                        toggleActiveScreenFeature(!mPrefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false) && 
                                mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_ENABLED, false));
                        if (DEBUG) log("Notification manager service initialized");
                    }
                }
            });

            switch (Build.VERSION.SDK_INT) {
                case 16:
                    // pkg, tag, id, notification, idOut
                    XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                            "enqueueNotificationWithTag", String.class, String.class, int.class,
                            Notification.class, int[].class, notifyHook);
                    break;
                case 17:
                    // pkg, tag, id, notification, idOut, userId
                    XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                            "enqueueNotificationWithTag", String.class, String.class, int.class,
                            Notification.class, int[].class, int.class, notifyHook);
                    break;
                case 18:
                    // pkg, basePkg, tag, id, notification, idOut, userId
                    XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                            "enqueueNotificationWithTag", String.class, String.class, String.class, int.class,
                            Notification.class, int[].class, int.class, notifyHook);
                    break;
            }

            XposedHelpers.findAndHookMethod(CLASS_STATUSBAR_MGR_SERVICE, null, "onPanelRevealed", 
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mOnPanelRevealedBlocked) {
                        param.setResult(null);
                        if (DEBUG) log("onPanelRevealed blocked");
                    }
                }
            });

            XposedHelpers.findAndHookConstructor(Notification.class, Parcel.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final Parcel p = (Parcel) param.args[0];
                    final int pos = p.dataPosition();
                    Bundle extras = new Bundle();
                    boolean extrasFound = false;
                    while (p.dataAvail() > 0) {
                        try {
                            if (NOTIF_EXTRAS.equals(p.readString())) {
                                if (DEBUG) log("GB extras magic found. Reading bundle");
                                extras = p.readBundle();
                                extrasFound = true;
                                break;
                            } else if (DEBUG) {
                                log("No GB extras magic in value");
                            }
                        } catch (Throwable t) {
                            if (DEBUG) log("Error reading value from parcel: " + t.getMessage());
                        }
                    }
                    if (!extrasFound) {
                        p.setDataPosition(pos);
                    }
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, NOTIF_EXTRAS, extras);
                }
            });

            XposedHelpers.findAndHookMethod(Notification.class, "writeToParcel",
                    Parcel.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    Parcel p = (Parcel) param.args[0];
                    p.writeString(NOTIF_EXTRAS);
                    p.writeBundle((Bundle) XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, NOTIF_EXTRAS));
                    if (DEBUG) log("Notification to parcel: gbExtras written");
                }
            });

            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLASS_VIBRATOR_SERVICE, null),
                    "startVibrationLocked", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mQuietHours.quietHoursActive() && mQuietHours.muteSystemVibe) {
                        if (DEBUG) log("startVibrationLocked: system level vibration suppressed");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static XC_MethodHook notifyHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                if (mPrefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false)) {
                    if (DEBUG) log("Ultimate notification control feature locked.");
                    return;
                }

                final int id = (Integer) param.args[Build.VERSION.SDK_INT > 17 ? 3 : 2];
                final String pkgName = (String) param.args[0];
                Notification n = (Notification) param.args[Build.VERSION.SDK_INT > 17 ? 4 : 3];

                if (pkgName.equals(PACKAGE_NAME_GRAVITYBOX) && id >= 2049) return;

                LedSettings ls = LedSettings.deserialize(mPrefs.getStringSet(pkgName, null));
                if (!ls.getEnabled()) {
                    // use default settings in case they are active
                    ls = LedSettings.deserialize(mPrefs.getStringSet("default", null));
                    if (!ls.getEnabled() && !mQuietHours.quietHoursActive(ls, n, mUserPresent)) {
                        return;
                    }
                }
                if (DEBUG) log(pkgName + ": " + ls.toString());

                Bundle extras = new Bundle();
                XposedHelpers.setAdditionalInstanceField(n, NOTIF_EXTRAS, extras);
                final boolean qhActive = mQuietHours.quietHoursActive(ls, n, mUserPresent);
                final boolean qhActiveIncludingLed = qhActive && mQuietHours.muteLED;
                final boolean qhActiveIncludingVibe = qhActive && mQuietHours.muteVibe;
                final boolean qhActiveIncludingActiveScreen = qhActive &&
                        !mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_IGNORE_QUIET_HOURS, false);
                extras.putBoolean(NOTIF_EXTRA_PROGRESS_TRACKING, ls.getProgressTracking());

                boolean isOngoing = ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0 || 
                        (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0);
                // additional check if old notification had a foreground service flag set since it seems not to be propagated
                // for updated notifications (until Notification gets processed by WorkerHandler which is too late for us)
                if (!isOngoing) {
                    try {
                        ArrayList<?> notifList = (ArrayList<?>) XposedHelpers.getObjectField(param.thisObject, "mNotificationList");
                        synchronized (notifList) {
                            int index = -1;
                            if (Build.VERSION.SDK_INT == 16) { // 4.1
                                index = (Integer) XposedHelpers.callMethod(param.thisObject, "indexOfNotificationLocked",
                                            param.args[0], param.args[1], param.args[2]);
                            } else if (Build.VERSION.SDK_INT == 17) { // 4.2
                                index = (Integer) XposedHelpers.callMethod(param.thisObject, "indexOfNotificationLocked",
                                        param.args[0], param.args[1], param.args[2], param.args[5]);
                            } else { // 4.3
                                index = (Integer) XposedHelpers.callMethod(param.thisObject, "indexOfNotificationLocked",
                                        param.args[0], param.args[2], param.args[3], param.args[6]);
                            }
                            if (index >= 0) {
                                Object oldNotif = notifList.get(index);
                                if (oldNotif != null) {
                                    Notification oldN = Build.VERSION.SDK_INT == 18 ?
                                            (Notification) XposedHelpers.callMethod(oldNotif, "getNotification") :
                                                (Notification) XposedHelpers.getObjectField(oldNotif, "notification");
                                    if ((oldN.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                                        n.flags |= Notification.FLAG_FOREGROUND_SERVICE | 
                                                Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
                                        isOngoing = true;
                                    }
                                    if (DEBUG) log("Old notification foreground service check: isOngoing=" + isOngoing);
                                }
                            }
                        }
                    } catch (Throwable t) { /* yet another vendor messing with the internals... */ }
                }

                if (isOngoing && !ls.getOngoing() && !qhActive) {
                    if (DEBUG) log("Ongoing led control disabled. Ignoring.");
                    return;
                }

                // lights
                if (qhActiveIncludingLed || 
                        (ls.getEnabled() && ls.getLedMode() == LedMode.OFF &&
                            !(isOngoing && !ls.getOngoing()))) {
                    n.defaults &= ~Notification.DEFAULT_LIGHTS;
                    n.flags &= ~Notification.FLAG_SHOW_LIGHTS;
                } else if (ls.getEnabled() && ls.getLedMode() == LedMode.OVERRIDE &&
                        !(isOngoing && !ls.getOngoing())) {
                    n.defaults &= ~Notification.DEFAULT_LIGHTS;
                    n.flags |= Notification.FLAG_SHOW_LIGHTS;
                    n.ledOnMS = ls.getLedOnMs();
                    n.ledOffMS = ls.getLedOffMs();
                    n.ledARGB = ls.getColor();
                }

                // vibration
                if (qhActiveIncludingVibe) {
                    n.defaults &= ~Notification.DEFAULT_VIBRATE;
                    n.vibrate = new long[] {0};
                } else if (ls.getEnabled() && !(isOngoing && !ls.getOngoing())) {
                    if (ls.getVibrateOverride() && ls.getVibratePattern() != null &&
                            ((n.defaults & Notification.DEFAULT_VIBRATE) != 0 || 
                              n.vibrate != null || !ls.getVibrateReplace())) {
                        n.defaults &= ~Notification.DEFAULT_VIBRATE;
                        n.vibrate = ls.getVibratePattern();
                    }
                }

                // sound
                if (qhActive || (ls.getEnabled() && 
                        ls.getSoundToVibrateDisabled() && isRingerModeVibrate())) {
                    n.defaults &= ~Notification.DEFAULT_SOUND;
                    n.sound = null;
                    n.flags &= ~Notification.FLAG_INSISTENT;
                } else {
                    if (ls.getSoundOverride() &&
                        ((n.defaults & Notification.DEFAULT_SOUND) != 0 ||
                          n.sound != null || !ls.getSoundReplace())) {
                        n.defaults &= ~Notification.DEFAULT_SOUND;
                        n.sound = ls.getSoundUri();
                    }
                    if (ls.getSoundOnlyOnce()) {
                        if (ls.getSoundOnlyOnceTimeout() > 0) {
                            if (mNotifTimestamps.containsKey(pkgName) &&
                                    (System.currentTimeMillis() - mNotifTimestamps.get(pkgName) < 
                                            ls.getSoundOnlyOnceTimeout())) {
                                n.defaults &= ~Notification.DEFAULT_SOUND;
                                n.defaults &= ~Notification.DEFAULT_VIBRATE;
                                n.sound = null;
                                n.vibrate = new long[] {0};
                                n.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
                            } else {
                                mNotifTimestamps.put(pkgName, System.currentTimeMillis());
                            }
                        } else {
                            n.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
                        }
                    } else {
                        n.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
                    }
                    if (ls.getInsistent()) {
                        n.flags |= Notification.FLAG_INSISTENT;
                    } else {
                        n.flags &= ~Notification.FLAG_INSISTENT;
                    }
                }

                if (ls.getEnabled()) {
                    // active screen mode
                    if (ls.getActiveScreenMode() != ActiveScreenMode.DISABLED && 
                            !qhActiveIncludingActiveScreen && !isOngoing && mPm != null && mKm.isKeyguardLocked()) {
                        extras.putString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE,
                                ls.getActiveScreenMode().toString());
                        extras.putBoolean(NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE, !mProximityWakeUpEnabled &&
                                mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_POCKET_MODE, true));
                    }
                }

                if (DEBUG) log("Notification info: defaults=" + n.defaults + "; flags=" + n.flags);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }

        @Override
        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                Notification n = (Notification) param.args[Build.VERSION.SDK_INT > 17 ? 4 : 3];
                Bundle extras = (Bundle) XposedHelpers.getAdditionalInstanceField(n, NOTIF_EXTRAS);
                if (extras == null || !extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE) || !(mPm != null && 
                        !mPm.isScreenOn() && mKm.isKeyguardLocked()))
                    return;

                final ActiveScreenMode asMode = ActiveScreenMode.valueOf(
                        extras.getString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE));
                final String pkgName = (String) param.args[0];
                if (DEBUG) log("Performing Active Screen for " + pkgName + " with mode " +
                        asMode.toString());

                mOnPanelRevealedBlocked = asMode == ActiveScreenMode.EXPAND_PANEL;
                if (mSm != null && mProxSensor != null &&
                        extras.getBoolean(NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE)) {
                    mSm.registerListener(mProxSensorEventListener, mProxSensor, SensorManager.SENSOR_DELAY_FASTEST);
                    if (DEBUG) log("Performing active screen using proximity sensor");
                } else {
                    performActiveScreen();
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private static boolean isRingerModeVibrate() {
        try {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            return (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    private static void toggleActiveScreenFeature(boolean enable) {
        try {
            if (enable && mContext != null) {
                mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                mKm = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                mSm = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
                mProxSensor = mSm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            } else {
                mProxSensor = null;
                mSm = null;
                mPm = null;
                mKm = null;
            }
            if (DEBUG) log("Active screen feature: " + enable);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private static void performActiveScreen() {
        if (mOnPanelRevealedBlocked) {
            mContext.sendBroadcast(new Intent(ModHwKeys.ACTION_EXPAND_NOTIFICATIONS));
        }
        if (Build.VERSION.SDK_INT > 16) {
            long ident = Binder.clearCallingIdentity();
            try {
                mPm.wakeUp(SystemClock.uptimeMillis());
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        } else {
            final WakeLock wl = mPm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, TAG);
            wl.acquire(10000);
        }
    }

    private static void clearNotifications() {
        try {
            if (mNotifManagerService != null) {
                if (Build.VERSION.SDK_INT == 16) {
                    XposedHelpers.callMethod(mNotifManagerService, "cancelAll");
                } else {
                    XposedHelpers.callMethod(mNotifManagerService, "cancelAll",
                            XposedHelpers.callStaticMethod(ActivityManager.class, "getCurrentUser"));
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
