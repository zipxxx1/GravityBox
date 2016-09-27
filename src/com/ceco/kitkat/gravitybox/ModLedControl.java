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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ceco.kitkat.gravitybox.HeadsUpSnoozeDialog.HeadsUpSnoozeTimerSetListener;
import com.ceco.kitkat.gravitybox.ledcontrol.LedSettings;
import com.ceco.kitkat.gravitybox.ledcontrol.LedSettings.ActiveScreenMode;
import com.ceco.kitkat.gravitybox.ledcontrol.LedSettings.HeadsUpMode;
import com.ceco.kitkat.gravitybox.ledcontrol.QuietHours;
import com.ceco.kitkat.gravitybox.ledcontrol.QuietHoursActivity;
import com.ceco.kitkat.gravitybox.ledcontrol.LedSettings.LedMode;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModLedControl {
    private static final String TAG = "GB:ModLedControl";
    public static final boolean DEBUG = false;
    private static final String CLASS_USER_HANDLE = "android.os.UserHandle";
    private static final String CLASS_NOTIFICATION_MANAGER_SERVICE = "com.android.server.NotificationManagerService";
    private static final String CLASS_STATUSBAR_MGR_SERVICE = "com.android.server.StatusBarManagerService";
    private static final String CLASS_VIBRATOR_SERVICE = "com.android.server.VibratorService";
    private static final String CLASS_BASE_STATUSBAR = "com.android.systemui.statusbar.BaseStatusBar";
    private static final String CLASS_PHONE_STATUSBAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_STATUSBAR_NOTIFICATION = "android.service.notification.StatusBarNotification";
    private static final String CLASS_KG_TOUCH_DELEGATE = "com.android.systemui.statusbar.phone.KeyguardTouchDelegate";
    private static final String CLASS_NOTIF_DATA_ENTRY = "com.android.systemui.statusbar.NotificationData.Entry";
    private static final String CLASS_EXPAND_HELPER = "com.android.systemui.ExpandHelper";
    private static final String CLASS_HEADSUP_NOTIF_VIEW = "com.android.systemui.statusbar.policy.HeadsUpNotificationView";
    private static final String CLASS_STATUSBAR_ICON_VIEW = "com.android.systemui.statusbar.StatusBarIconView";
    private static final String PACKAGE_NAME_PHONE = "com.android.phone";
    public static final String PACKAGE_NAME_SYSTEMUI = "com.android.systemui";
    private static final int MISSED_CALL_NOTIF_ID = 1;

    private static final String NOTIF_EXTRA_HEADS_UP_MODE = "gbHeadsUpMode";
    private static final String NOTIF_EXTRA_HEADS_UP_EXPANDED = "gbHeadsUpExpanded";
    private static final String NOTIF_EXTRA_HEADS_UP_GRAVITY = "gbHeadsUpGravity";
    private static final String NOTIF_EXTRA_HEADS_UP_TIMEOUT = "gbHeadsUpTimeout";
    private static final String NOTIF_EXTRA_HEADS_UP_ALPHA = "gbHeadsUpAlpha";
    private static final String NOTIF_EXTRA_HEADS_UP_IGNORE_UPDATE = "gbHeadsUpIgnoreUpdate";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN_MODE = "gbActiveScreenMode";
    private static final String NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE = "gbActiveScreenPocketMode";
    public static final String NOTIF_EXTRA_PROGRESS_TRACKING = "gbProgressTracking";
    private static final int MSG_SHOW_HEADS_UP = 1026;
    private static final int MSG_HIDE_HEADS_UP = 1027;
    private static final int NAVIGATION_HINT_BACK_ALT = 1 << 0;

    public static final String ACTION_CLEAR_NOTIFICATIONS = "gravitybox.intent.action.CLEAR_NOTIFICATIONS";

    private static XSharedPreferences mPrefs;
    private static XSharedPreferences mQhPrefs;
    private static Notification mNotifOnNextScreenOff;
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

    private static BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mNotifOnNextScreenOff != null) {
                try {
                    NotificationManager nm = 
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(MISSED_CALL_NOTIF_ID, mNotifOnNextScreenOff);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
                mNotifOnNextScreenOff = null;
            }
            context.unregisterReceiver(this);
        }
    };

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
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notify",
                    String.class, int.class, Notification.class, notifyHookPkg);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notifyAsUser",
                    String.class, int.class, Notification.class, CLASS_USER_HANDLE, notifyHookPkg);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancel",
                    String.class, int.class, cancelHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancelAsUser",
                    String.class, int.class, CLASS_USER_HANDLE, cancelHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancelAll", cancelHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

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

            XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                    "enqueueNotificationInternal", String.class, String.class,
                        int.class, int.class, String.class, int.class, Notification.class, 
                        int[].class, int.class, notifyHook);

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

            XposedBridge.hookAllMethods(XposedHelpers.findClass(CLASS_VIBRATOR_SERVICE, null),
                    "startVibrationLocked", startVibrationHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static XC_MethodHook notifyHookPkg = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            // Phone missed calls: fix AOSP bug preventing LED from working for missed calls
            final Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
            final String pkgName = context.getPackageName();
            if (mNotifOnNextScreenOff == null && pkgName.equals(PACKAGE_NAME_PHONE) && 
                    (Integer)param.args[1] == MISSED_CALL_NOTIF_ID) {
                mNotifOnNextScreenOff = (Notification) param.args[2];
                context.registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
                if (DEBUG) log("Scheduled missed call notification for next screen off");
                return;
            }
        }
    };

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
                final boolean qhActiveIncludingVibe = qhActive && mQuietHours.muteVibe;
                final boolean qhActiveIncludingActiveScreen = qhActive &&
                        !mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_IGNORE_QUIET_HOURS, false);

                if (ls.getEnabled()) {
                    n.extras.putBoolean(NOTIF_EXTRA_PROGRESS_TRACKING, ls.getProgressTracking());
                }

                boolean isOngoing = ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0 || 
                        (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0);
                // additional check if old notification had a foreground service flag set since it seems not to be propagated
                // for updated notifications (until Notification gets processed by WorkerHandler which is too late for us)
                if (!isOngoing) {
                    try {
                        ArrayList<?> notifList = (ArrayList<?>) XposedHelpers.getObjectField(param.thisObject, "mNotificationList");
                        synchronized (notifList) {
                            int index = (Integer) XposedHelpers.callMethod(param.thisObject, "indexOfNotificationLocked",
                                    param.args[0], param.args[4], param.args[5], param.args[8]);
                            if (index >= 0) {
                                Object oldNotif = notifList.get(index);
                                if (oldNotif != null) {
                                    Notification oldN = (Notification) XposedHelpers.callMethod(oldNotif, "getNotification");
                                    isOngoing = (oldN.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
                                    if (DEBUG) log("Old notification foreground service check: isOngoing=" + isOngoing);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        /* yet another vendor messing with the internals... */
                        if (DEBUG) XposedBridge.log(t);
                    }
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
                    // heads up mode
                    n.extras.putString(NOTIF_EXTRA_HEADS_UP_MODE, ls.getHeadsUpMode().toString());
                    if (ls.getHeadsUpMode() != HeadsUpMode.OFF) {
                        n.extras.putBoolean(NOTIF_EXTRA_HEADS_UP_EXPANDED,
                                ls.getHeadsUpExpanded());
                        n.extras.putBoolean(NOTIF_EXTRA_HEADS_UP_IGNORE_UPDATE,
                                ls.getHeadsUpIgnoreUpdate());
                        n.extras.putInt(NOTIF_EXTRA_HEADS_UP_TIMEOUT,
                                ls.getHeadsUpTimeout());
                    }
                    // active screen mode
                    if (ls.getActiveScreenMode() != ActiveScreenMode.DISABLED && 
                            n.priority > Notification.PRIORITY_MIN &&
                            !qhActiveIncludingActiveScreen && !isOngoing && mPm != null && mKm.isKeyguardLocked()) {
                        n.extras.putString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE,
                                ls.getActiveScreenMode().toString());
                        n.extras.putBoolean(NOTIF_EXTRA_ACTIVE_SCREEN_POCKET_MODE, !mProximityWakeUpEnabled &&
                                mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_POCKET_MODE, true));
                        if (ls.getActiveScreenMode() == ActiveScreenMode.HEADS_UP) {
                            n.extras.putInt(NOTIF_EXTRA_HEADS_UP_GRAVITY, Integer.valueOf(
                                    mPrefs.getString(LedSettings.PREF_KEY_ACTIVE_SCREEN_HEADSUP_POSITION, "17")));
                            n.extras.putInt(NOTIF_EXTRA_HEADS_UP_TIMEOUT,
                                    mPrefs.getInt(LedSettings.PREF_KEY_ACTIVE_SCREEN_HEADSUP_TIMEOUT, 10));
                            n.extras.putFloat(NOTIF_EXTRA_HEADS_UP_ALPHA,
                                    (float)(100 - mPrefs.getInt(LedSettings.PREF_KEY_ACTIVE_SCREEN_HEADSUP_ALPHA,
                                            0)) / 100f);
                        }
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
                Notification n = (Notification) param.args[6];
                if (!n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE) || !(mPm != null && 
                        !mPm.isScreenOn() && mKm.isKeyguardLocked()))
                    return;

                final ActiveScreenMode asMode = ActiveScreenMode.valueOf(
                        n.extras.getString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE));
                final String pkgName = (String) param.args[0];
                if (DEBUG) log("Performing Active Screen for " + pkgName + " with mode " +
                        asMode.toString());

                mOnPanelRevealedBlocked = asMode == ActiveScreenMode.EXPAND_PANEL;
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

    private static XC_MethodHook cancelHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                final Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                final String pkgName = context.getPackageName();
                final boolean isMissedCallNotifOrAll = pkgName.equals(PACKAGE_NAME_PHONE) &&
                        (param.args.length == 0 || (Integer) param.args[1] == MISSED_CALL_NOTIF_ID);
                if (isMissedCallNotifOrAll && mNotifOnNextScreenOff != null) {
                    mNotifOnNextScreenOff = null;
                    context.unregisterReceiver(mScreenOffReceiver);
                    if (DEBUG) log("Pending missed call notification canceled");
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private static XC_MethodHook startVibrationHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            if (mQuietHours.quietHoursActive() && mQuietHours.muteSystemVibe) {
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
        if (mOnPanelRevealedBlocked) {
            mContext.sendBroadcast(new Intent(ModHwKeys.ACTION_EXPAND_NOTIFICATIONS));
        }
        long ident = Binder.clearCallingIdentity();
        try {
            mPm.wakeUp(SystemClock.uptimeMillis());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static void clearNotifications() {
        try {
            if (mNotifManagerService != null) {
                XposedHelpers.callMethod(mNotifManagerService, "cancelAll",
                        XposedHelpers.callStaticMethod(ActivityManager.class, "getCurrentUser"));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // SystemUI package
    private static WindowManager.LayoutParams mHeadsUpLp;
    private static long mHeadsUpClickTime;
    private static Handler mHeadsUpHandler;
    private static Runnable mHeadsUpHideRunnable;
    private static Object mStatusBar;
    private static HeadsUpParams mHeadsUpParams;
    private static HeadsUpSnoozeDialog mHeadsUpSnoozeDlg;
    private static ImageButton mHeadsUpSnoozeBtn;
    private static Map<String, Long> mHeadsUpSnoozeMap;
    private static XSharedPreferences mSysUiPrefs;

    static class HeadsUpParams {
        int yOffset;
        int gravity;
        float alpha;
    }

    private static BroadcastReceiver mSystemUiBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                try {
                    Object huNotifEntry = XposedHelpers.getObjectField(mStatusBar,
                            "mInterruptingNotificationEntry");
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
                    mHeadsUpSnoozeBtn = addSnoozeButton((ViewGroup) XposedHelpers.getObjectField(
                            param.thisObject, "mHeadsUpNotificationView"));
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "shouldInterrupt",
                    CLASS_STATUSBAR_NOTIFICATION, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // disable heads up if notification is for different user in multi-user environment
                    if (!(Boolean)XposedHelpers.callMethod(param.thisObject, "notificationIsForCurrentUser",
                            param.args[0])) {
                        if (DEBUG) log("HeadsUp: Notification is not for current user");
                        param.setResult(false);
                        return;
                    }

                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    Notification n = (Notification) XposedHelpers.getObjectField(param.args[0], "notification");
                    View headsUpView = (View) XposedHelpers.getObjectField(param.thisObject, "mHeadsUpNotificationView");
                    int statusBarWindowState = XposedHelpers.getIntField(param.thisObject, "mStatusBarWindowState");
                    String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "pkg");

                    boolean showHeadsUp = false;

                    // no heads up if snoozing
                    if (mSysUiPrefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_SNOOZE, false) &&
                            isHeadsUpSnoozing(pkgName)) {
                        if (DEBUG) log("Heads up currently snoozing for " + pkgName);
                        showHeadsUp = false;
                    // show expanded heads up for non-intrusive incoming call
                    } else if (isNonIntrusiveIncomingCallNotification(n) && isHeadsUpAllowed(context)) {
                        n.extras.putBoolean(NOTIF_EXTRA_HEADS_UP_EXPANDED, true);
                        showHeadsUp = true;
                    // show if active screen heads up mode set
                    } else if (n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE) &&
                            ActiveScreenMode.valueOf(n.extras.getString(NOTIF_EXTRA_ACTIVE_SCREEN_MODE)) ==
                                ActiveScreenMode.HEADS_UP) {
                        if (DEBUG) log("Showing active screen heads up");
                        showHeadsUp = true;
                    // disable when panels are disabled
                    } else if (!(Boolean) XposedHelpers.callMethod(param.thisObject, "panelsEnabled")) {
                        if (DEBUG) log("shouldInterrupt: NO due to panels being disabled");
                        showHeadsUp = false;
                    // explicitly disable for all ongoing notifications
                    } else if ((Boolean) XposedHelpers.callMethod(param.args[0], "isOngoing")) {
                        if (DEBUG) log("Disabling heads up for ongoing notification");
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
                                showHeadsUp = isHeadsUpAllowed(context,
                                        (Integer) XposedHelpers.callMethod(param.args[0], "getScore"));
                                break;
                            case ALWAYS: 
                                showHeadsUp = isHeadsUpAllowed(context);
                                break;
                            case OFF: 
                                showHeadsUp = false; 
                                break;
                            case IMMERSIVE:
                                showHeadsUp = isStatusBarHidden(statusBarWindowState) && isHeadsUpAllowed(context);
                                break;
                        }
                    }

                    if (showHeadsUp) {
                        if (mHeadsUpHandler != null) {
                            mHeadsUpHandler.removeCallbacks(mHeadsUpHideRunnable);
                        }
                        mHeadsUpParams.yOffset = isStatusBarHidden(statusBarWindowState) ? 0 :
                            (Integer) XposedHelpers.callMethod(param.thisObject, "getStatusBarHeight");
                        mHeadsUpParams.gravity = n.extras.getInt(NOTIF_EXTRA_HEADS_UP_GRAVITY,
                                Integer.valueOf(mSysUiPrefs.getString(
                                        GravityBoxSettings.PREF_KEY_HEADS_UP_POSITION, "48")));
                        mHeadsUpParams.alpha = n.extras.getFloat(NOTIF_EXTRA_HEADS_UP_ALPHA,
                                (float)(100 - mSysUiPrefs.getInt(GravityBoxSettings.PREF_KEY_HEADS_UP_ALPHA, 0)) / 100f);
                        maybeUpdateHeadsUpLayout(headsUpView);
                    }

                    param.setResult(showHeadsUp);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_NOTIF_DATA_ENTRY, classLoader, "setInterruption", 
                    XC_MethodReplacement.DO_NOTHING);

            XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUSBAR, classLoader, "resetHeadsUpDecayTimer",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object headsUpView = XposedHelpers.getObjectField(param.thisObject, "mHeadsUpNotificationView");
                    Object headsUp = XposedHelpers.getObjectField(headsUpView, "mHeadsUp");
                    Object sbNotif = XposedHelpers.getObjectField(headsUp, "notification");
                    Notification n = (Notification) XposedHelpers.getObjectField(sbNotif, "notification");
                    int timeout = n.extras.containsKey(NOTIF_EXTRA_HEADS_UP_TIMEOUT) ?
                            n.extras.getInt(NOTIF_EXTRA_HEADS_UP_TIMEOUT) * 1000 :
                                mSysUiPrefs.getInt(GravityBoxSettings.PREF_KEY_HEADS_UP_TIMEOUT, 5) * 1000;
                    XposedHelpers.setIntField(param.thisObject, "mHeadsUpNotificationDecay", timeout);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_EXPAND_HELPER, classLoader, "isInside",
                    View.class, float.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mSysUiPrefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_ONE_FINGER, false)) {
                        param.setResult(true);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_HEADSUP_NOTIF_VIEW, classLoader,
                    "setNotification", CLASS_NOTIF_DATA_ENTRY, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object sbNotif = XposedHelpers.getObjectField(param.args[0], "notification");
                    Notification n = (Notification) XposedHelpers.getObjectField(sbNotif, "notification");
                    final boolean expanded = n.extras.containsKey(NOTIF_EXTRA_HEADS_UP_EXPANDED) ?
                            n.extras.getBoolean(NOTIF_EXTRA_HEADS_UP_EXPANDED, false) :
                                mSysUiPrefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_EXPANDED, false);
                    Object headsUp = XposedHelpers.getObjectField(param.thisObject, "mHeadsUp");
                    Object row = XposedHelpers.getObjectField(headsUp, "row");
                    XposedHelpers.callMethod(row, "setExpanded", expanded);
                    if (mHeadsUpSnoozeBtn != null) {
                        if (mSysUiPrefs.getBoolean(GravityBoxSettings.PREF_KEY_HEADS_UP_SNOOZE, false)) {
                            String pkgName = (String) XposedHelpers.getObjectField(sbNotif, "pkg");
                            mHeadsUpSnoozeDlg.setPackageName(pkgName);
                            mHeadsUpSnoozeBtn.setVisibility(View.VISIBLE);
                        } else {
                            mHeadsUpSnoozeBtn.setVisibility(View.GONE);
                            resetHeadsUpSnoozeTimers();
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "updateNotification",
                    IBinder.class, CLASS_STATUSBAR_NOTIFICATION, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Notification n = (Notification ) XposedHelpers.getObjectField(param.args[1], "notification");
                    if (n.extras.getBoolean(NOTIF_EXTRA_HEADS_UP_IGNORE_UPDATE, false)) {
                        if (DEBUG) log("updateNotification: ignoring heads up for updated notification");
                        return;
                    }
                    if ((Boolean)XposedHelpers.callMethod(param.thisObject, "shouldInterrupt", param.args[1])) {
                        Constructor<?> c = XposedHelpers.findConstructorExact(CLASS_NOTIF_DATA_ENTRY,
                                classLoader, IBinder.class, CLASS_STATUSBAR_NOTIFICATION,
                                    CLASS_STATUSBAR_ICON_VIEW);
                        Object huView = XposedHelpers.getObjectField(param.thisObject, "mHeadsUpNotificationView");
                        Object entry = c.newInstance(param.args[0], param.args[1], null);
                        if ((Boolean) XposedHelpers.callMethod(param.thisObject, "inflateViews",
                                entry, XposedHelpers.callMethod(huView, "getHolder"))) {
                            Handler h = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
                            Object huNotifEntry = XposedHelpers.getObjectField(param.thisObject,
                                    "mInterruptingNotificationEntry");
                            if (huNotifEntry != null) {
                                h.removeMessages(MSG_HIDE_HEADS_UP);
                                XposedHelpers.callMethod(param.thisObject, "setHeadsUpVisibility", false);
                            }
                            XposedHelpers.setLongField(param.thisObject, "mInterruptingNotificationTime",
                                    System.currentTimeMillis());
                            XposedHelpers.setObjectField(param.thisObject, "mInterruptingNotificationEntry", entry);
                            XposedHelpers.callMethod(huView, "setNotification", entry);
                            h.sendEmptyMessage(MSG_SHOW_HEADS_UP);
                            XposedHelpers.callMethod(param.thisObject, "resetHeadsUpDecayTimer");
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_HEADSUP_NOTIF_VIEW, classLoader, "onInterceptTouchEvent",
                    MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    MotionEvent ev = (MotionEvent) param.args[0];
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        mHeadsUpClickTime = System.currentTimeMillis();
                    } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - mHeadsUpClickTime < 300) {
                            if (mHeadsUpHandler == null) {
                                mHeadsUpHandler = new Handler();
                                mHeadsUpHideRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        ((View)param.thisObject).setVisibility(View.GONE);
                                    }
                                };
                            }
                            mHeadsUpHandler.postDelayed(mHeadsUpHideRunnable, 200);
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "notifyHeadsUpScreenOn", 
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    Object huNotifEntry = XposedHelpers.getObjectField(param.thisObject,
                            "mInterruptingNotificationEntry");
                    if (huNotifEntry != null) {
                        Object sbNotif = XposedHelpers.getObjectField(huNotifEntry, "notification");
                        Notification n = (Notification) XposedHelpers.getObjectField(sbNotif, "notification");
                        if (n.extras.containsKey(NOTIF_EXTRA_ACTIVE_SCREEN_MODE)) {
                            param.setResult(null);
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUSBAR, classLoader, "setImeWindowStatus",
                    IBinder.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    View headsUpView = (View) XposedHelpers.getObjectField(
                            param.thisObject, "mHeadsUpNotificationView");
                    if (headsUpView != null) {
                        maybeUpdateHeadsUpLayout(headsUpView);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUSBAR, classLoader, "animateHeadsUp",
                    boolean.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    View headsUpView = (View) XposedHelpers.getObjectField(
                            param.thisObject, "mHeadsUpNotificationView");
                    if (headsUpView != null && mHeadsUpParams.alpha < 1f) {
                        float frac = (Float) param.args[1] / 0.4f;
                        frac = frac < 1f ? frac : 1f;
                        float alpha = 1f - frac;
                        alpha = alpha > mHeadsUpParams.alpha ? mHeadsUpParams.alpha : alpha;
                        float offset = XposedHelpers.getFloatField(param.thisObject, 
                                "mHeadsUpVerticalOffset") * frac;
                        offset = (Boolean) param.args[0] ? offset : 0f;
                        headsUpView.setAlpha(alpha);
                        headsUpView.setY(offset);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void maybeUpdateHeadsUpLayout(View headsUpView) {
        if (headsUpView == null || !headsUpView.isAttachedToWindow()) return;
        final Context context = headsUpView.getContext();

        if (mHeadsUpLp == null) {
            mHeadsUpLp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL, // above the status bar!
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                    PixelFormat.TRANSLUCENT);
            mHeadsUpLp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            mHeadsUpLp.gravity = -1;
            mHeadsUpLp.y = -1;
            mHeadsUpLp.setTitle("Heads Up");
            mHeadsUpLp.packageName = context.getPackageName();
            mHeadsUpLp.windowAnimations = context.getResources().getIdentifier(
                    "Animation.StatusBar.HeadsUp", "style", PACKAGE_NAME_SYSTEMUI);
        }

        final int gravity = isImeShowing() ? Gravity.TOP : mHeadsUpParams.gravity;
        final int yOffset = gravity != Gravity.TOP ? 0 : mHeadsUpParams.yOffset;
        if (headsUpView.getAlpha() >= 0.2f) {
            headsUpView.setAlpha(mHeadsUpParams.alpha);
        }

        final boolean layoutChanged = mHeadsUpLp.y != yOffset ||
                mHeadsUpLp.gravity != gravity;
        if (layoutChanged) {
            mHeadsUpLp.y = yOffset;
            mHeadsUpLp.gravity = gravity;
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(headsUpView, mHeadsUpLp);
        }
    }

    private static boolean isHeadsUpAllowed(Context context) {
        return isHeadsUpAllowed(context, 20);
    }

    private static boolean isHeadsUpAllowed(Context context, int score) {
        if (context == null) return false;

        if (score < Integer.valueOf(mSysUiPrefs.getString(
                GravityBoxSettings.PREF_KEY_HEADS_UP_IMPORTANCE, "-20"))) {
            if (DEBUG) log("isHeadsUpAllowed: no due to low importance level");
            return false;
        }

        Object kgTouchDelegate = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass(CLASS_KG_TOUCH_DELEGATE, context.getClassLoader()),
                "getInstance", context);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return (pm.isScreenOn() &&
                !(Boolean) XposedHelpers.callMethod(kgTouchDelegate, "isShowingAndNotHidden") &&
                !(Boolean) XposedHelpers.callMethod(kgTouchDelegate, "isInputRestricted") &&
                !shouldNotDisturb(context));
    }

    private static boolean isStatusBarHidden(int statusBarWindowState) {
        return (statusBarWindowState != 0);
    }

    private static boolean isImeShowing() {
        if (mStatusBar == null) return false;

        int iconHints = XposedHelpers.getIntField(mStatusBar, "mNavigationIconHints");
        return ((iconHints & NAVIGATION_HINT_BACK_ALT) == NAVIGATION_HINT_BACK_ALT);
    }

    private static boolean isNonIntrusiveIncomingCallNotification(Notification n) {
        return (n != null &&
                n.extras.getBoolean(ModDialer.NOTIF_EXTRA_NON_INTRUSIVE_CALL, false));
    }

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

    private static ImageButton addSnoozeButton(ViewGroup vg) throws Throwable {
        final Context context = vg.getContext();
        final Context gbContext = Utils.getGbContext(context);
        mHeadsUpSnoozeDlg = new HeadsUpSnoozeDialog(context, gbContext, mHeadsUpSnoozeTimerSetListener);
        mHeadsUpSnoozeMap = new HashMap<String, Long>();
        mHeadsUpSnoozeMap.put("[ALL]", 0l);

        ImageButton imgButton = new ImageButton(context);
        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40,
                vg.getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx, 
                Gravity.TOP | Gravity.END);
        lp.rightMargin = sizePx / 2;
        imgButton.setLayoutParams(lp);
        imgButton.setImageDrawable(gbContext.getResources().getDrawable(R.drawable.ic_heads_up_snooze));
        imgButton.setHapticFeedbackEnabled(true);
        imgButton.setVisibility(View.GONE);
        ((ViewGroup)vg.getChildAt(0)).addView(imgButton);
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
