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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ceco.lollipop.gravitybox.R;
import com.ceco.lollipop.gravitybox.HeadsUpSnoozeDialog.HeadsUpSnoozeTimerSetListener;
import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarState;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHours;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHoursActivity;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings.ActiveScreenMode;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings.HeadsUpMode;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings.LedMode;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings.Visibility;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings.VisibilityLs;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModLedControl {
    private static final String TAG = "GB:ModLedControl";
    public static final boolean DEBUG = false;
    private static final String CLASS_NOTIFICATION_MANAGER_SERVICE = "com.android.server.notification.NotificationManagerService";
    private static final String CLASS_VIBRATOR_SERVICE = "com.android.server.VibratorService";
    private static final String CLASS_BASE_STATUSBAR = "com.android.systemui.statusbar.BaseStatusBar";
    private static final String CLASS_PHONE_STATUSBAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_KG_TOUCH_DELEGATE = "com.android.systemui.statusbar.phone.KeyguardTouchDelegate";
    private static final String CLASS_NOTIF_DATA = "com.android.systemui.statusbar.NotificationData";
    private static final String CLASS_NOTIF_DATA_ENTRY = "com.android.systemui.statusbar.NotificationData.Entry";
    private static final String CLASS_HEADSUP_NOTIF_VIEW = "com.android.systemui.statusbar.policy.HeadsUpNotificationView";
    private static final String CLASS_NOTIFICATION_RECORD = "com.android.server.notification.NotificationRecord";
    public static final String PACKAGE_NAME_SYSTEMUI = "com.android.systemui";

    private static final String NOTIF_EXTRA_HEADS_UP_MODE = "gbHeadsUpMode";
    private static final String NOTIF_EXTRA_HEADS_UP_TIMEOUT = "gbHeadsUpTimeout";
    private static final String NOTIF_EXTRA_HEADS_UP_ALPHA = "gbHeadsUpAlpha";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN = "gbActiveScreen";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN_MODE = "gbActiveScreenMode";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE = "gbActiveScreenPocketMode";
    public static final String NOTIF_EXTRA_PROGRESS_TRACKING = "gbProgressTracking";
    private static final String NOTIF_EXTRA_PRIORITY_MODE = "gbPriorityMode";
    private static final int MSG_HIDE_HEADS_UP = 1029;
    public static final String NOTIF_EXTRA_VISIBILITY_LS = "gbVisibilityLs";
    public static final String NOTIF_EXTRA_HIDE_PERSISTENT = "gbHidePersistent";

    private  static final String SETTING_ZEN_MODE = "zen_mode";

    public static final String ACTION_CLEAR_NOTIFICATIONS = "gravitybox.intent.action.CLEAR_NOTIFICATIONS";

    private static XSharedPreferences mPrefs;
    private static XSharedPreferences mQhPrefs;
    private static Context mContext;
    private static PowerManager mPm;
    private static SensorManager mSm;
    private static KeyguardManager mKm;
    private static Sensor mProxSensor;
    private static QuietHours mQuietHours;
    private static Map<String, Long> mNotifTimestamps = new HashMap<String, Long>();
    private static boolean mUserPresent;
    private static Object mNotifManagerService;
    private static boolean mProximityWakeUpEnabled;
    private static boolean mScreenOnDueToActiveScreen;
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
                mScreenOnDueToActiveScreen = false;
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mUserPresent = false;
                mScreenOnDueToActiveScreen = false;
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

    public static void initAndroid(final XSharedPreferences mainPrefs, final ClassLoader classLoader) {
        mPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol");
        mPrefs.makeWorldReadable();
        mQhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
        mQhPrefs.makeWorldReadable();
        mQuietHours = new QuietHours(mQhPrefs);

        mProximityWakeUpEnabled = mainPrefs.getBoolean(GravityBoxSettings.PREF_KEY_POWER_PROXIMITY_WAKE, false);

        try {
            final Class<?> nmsClass = XposedHelpers.findClass(CLASS_NOTIFICATION_MANAGER_SERVICE, classLoader);
            XposedBridge.hookAllConstructors(nmsClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mNotifManagerService == null) {
                        mNotifManagerService = param.thisObject;
                        mContext = (Context) XposedHelpers.callMethod(param.thisObject, "getContext");

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

            XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, classLoader,
                    "enqueueNotificationInternal", String.class, String.class,
                    int.class, int.class, String.class, 
                    int.class, Notification.class, int[].class, int.class, notifyHook);

            XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, classLoader,
                    "applyZenModeLocked", CLASS_NOTIFICATION_RECORD, applyZenModeHook);

            XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, classLoader,
                    "updateLightsLocked", updateLightsLockedHook);

            XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, classLoader,
                    "buzzBeepBlinkLocked", CLASS_NOTIFICATION_RECORD, buzzBeepBlinkLockedHook);

            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLASS_VIBRATOR_SERVICE, classLoader),
                    "startVibrationLocked", startVibrationHook);

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

                Notification n = (Notification) param.args[6];
                if (n.extras.containsKey("gbIgnoreNotification")) return;

                Object oldRecord = getOldNotificationRecord(param.args[0], param.args[4],
                        param.args[5], param.args[8]);
                Notification oldN = getNotificationFromRecord(oldRecord);
                final String pkgName = (String) param.args[0];

                LedSettings ls = LedSettings.deserialize(mPrefs.getStringSet(pkgName, null));
                if (!ls.getEnabled()) {
                    // use default settings in case they are active
                    ls = LedSettings.deserialize(mPrefs.getStringSet("default", null));
                    if (!ls.getEnabled() && !mQuietHours.quietHoursActive(ls, n, mUserPresent)) {
                        return;
                    }
                }
                if (DEBUG) log(pkgName + ": " + ls.toString());

                final boolean qhActive = mQuietHours.quietHoursActive(ls, n, mUserPresent);
                final boolean qhActiveIncludingLed = qhActive && mQuietHours.muteLED;
                final boolean qhActiveIncludingVibe = qhActive && (
                        (mQuietHours.mode != QuietHours.Mode.WEAR && mQuietHours.muteVibe) ||
                        (mQuietHours.mode == QuietHours.Mode.WEAR && mUserPresent));
                final boolean qhActiveIncludingActiveScreen = qhActive &&
                        !mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_IGNORE_QUIET_HOURS, false);

                if (ls.getEnabled()) {
                    n.extras.putBoolean(NOTIF_EXTRA_PROGRESS_TRACKING, ls.getProgressTracking());
                    n.extras.putString(NOTIF_EXTRA_VISIBILITY_LS, ls.getVisibilityLs().toString());
                    n.extras.putBoolean(NOTIF_EXTRA_HIDE_PERSISTENT, ls.getHidePersistent());
                }

                // whether to ignore ongoing notification
                boolean isOngoing = ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0 || 
                        (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0);
                // additional check if old notification had a foreground service flag set since it seems not to be propagated
                // for updated notifications (until Notification gets processed by WorkerHandler which is too late for us)
                if (!isOngoing && oldN != null) {
                    isOngoing = (oldN.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
                    if (DEBUG) log("Old notification foreground service check: isOngoing=" + isOngoing);
                }

                if (isOngoing && !ls.getOngoing() && !qhActive) {
                    if (DEBUG) log("Ongoing led control disabled. Ignoring.");
                    return;
                }

                // lights
                if (qhActiveIncludingLed || 
                        (ls.getEnabled() && !(isOngoing && !ls.getOngoing()) &&
                            (ls.getLedMode() == LedMode.OFF ||
                             currentZenModeDisallowsLed(ls.getLedDnd()) ||
                             shouldIgnoreUpdatedNotificationLight(oldRecord, ls.getLedIgnoreUpdate())))) {
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
                    n.vibrate = null;
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
                            if (mNotifTimestamps.containsKey(pkgName)) {
                                long delta = System.currentTimeMillis() - mNotifTimestamps.get(pkgName);
                                if (delta > 500 &&  delta < ls.getSoundOnlyOnceTimeout()) {
                                    n.defaults &= ~Notification.DEFAULT_SOUND;
                                    n.defaults &= ~Notification.DEFAULT_VIBRATE;
                                    n.sound = null;
                                    n.vibrate = null;
                                    n.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
                                } else {
                                    mNotifTimestamps.put(pkgName, System.currentTimeMillis());
                                }
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
                    // heads up mode
                    n.extras.putString(NOTIF_EXTRA_HEADS_UP_MODE, ls.getHeadsUpMode().toString());
                    if (ls.getHeadsUpMode() != HeadsUpMode.OFF) {
                        n.extras.putInt(NOTIF_EXTRA_HEADS_UP_TIMEOUT,
                                ls.getHeadsUpTimeout());
                    }
                    // active screen mode
                    if (ls.getActiveScreenMode() != ActiveScreenMode.DISABLED && 
                            !(ls.getActiveScreenIgnoreUpdate() && oldN != null) &&
                            n.priority > Notification.PRIORITY_MIN &&
                            ls.getVisibilityLs() != VisibilityLs.CLEARABLE &&
                            ls.getVisibilityLs() != VisibilityLs.ALL &&
                            !qhActiveIncludingActiveScreen && !isOngoing &&
                            mPm != null && mKm.isKeyguardLocked()) {
                        n.extras.putBoolean(NOTIF_EXTRA_ACTIVE_SCREEN, true);
                        n.extras.putString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE,
                                ls.getActiveScreenMode().toString());
                    }
                    // visibility
                    if (ls.getVisibility() != Visibility.DEFAULT) {
                        n.visibility = ls.getVisibility().getValue();
                    }
                    // priority mode
                    n.extras.putBoolean(NOTIF_EXTRA_PRIORITY_MODE, ls.getPriorityMode());
                }

                if (DEBUG) log("Notification info: defaults=" + n.defaults + "; flags=" + n.flags);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private static Object getOldNotificationRecord(Object pkg, Object tag, Object id, Object userId) {
        Object oldNotifRecord = null;
        try {
            ArrayList<?> notifList = (ArrayList<?>) XposedHelpers.getObjectField(
                    mNotifManagerService, "mNotificationList");
            synchronized (notifList) {
                int index = (Integer) XposedHelpers.callMethod(
                        mNotifManagerService, "indexOfNotificationLocked",
                        pkg, tag, id, userId);
                if (index >= 0) {
                    oldNotifRecord = notifList.get(index);
                }
            }
        } catch (Throwable t) {
            log("Error in getOldNotificationRecord: " + t.getMessage());
            if (DEBUG) XposedBridge.log(t);
        }
        if (DEBUG) log("getOldNotificationRecord: has old record: " + (oldNotifRecord != null));
        return oldNotifRecord;
    }

    private static Notification getNotificationFromRecord(Object record) {
        Notification notif = null;
        if (record != null) {
            try {
                notif = (Notification) XposedHelpers.callMethod(record, "getNotification");
            } catch (Throwable t) {
                log("Error in getNotificationFromRecord: " + t.getMessage());
                if (DEBUG) XposedBridge.log(t);
            }
        }
        return notif;
    }

    private static boolean notificationRecordHasLight(Object record) {
        boolean hasLight = false;
        if (record != null) {
            try {
                String key = (String) XposedHelpers.callMethod(record, "getKey");
                List<?> lights = (List<?>) XposedHelpers.getObjectField(
                        mNotifManagerService, "mLights");
                hasLight = lights.contains(key);
            } catch (Throwable t) {
                log("Error in notificationRecordHasLight: " + t.getMessage());
                if (DEBUG) XposedBridge.log(t);
            }
        }
        if (DEBUG) log("notificationRecordHasLight: " + hasLight);
        return hasLight;
    }

    private static boolean shouldIgnoreUpdatedNotificationLight(Object record, boolean ignore) {
        boolean shouldIgnore = (ignore && record != null && !notificationRecordHasLight(record));
        if (DEBUG) log("shouldIgnoreUpdatedNotificationLight: " + shouldIgnore);
        return shouldIgnore;
    }

    private static XC_MethodHook applyZenModeHook = new XC_MethodHook() {
        @SuppressWarnings("deprecation")
        @Override
        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                Notification n = (Notification) XposedHelpers.callMethod(param.args[0], "getNotification");
                if (!n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN) ||
                        !n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE) ||
                        !(mPm != null && !mPm.isScreenOn() && mKm.isKeyguardLocked())) {
                    n.extras.remove(NOTIF_EXTRA_ACTIVE_SCREEN);
                    return;
                }
                n.extras.remove(NOTIF_EXTRA_ACTIVE_SCREEN);

                // check if intercepted by Zen
                if (!mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_IGNORE_QUIET_HOURS, false) &&
                        (boolean) XposedHelpers.callMethod(param.args[0], "isIntercepted")) {
                    if (DEBUG) log("Active screen: intercepted by Zen - ignoring");
                    n.extras.remove(NOTIF_EXTRA_ACTIVE_SCREEN_MODE);
                    return;
                }

                // set additional params
                final ActiveScreenMode asMode = ActiveScreenMode.valueOf(
                        n.extras.getString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE));
                n.extras.putBoolean(NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE, !mProximityWakeUpEnabled &&
                        mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_POCKET_MODE, true));
                if (asMode == ActiveScreenMode.HEADS_UP) {
                    n.extras.putInt(NOTIF_EXTRA_HEADS_UP_TIMEOUT,
                            mPrefs.getInt(LedSettings.PREF_KEY_ACTIVE_SCREEN_HEADSUP_TIMEOUT, 10));
                    n.extras.putFloat(NOTIF_EXTRA_HEADS_UP_ALPHA,
                            (float)(100 - mPrefs.getInt(LedSettings.PREF_KEY_ACTIVE_SCREEN_HEADSUP_ALPHA,
                                    0)) / 100f);
                }

                if (DEBUG) log("Performing Active Screen with mode " + asMode.toString());

                if (mSm != null && mProxSensor != null &&
                        n.extras.getBoolean(NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE)) {
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

    private static XC_MethodHook updateLightsLockedHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            if (mScreenOnDueToActiveScreen) {
                try {
                    XposedHelpers.setBooleanField(param.thisObject, "mScreenOn", false);
                    if (DEBUG) log("updateLightsLocked: Screen on due to active screen - pretending it's off");
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        }
    };

    private static XC_MethodHook buzzBeepBlinkLockedHook = new XC_MethodHook() {
        @SuppressWarnings("unchecked")
        @Override
        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                Notification n = (Notification) XposedHelpers.callMethod(param.args[0], "getNotification");
                if ((n.flags & Notification.FLAG_SHOW_LIGHTS) == 0 ||
                        !n.extras.getBoolean(NOTIF_EXTRA_PRIORITY_MODE, false)) return;

                int score = XposedHelpers.getIntField(param.args[0], "score");
                int interruptionThreshold = XposedHelpers.getStaticIntField(
                        param.thisObject.getClass(), "SCORE_INTERRUPTION_THRESHOLD");
                boolean wasIntercepted = (boolean) XposedHelpers.callMethod(param.args[0], "isIntercepted");
                if (score >= interruptionThreshold && wasIntercepted) {
                    if (DEBUG) log("buzzBeepBlinkLocked: forcing LED for priority mode notification");
                    ((List<String>) XposedHelpers.getObjectField(param.thisObject, "mLights"))
                        .add((String) XposedHelpers.callMethod(param.args[0], "getKey"));
                    XposedHelpers.callMethod(param.thisObject, "updateLightsLocked");
                    if (XposedHelpers.getBooleanField(param.thisObject, "mUseAttentionLight")) {
                        XposedHelpers.callMethod(XposedHelpers.getObjectField(
                                param.thisObject, "mAttentionLight"), "pulse");
                    }
                    ((Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler"))
                        .post((Runnable) XposedHelpers.getObjectField(param.thisObject, "mBuzzBeepBlinked"));
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private static XC_MethodHook startVibrationHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            if (mQuietHours.quietHoursActive() && (mQuietHours.muteSystemVibe ||
                    mQuietHours.mode == QuietHours.Mode.WEAR)) {
                if (DEBUG) log("startVibrationLocked: system level vibration suppressed");
                param.setResult(null);
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

    private static boolean currentZenModeDisallowsLed(String dnd) {
        if (dnd == null || dnd.isEmpty())
            return false;

        try {
            int zenMode = Settings.Global.getInt(mContext.getContentResolver(),
                    SETTING_ZEN_MODE, 0);
            List<String> dndList = Arrays.asList(dnd.split(","));
            return dndList.contains(Integer.toString(zenMode));
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

    private static void performActiveScreen() {
        long ident = Binder.clearCallingIdentity();
        try {
            XposedHelpers.callMethod(mPm, "wakeUp", SystemClock.uptimeMillis());
            mScreenOnDueToActiveScreen = true;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static void clearNotifications() {
        try {
            if (mNotifManagerService != null) {
                XposedHelpers.callMethod(mNotifManagerService, "cancelAllLocked",
                        android.os.Process.myUid(), android.os.Process.myPid(),
                        XposedHelpers.callStaticMethod(ActivityManager.class, "getCurrentUser"),
                        3, (Object)null, true);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // SystemUI package
    private static Object mStatusBar;
    private static HeadsUpParams mHeadsUpParams;
    private static HeadsUpSnoozeDialog mHeadsUpSnoozeDlg;
    private static ImageButton mHeadsUpSnoozeBtn;
    private static Map<String, Long> mHeadsUpSnoozeMap;
    private static XSharedPreferences mSysUiPrefs;

    static class HeadsUpParams {
        float alpha;
    }

    private static BroadcastReceiver mSystemUiBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                try {
                    View headsUpView = (View) XposedHelpers.getObjectField(
                            mStatusBar, "mHeadsUpNotificationView");
                    Object huNotifEntry = XposedHelpers.callMethod(headsUpView, "getEntry");
                    if (huNotifEntry != null) {
                        Handler h = (Handler) XposedHelpers.getObjectField(mStatusBar, "mHandler");
                        h.sendEmptyMessage(MSG_HIDE_HEADS_UP);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_HEADS_UP_SNOOZE_RESET)) {
                resetHeadsUpSnoozeTimers();
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_HEADS_UP_SETTINGS_CHANGED)) {
                mSysUiPrefs.reload();
            }
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(CLASS_NOTIF_DATA, classLoader),
                    "shouldFilterOut", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    StatusBarNotification sbn = (StatusBarNotification)param.args[0];
                    Notification n = sbn.getNotification();

                    // whether to hide persistent everywhere
                    if (!sbn.isClearable() && n.extras.getBoolean(NOTIF_EXTRA_HIDE_PERSISTENT)) {
                        param.setResult(true);
                        return;
                    }

                    // whether to hide during keyguard
                    if (ModStatusBar.getStatusBarState() != StatusBarState.SHADE) {
                        VisibilityLs vls = n.extras.containsKey(NOTIF_EXTRA_VISIBILITY_LS) ?
                                VisibilityLs.valueOf(n.extras.getString(NOTIF_EXTRA_VISIBILITY_LS)) :
                                    VisibilityLs.DEFAULT;
                        switch (vls) {
                            case CLEARABLE:
                                param.setResult(sbn.isClearable());
                                break;
                            case PERSISTENT:
                                param.setResult(!sbn.isClearable());
                                break;
                            case ALL:
                                param.setResult(true);
                                break;
                            case DEFAULT:
                            default: return;
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void initHeadsUp(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            mSysUiPrefs = prefs;
            mHeadsUpParams = new HeadsUpParams();
            mHeadsUpParams.alpha = (float)(100f - mSysUiPrefs.getInt(
                    GravityBoxSettings.PREF_KEY_HEADS_UP_ALPHA, 0)) / 100f;

            XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUSBAR, classLoader, "start", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(Boolean)XposedHelpers.getBooleanField(param.thisObject, "mUseHeadsUp")) {
                        XposedHelpers.setBooleanField(param.thisObject, "mUseHeadsUp", true);
                        XposedHelpers.callMethod(param.thisObject, "addHeadsUpView");
                    }
                    mStatusBar = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mStatusBar, "mContext");
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(Intent.ACTION_USER_PRESENT);
                    intentFilter.addAction(GravityBoxSettings.ACTION_HEADS_UP_SNOOZE_RESET);
                    intentFilter.addAction(GravityBoxSettings.ACTION_HEADS_UP_SETTINGS_CHANGED);
                    context.registerReceiver(mSystemUiBroadcastReceiver, intentFilter);
                    mHeadsUpSnoozeBtn = createSnoozeButton(context);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "shouldInterrupt",
                    StatusBarNotification.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // disable heads up if notification is for different user in multi-user environment
                    if (!(Boolean)XposedHelpers.callMethod(param.thisObject, "isNotificationForCurrentProfiles",
                            param.args[0])) {
                        if (DEBUG) log("HeadsUp: Notification is not for current user");
                        return;
                    }

                    StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    Notification n = sbn.getNotification();
                    int statusBarWindowState = XposedHelpers.getIntField(param.thisObject, "mStatusBarWindowState");
                    String pkgName = sbn.getPackageName();

                    boolean showHeadsUp = false;

                    // no heads up if snoozing
                    if (mSysUiPrefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_SNOOZE, false) &&
                            isHeadsUpSnoozing(pkgName)) {
                        if (DEBUG) log("Heads up currently snoozing for " + pkgName);
                        showHeadsUp = false; 
                    // show if active screen heads up mode set
                    } else if (n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE) &&
                            ActiveScreenMode.valueOf(n.extras.getString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE)) ==
                                ActiveScreenMode.HEADS_UP) {
                        if (DEBUG) log("Showing active screen heads up");
                        showHeadsUp = true;
                    // no heads up if app with DND enabled is in the foreground
                    } else if (shouldNotDisturb(context)) {
                        if (DEBUG) log("shouldInterrupt: NO due to DND app in the foreground");
                        showHeadsUp = false;
                    // disable when panels are disabled
                    } else if (!(Boolean) XposedHelpers.callMethod(param.thisObject, "panelsEnabled")) {
                        if (DEBUG) log("shouldInterrupt: NO due to panels being disabled");
                        showHeadsUp = false;
                    // get desired mode set by UNC or use default
                    } else {
                        HeadsUpMode mode = n.extras.containsKey(NOTIF_EXTRA_HEADS_UP_MODE) ?
                                HeadsUpMode.valueOf(n.extras.getString(NOTIF_EXTRA_HEADS_UP_MODE)) :
                                    HeadsUpMode.DEFAULT;
                        if (DEBUG) log("Heads up mode: " + mode.toString());
    
                        switch (mode) {
                            default:
                            case DEFAULT:
                                showHeadsUp = (Boolean) param.getResult();
                                break;
                            case ALWAYS: 
                                showHeadsUp = isHeadsUpAllowed(sbn, context);
                                break;
                            case OFF: 
                                showHeadsUp = false; 
                                break;
                            case IMMERSIVE:
                                showHeadsUp = isStatusBarHidden(statusBarWindowState) && isHeadsUpAllowed(sbn, context);
                                break;
                        }
                    }

                    if (showHeadsUp) {
                        mHeadsUpParams.alpha = n.extras.getFloat(NOTIF_EXTRA_HEADS_UP_ALPHA,
                                (float)(100 - mSysUiPrefs.getInt(GravityBoxSettings.PREF_KEY_HEADS_UP_ALPHA, 0)) / 100f);
                    }

                    param.setResult(showHeadsUp);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUSBAR, classLoader, "resetHeadsUpDecayTimer",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object headsUpView = XposedHelpers.getObjectField(param.thisObject, "mHeadsUpNotificationView");
                    Object headsUp = XposedHelpers.getObjectField(headsUpView, "mHeadsUp");
                    if (headsUp == null) return;
                    StatusBarNotification sbNotif = (StatusBarNotification)
                            XposedHelpers.getObjectField(headsUp, "notification");
                    Notification n = sbNotif.getNotification();
                    int timeout = n.extras.containsKey(NOTIF_EXTRA_HEADS_UP_TIMEOUT) ?
                            n.extras.getInt(NOTIF_EXTRA_HEADS_UP_TIMEOUT) * 1000 :
                                mSysUiPrefs.getInt(GravityBoxSettings.PREF_KEY_HEADS_UP_TIMEOUT, 5) * 1000;
                    XposedHelpers.setIntField(param.thisObject, "mHeadsUpNotificationDecay", timeout);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_HEADSUP_NOTIF_VIEW, classLoader,
                    "showNotification", CLASS_NOTIF_DATA_ENTRY, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // alpha
                    ViewGroup contentHolder = (ViewGroup) XposedHelpers.getObjectField(param.thisObject, "mContentHolder");
                    contentHolder.setAlpha(mHeadsUpParams.alpha);
                    Object swipeHelper = XposedHelpers.getObjectField(param.thisObject, "mSwipeHelper");
                    XposedHelpers.callMethod(swipeHelper, "setMaxSwipeProgress", mHeadsUpParams.alpha);
                    // snooze button
                    StatusBarNotification sbNotif = (StatusBarNotification)
                            XposedHelpers.getObjectField(param.args[0], "notification");
                    if (mHeadsUpSnoozeBtn != null) {
                        if (mSysUiPrefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_SNOOZE, false)) {
                            contentHolder.addView(mHeadsUpSnoozeBtn);
                            mHeadsUpSnoozeDlg.setPackageName(sbNotif.getPackageName());
                            mHeadsUpSnoozeBtn.setVisibility(View.VISIBLE);
                            if (DEBUG) log("Showing snooze button ");
                        } else {
                            mHeadsUpSnoozeBtn.setVisibility(View.GONE);
                            resetHeadsUpSnoozeTimers();
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "notifyHeadsUpScreenOn", 
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    View headsUpView = (View) XposedHelpers.getObjectField(
                            param.thisObject, "mHeadsUpNotificationView");
                    Object huNotifEntry = XposedHelpers.callMethod(headsUpView, "getEntry");
                    if (huNotifEntry != null) {
                        StatusBarNotification sbNotif = (StatusBarNotification)
                                XposedHelpers.getObjectField(huNotifEntry, "notification");
                        Notification n = sbNotif.getNotification();
                        if (n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE)) {
                            param.setResult(null);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean keyguardAllowsHeadsUp(Context context) {
        boolean isShowingAndNotOccluded;
        boolean isInputRestricted;
        if (Build.VERSION.SDK_INT < 22) {
            Object kgTouchDelegate = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass(CLASS_KG_TOUCH_DELEGATE, context.getClassLoader()),
                    "getInstance", context);
            isShowingAndNotOccluded = (boolean) XposedHelpers.callMethod(
                    kgTouchDelegate, "isShowingAndNotOccluded");
            isInputRestricted = (boolean) XposedHelpers.callMethod(
                    kgTouchDelegate, "isInputRestricted");
        } else {
            Object kgViewManager = XposedHelpers.getObjectField(mStatusBar, "mStatusBarKeyguardViewManager");
            isShowingAndNotOccluded = ((boolean)XposedHelpers.callMethod(kgViewManager, "isShowing") &&
                    !(boolean)XposedHelpers.callMethod(kgViewManager, "isOccluded"));
            isInputRestricted = (boolean)XposedHelpers.callMethod(kgViewManager, "isInputRestricted");
        }
        return (!isShowingAndNotOccluded && !isInputRestricted);
    }

    @SuppressWarnings("deprecation")
    private static boolean isHeadsUpAllowed(StatusBarNotification sbn, Context context) {
        if (context == null) return false;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        Notification n = sbn.getNotification();
        return (pm.isScreenOn() &&
                keyguardAllowsHeadsUp(context) &&
                (!sbn.isOngoing() || n.fullScreenIntent != null || (n.extras.getInt("headsup", 0) != 0)));
    }

    private static boolean isStatusBarHidden(int statusBarWindowState) {
        return (statusBarWindowState != 0);
    }

    @SuppressWarnings("deprecation")
    private static String getTopLevelPackageName(Context context) {
        try {
            final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            ComponentName cn = taskInfo.get(0).topActivity;
            return cn.getPackageName();
        } catch (Throwable t) {
            log("Error getting top level package: " + t.getMessage());
            return null;
        }
    }

    private static boolean shouldNotDisturb(Context context) {
        String pkgName = getTopLevelPackageName(context);
        final XSharedPreferences uncPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol");
        if(!uncPrefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false) && pkgName != null) {
            LedSettings ls = LedSettings.deserialize(uncPrefs.getStringSet(pkgName, null));
            return (ls.getEnabled() && ls.getHeadsUpDnd());
        } else {
            return false;
        }
    }

    private static ImageButton createSnoozeButton(final Context context) throws Throwable {
        final Context gbContext = Utils.getGbContext(context);
        mHeadsUpSnoozeDlg = new HeadsUpSnoozeDialog(context, gbContext, mHeadsUpSnoozeTimerSetListener);
        mHeadsUpSnoozeMap = new HashMap<String, Long>();
        mHeadsUpSnoozeMap.put("[ALL]", 0l);

        ImageButton imgButton = new ImageButton(context);
        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40,
                context.getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx, 
                Gravity.BOTTOM | Gravity.END);
        imgButton.setLayoutParams(lp);
        imgButton.setImageDrawable(gbContext.getResources().getDrawable(R.drawable.ic_heads_up_snooze));
        imgButton.setHapticFeedbackEnabled(true);
        imgButton.setVisibility(View.GONE);
        imgButton.setElevation(30);
        imgButton.setAlpha(0.5f);
        imgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pkgName = mHeadsUpSnoozeDlg.getPackageName();
                if (pkgName != null) {
                    long millis = mSysUiPrefs.getInt(GravityBoxSettings.PREF_KEY_HEADS_UP_SNOOZE_TIMER, 60) * 60000;
                    mHeadsUpSnoozeTimerSetListener.onHeadsUpSnoozeTimerSet(pkgName, millis);
                    Toast.makeText(context, String.format(gbContext.getString(R.string.headsup_snooze_timer_set),
                            mHeadsUpSnoozeDlg.getAppName()), Toast.LENGTH_SHORT).show();
                }
            }
        });
        imgButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mHeadsUpSnoozeDlg.show();
                return true;
            }
        });
        if (DEBUG) log("Snooze button created");
        return imgButton;
    }

    private static HeadsUpSnoozeTimerSetListener mHeadsUpSnoozeTimerSetListener = 
            new HeadsUpSnoozeTimerSetListener() {
        @Override
        public void onHeadsUpSnoozeTimerSet(String pkgName, long millis) {
            synchronized (mHeadsUpSnoozeMap) {
                if (DEBUG) log("onHeadsUpSnoozeTimerSet: pkgName:"+pkgName+"; millis:"+millis);
                String key = pkgName == null ? "[ALL]" : pkgName;
                mHeadsUpSnoozeMap.put(key, System.currentTimeMillis()+millis);
            }
        }
    };

    private static boolean isHeadsUpSnoozing(String pkgName) {
        if (mHeadsUpSnoozeMap == null) return false;
        synchronized (mHeadsUpSnoozeMap) {
            final long currentTime = System.currentTimeMillis();
            final long pkgSnoozeUntil = mHeadsUpSnoozeMap.containsKey(pkgName) ?
                    mHeadsUpSnoozeMap.get(pkgName) : 0;
            final long allSnoozeUntil = mHeadsUpSnoozeMap.get("[ALL]");
            return (pkgSnoozeUntil > currentTime || allSnoozeUntil > currentTime);
        }
    }

    private static void resetHeadsUpSnoozeTimers() {
        if (mHeadsUpSnoozeMap == null) return;
        synchronized (mHeadsUpSnoozeMap) {
            for (Map.Entry<String, Long> entry : mHeadsUpSnoozeMap.entrySet()) {
                entry.setValue(0l);
            }
        }
    }
}
