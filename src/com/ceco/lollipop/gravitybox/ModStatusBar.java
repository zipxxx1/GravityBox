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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.ceco.lollipop.gravitybox.R;
import com.ceco.lollipop.gravitybox.TrafficMeterAbstract.TrafficMeterMode;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.database.ContentObserver;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.service.notification.NotificationListenerService.RankingMap;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ModStatusBar {
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String TAG = "GB:ModStatusBar";
    private static final String CLASS_BASE_STATUSBAR = "com.android.systemui.statusbar.BaseStatusBar";
    private static final String CLASS_PHONE_STATUSBAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_TICKER = "com.android.systemui.statusbar.phone.PhoneStatusBar$MyTicker";
    private static final String CLASS_PHONE_STATUSBAR_POLICY = "com.android.systemui.statusbar.phone.PhoneStatusBarPolicy";
    private static final String CLASS_POWER_MANAGER = "android.os.PowerManager";
    private static final String CLASS_PLUGINFACTORY = Utils.hasLenovoVibeUI() ?
            "com.android.systemui.lenovo.ext.PluginFactory" :
            "com.mediatek.systemui.ext.PluginFactory";
    private static final String CLASS_NETWORKTYPE = Utils.hasLenovoVibeUI() ?
            "com.android.systemui.lenovo.ext.NetworkType" :
            "com.mediatek.systemui.ext.NetworkType";
    private static final String CLASS_EXPANDABLE_NOTIF_ROW = "com.android.systemui.statusbar.ExpandableNotificationRow";
    private static final String CLASS_PHONE_STATUSBAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String CLASS_ICON_MERGER = "com.android.systemui.statusbar.phone.IconMerger";
    private static final String CLASS_SAVE_IMG_TASK = "com.android.systemui.screenshot.SaveImageInBackgroundTask";
    private static final boolean DEBUG = false;

    private static final float BRIGHTNESS_CONTROL_PADDING = 0.15f;
    private static final int BRIGHTNESS_CONTROL_LONG_PRESS_TIMEOUT = 750; // ms
    private static final int BRIGHTNESS_CONTROL_LINGER_THRESHOLD = 20;
    private static final int STATUS_BAR_DISABLE_EXPAND = 0x00010000;
    public static final String SETTING_ONGOING_NOTIFICATIONS = "gb_ongoing_notifications";

    public static final String ACTION_START_SEARCH_ASSIST = "gravitybox.intent.action.START_SEARCH_ASSIST";

    private static final String ACTION_DELETE_SCREENSHOT = "com.android.systemui.DELETE_SCREENSHOT";
    private static final String SCREENSHOT_URI = "com.android.systemui.SCREENSHOT_URI";
    private static final int SCREENSHOT_NOTIFICATION_ID = 789;

    private static enum TickerPolicy { DEFAULT, LOCKED, SECURED, DISABLED };

    private static ViewGroup mIconArea;
    private static LinearLayout mLayoutCenter;
    private static StatusbarClock mClock;
    private static Object mPhoneStatusBar;
    private static ViewGroup mStatusBarView;
    private static Context mContext;
    private static int mAnimPushUpOut;
    private static int mAnimPushDownIn;
    private static int mAnimFadeIn;
    private static boolean mClockCentered = false;
    private static String mClockLink;
    private static boolean mAlarmHide = false;
    private static Object mPhoneStatusBarPolicy;
    private static SettingsObserver mSettingsObserver;
    private static String mOngoingNotif;
    private static TrafficMeterAbstract mTrafficMeter;
    private static TrafficMeterMode mTrafficMeterMode = TrafficMeterMode.OFF;
    private static ViewGroup mSbContents;
    private static boolean mClockInSbContents = false;
    private static boolean mDisableDataNetworkTypeIcons = false;
    private static Object mStatusBarPlugin;
    private static Object mGetDataNetworkTypeIconGeminiHook;
    private static boolean mNotifExpandAll;
    private static boolean mDt2sEnabled;
    private static GestureDetector mDoubletapGesture;
    private static View mIconMergerView;
    private static String mClockLongpressLink;
    private static XSharedPreferences mPrefs;
    private static int mDeleteIconId;
    private static StatusbarDownloadProgressView mDownloadProgressView;
    private static TickerPolicy mTickerPolicy;

    // Brightness control
    private static boolean mBrightnessControlEnabled;
    private static boolean mBrightnessControl;
    private static float mScreenWidth;
    private static int mMinBrightness;
    private static int mPeekHeight;
    private static boolean mJustPeeked;
    private static int mLinger;
    private static int mInitialTouchX;
    private static int mInitialTouchY;
    private static int BRIGHTNESS_ON = 255;

    private static List<BroadcastSubReceiver> mBroadcastSubReceivers = new ArrayList<BroadcastSubReceiver>();

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());

            for (BroadcastSubReceiver bsr : mBroadcastSubReceivers) {
                bsr.onBroadcastReceived(context, intent);
            }
            ModBatteryStyle.onBroadcastReceived(context, intent);

            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_CLOCK_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_CENTER_CLOCK)) {
                    setClockPosition(intent.getBooleanExtra(GravityBoxSettings.EXTRA_CENTER_CLOCK, false));
                    updateTrafficMeterPosition();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_LINK)) {
                    mClockLink = intent.getStringExtra(GravityBoxSettings.EXTRA_CLOCK_LINK);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_CLOCK_LONGPRESS_LINK)) {
                    mClockLongpressLink = intent.getStringExtra(GravityBoxSettings.EXTRA_CLOCK_LONGPRESS_LINK);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_ALARM_HIDE)) {
                    mAlarmHide = intent.getBooleanExtra(GravityBoxSettings.EXTRA_ALARM_HIDE, false);
                    if (mPhoneStatusBarPolicy != null) {
                        XposedHelpers.callMethod(mPhoneStatusBarPolicy, "updateAlarm");
                    }
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_DISABLE_DATA_NETWORK_TYPE_ICONS_CHANGED)
                    && intent.hasExtra(GravityBoxSettings.EXTRA_DATA_NETWORK_TYPE_ICONS_DISABLED)) {
                mDisableDataNetworkTypeIcons = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_DATA_NETWORK_TYPE_ICONS_DISABLED, false);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_BRIGHTNESS_CHANGED)
                    && intent.hasExtra(GravityBoxSettings.EXTRA_SB_BRIGHTNESS)) {
                mBrightnessControlEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_SB_BRIGHTNESS, false);
                if (mSettingsObserver != null) {
                    mSettingsObserver.update();
                }
            } else if (intent.getAction().equals(
                    GravityBoxSettings.ACTION_PREF_ONGOING_NOTIFICATIONS_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_ONGOING_NOTIF)) {
                    mOngoingNotif = intent.getStringExtra(GravityBoxSettings.EXTRA_ONGOING_NOTIF);
                    if (DEBUG) log("mOngoingNotif = " + mOngoingNotif);
                } else if (intent.hasExtra(GravityBoxSettings.EXTRA_ONGOING_NOTIF_RESET)) {
                    mOngoingNotif = "";
                    Settings.Secure.putString(mContext.getContentResolver(),
                            SETTING_ONGOING_NOTIFICATIONS, "");
                    if (DEBUG) log("Ongoing notifications list reset");
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_DATA_TRAFFIC_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_MODE)) {
                    try {
                        TrafficMeterMode mode = TrafficMeterMode.valueOf(
                            intent.getStringExtra(GravityBoxSettings.EXTRA_DT_MODE));
                        setTrafficMeterMode(mode);
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_DT_POSITION)) {
                    updateTrafficMeterPosition();
                }
            } else if (intent.getAction().equals(ACTION_START_SEARCH_ASSIST)) {
                startSearchAssist();
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_NOTIF_EXPAND_ALL_CHANGED) &&
                    intent.hasExtra(GravityBoxSettings.EXTRA_NOTIF_EXPAND_ALL)) {
                mNotifExpandAll = intent.getBooleanExtra(GravityBoxSettings.EXTRA_NOTIF_EXPAND_ALL, false);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_DT2S_CHANGED) &&
                    intent.hasExtra(GravityBoxSettings.EXTRA_SB_DT2S)) {
                mDt2sEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_SB_DT2S, false);
            } else if (intent.getAction().equals(ACTION_DELETE_SCREENSHOT)) {
                Uri screenshotUri = Uri.parse(intent.getStringExtra(SCREENSHOT_URI));
                if (screenshotUri != null) {
                    mContext.getContentResolver().delete(screenshotUri, null, null);
                }
                NotificationManager notificationManager =
                        (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(SCREENSHOT_NOTIFICATION_ID);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_TICKER_POLICY_CHANGED)) {
                mTickerPolicy = TickerPolicy.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_TICKER_POLICY));
            }
        }
    };

    static class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_BRIGHTNESS_MODE), false, this);
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int brightnessValue = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            mBrightnessControl = brightnessValue != Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    && mBrightnessControlEnabled;
        }
    }

    public static void initResources(final XSharedPreferences prefs, final InitPackageResourcesParam resparam) {
        try {
            StatusbarSignalCluster.initResources(prefs, resparam);

            XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);
            mDeleteIconId = XResources.getFakeResId(modRes, R.drawable.ic_menu_delete);
            resparam.res.setReplacement(mDeleteIconId, modRes.fwd(R.drawable.ic_menu_delete));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void prepareLayout() {
        try {
            Resources res = mContext.getResources();

            // inject new center layout container
            mLayoutCenter = new LinearLayout(mContext);
            mLayoutCenter.setLayoutParams(new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            mLayoutCenter.setGravity(Gravity.CENTER);
            mStatusBarView.addView(mLayoutCenter);
            if (DEBUG) log("mLayoutCenter injected");

            // inject download progress view
            mDownloadProgressView = new StatusbarDownloadProgressView(mContext, mPrefs);
            mStatusBarView.addView(mDownloadProgressView);
            mBroadcastSubReceivers.add(mDownloadProgressView);

            // inject battery bar view
            BatteryBarView bbView = new BatteryBarView(mContext, mPrefs);
            mStatusBarView.addView(bbView);
            mBroadcastSubReceivers.add(bbView);
            mDownloadProgressView.registerListener(bbView);

            mIconArea = (ViewGroup) XposedHelpers.getObjectField(mPhoneStatusBar, "mSystemIconArea");

            // inject Quiet Hours view
            if (SysUiManagers.QuietHoursManager != null) {
                StatusbarQuietHoursView qhv = new StatusbarQuietHoursView(mContext);
                mIconArea.addView(qhv, 0);
            }

            mSbContents = (ViewGroup) XposedHelpers.getObjectField(mPhoneStatusBar, "mStatusBarContents");

            if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_MASTER_SWITCH, true)) {
                // find statusbar clock
                TextView clock = (TextView) mIconArea.findViewById(
                        res.getIdentifier("clock", "id", PACKAGE_NAME));
                // the second attempt
                if (clock == null && mSbContents != null) {
                    clock = (TextView) mSbContents.findViewById(
                            res.getIdentifier("clock", "id", PACKAGE_NAME));
                    mClockInSbContents = clock != null;
                }
                if (clock != null) {
                    mClock = new StatusbarClock(mPrefs);
                    mClock.setClock(clock);
                    if (SysUiManagers.IconManager != null) {
                        SysUiManagers.IconManager.registerListener(mClock);
                    }
                    mBroadcastSubReceivers.add(mClock);
                }
                setClockPosition(mPrefs.getBoolean(
                        GravityBoxSettings.PREF_KEY_STATUSBAR_CENTER_CLOCK, false));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void prepareHeaderTimeView() {
        try {
            Object header = XposedHelpers.getObjectField(mPhoneStatusBar, "mHeader");
            View timeView = (View) XposedHelpers.getObjectField(header, "mTime");
            if (timeView != null) {
                timeView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchClockAction(mClockLink);
                    }
                });
                timeView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        launchClockAction(mClockLongpressLink);
                        return true;
                    }
                });
            }
        } catch (Throwable t) {
            log("Error setting long-press handler on mTime: " + t.getMessage());
        }
    }

    private static void prepareBrightnessControl() {
        try {
            Class<?> powerManagerClass = XposedHelpers.findClass(CLASS_POWER_MANAGER,
                    mContext.getClassLoader());
            Resources res = mContext.getResources();
            mScreenWidth = (float) res.getDisplayMetrics().widthPixels;
            mMinBrightness = res.getInteger(res.getIdentifier(
                    "config_screenBrightnessSettingMinimum", "integer", "android"));
            mPeekHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 84,
                    res.getDisplayMetrics());
            BRIGHTNESS_ON = XposedHelpers.getStaticIntField(powerManagerClass, "BRIGHTNESS_ON");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void prepareTrafficMeter() {
        try {
            TrafficMeterMode mode = TrafficMeterMode.valueOf(
                    mPrefs.getString(GravityBoxSettings.PREF_KEY_DATA_TRAFFIC_MODE, "OFF"));
            setTrafficMeterMode(mode);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            mPrefs = prefs;

            final Class<?> phoneStatusBarClass =
                    XposedHelpers.findClass(CLASS_PHONE_STATUSBAR, classLoader);
            final Class<?> tickerClass =
                    XposedHelpers.findClass(CLASS_TICKER, classLoader);
            final Class<?> phoneStatusBarPolicyClass = 
                    XposedHelpers.findClass(CLASS_PHONE_STATUSBAR_POLICY, classLoader);
            Class<?> expandableNotifRowClass = null;
            if (!Utils.hasLenovoVibeUI()) {
                expandableNotifRowClass = XposedHelpers.findClass(CLASS_EXPANDABLE_NOTIF_ROW, classLoader);
            }
            final Class<?> phoneStatusbarViewClass = XposedHelpers.findClass(CLASS_PHONE_STATUSBAR_VIEW, classLoader);

            final Class<?>[] loadAnimParamArgs = new Class<?>[2];
            loadAnimParamArgs[0] = int.class;
            loadAnimParamArgs[1] = Animation.AnimationListener.class;

            mAlarmHide = prefs.getBoolean(GravityBoxSettings.PREF_KEY_ALARM_ICON_HIDE, false);
            mClockLink = prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_LINK, null);
            mClockLongpressLink = prefs.getString(
                    GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_LONGPRESS_LINK, null);
            mBrightnessControlEnabled = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_STATUSBAR_BRIGHTNESS, false);
            mOngoingNotif = prefs.getString(GravityBoxSettings.PREF_KEY_ONGOING_NOTIFICATIONS, "");
            mNotifExpandAll = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NOTIF_EXPAND_ALL, false);
            mDt2sEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DT2S, false);
            mTickerPolicy = TickerPolicy.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_STATUSBAR_TICKER_POLICY, "DEFAULT"));

            XposedBridge.hookAllConstructors(phoneStatusBarPolicyClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mPhoneStatusBarPolicy = param.thisObject;
                }
            });

            XposedHelpers.findAndHookMethod(phoneStatusBarPolicyClass, "updateAlarm", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object sbService = XposedHelpers.getObjectField(param.thisObject, "mService");
                    if (sbService != null) {
                        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                        boolean alarmSet = (alarmManager.getNextAlarmClock() != null);
                        XposedHelpers.callMethod(sbService, "setIconVisibility", "alarm_clock",
                                (alarmSet && !mAlarmHide));
                    }
                }
            });

            XposedHelpers.findAndHookMethod(phoneStatusBarClass, "makeStatusBarView", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mPhoneStatusBar = param.thisObject;
                    mStatusBarView = (ViewGroup) XposedHelpers.getObjectField(mPhoneStatusBar, "mStatusBarView");
                    mContext = (Context) XposedHelpers.getObjectField(mPhoneStatusBar, "mContext");
                    Resources res = mContext.getResources();
                    mAnimPushUpOut = res.getIdentifier("push_up_out", "anim", "android");
                    mAnimPushDownIn = res.getIdentifier("push_down_in", "anim", "android");
                    mAnimFadeIn = res.getIdentifier("fade_in", "anim", "android");

                    prepareLayout();
                    prepareHeaderTimeView();
                    prepareBrightnessControl();
                    prepareTrafficMeter();

                    ModBatteryStyle.init(mStatusBarView, prefs);

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_CLOCK_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_STATUSBAR_BRIGHTNESS_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_ONGOING_NOTIFICATIONS_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_DATA_TRAFFIC_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_DISABLE_DATA_NETWORK_TYPE_ICONS_CHANGED);
                    intentFilter.addAction(ACTION_START_SEARCH_ASSIST);
                    intentFilter.addAction(GravityBoxSettings.ACTION_NOTIF_EXPAND_ALL_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_STATUSBAR_DT2S_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_STATUSBAR_BT_VISIBILITY_CHANGED);
                    intentFilter.addAction(ACTION_DELETE_SCREENSHOT);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_STATUSBAR_DOWNLOAD_PROGRESS_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_STATUSBAR_TICKER_POLICY_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_BAR_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_STYLE_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_SIZE_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_STYLE_CHANGED);
                    if (Utils.isMtkDevice()) {
                        intentFilter.addAction(ModBatteryStyle.ACTION_MTK_BATTERY_PERCENTAGE_SWITCH);
                    }

                    mContext.registerReceiver(mBroadcastReceiver, intentFilter);

                    mSettingsObserver = new SettingsObserver(
                            (Handler) XposedHelpers.getObjectField(mPhoneStatusBar, "mHandler"));
                    mSettingsObserver.observe();
                }
            });

            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_CLOCK_MASTER_SWITCH, true)) {
                XposedHelpers.findAndHookMethod(phoneStatusBarClass, "showClock", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (mClock != null) {
                            mClock.setClockVisibility((Boolean)param.args[0]);
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(tickerClass, "tickerStarting", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mLayoutCenter == null) return;

                    mLayoutCenter.setVisibility(View.GONE);
                    Animation anim = (Animation) XposedHelpers.callMethod(
                            mPhoneStatusBar, "loadAnim", loadAnimParamArgs, mAnimPushUpOut, null);
                    mLayoutCenter.startAnimation(anim);
                }
            });

            XposedHelpers.findAndHookMethod(tickerClass, "tickerDone", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mLayoutCenter == null) return;

                    mLayoutCenter.setVisibility(View.VISIBLE);
                    Animation anim = (Animation) XposedHelpers.callMethod(
                            mPhoneStatusBar, "loadAnim", loadAnimParamArgs, mAnimPushDownIn, null);
                    mLayoutCenter.startAnimation(anim);
                }
            });

            XposedHelpers.findAndHookMethod(tickerClass, "tickerHalting", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mLayoutCenter == null) return;

                    mLayoutCenter.setVisibility(View.VISIBLE);
                    Animation anim = (Animation) XposedHelpers.callMethod(
                            mPhoneStatusBar, "loadAnim", loadAnimParamArgs, mAnimFadeIn, null);
                    mLayoutCenter.startAnimation(anim);
                }
            });

            XposedHelpers.findAndHookMethod(phoneStatusBarClass, 
                    "interceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mBrightnessControl) return;

                    brightnessControl((MotionEvent) param.args[0]);
                    if ((XposedHelpers.getIntField(param.thisObject, "mDisabled")
                            & STATUS_BAR_DISABLE_EXPAND) != 0) {
                        param.setResult(true);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(phoneStatusBarClass, "addNotification", 
                    StatusBarNotification.class, RankingMap.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final StatusBarNotification notif = (StatusBarNotification) param.args[0];
                    final String pkg = notif.getPackageName();
                    final boolean ongoing = notif.isOngoing();
                    final int id = notif.getId();
                    final Notification n = notif.getNotification();
                    if (DEBUG) log ("addNotificationViews: pkg=" + pkg + "; id=" + id + 
                                    "; iconId=" + n.icon + "; ongoing=" + ongoing);

                    if (!ongoing) return;

                    // store if new
                    final String notifData = pkg + "," + n.icon;
                    final ContentResolver cr = mContext.getContentResolver();
                    String storedNotifs = Settings.Secure.getString(cr,
                            SETTING_ONGOING_NOTIFICATIONS);
                    if (storedNotifs == null || !storedNotifs.contains(notifData)) {
                        if (storedNotifs == null || storedNotifs.isEmpty()) {
                            storedNotifs = notifData;
                        } else {
                            storedNotifs += "#C3C0#" + notifData;
                        }
                        if (DEBUG) log("New storedNotifs = " + storedNotifs);
                        Settings.Secure.putString(cr, SETTING_ONGOING_NOTIFICATIONS, storedNotifs);
                    }

                    // block if requested
                    if (mOngoingNotif.contains(notifData)) {
                        param.setResult(null);
                        if (DEBUG) log("Ongoing notification " + notifData + " blocked.");
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mDownloadProgressView != null) {
                        mDownloadProgressView.onNotificationAdded((StatusBarNotification)param.args[0]);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "updateNotification", 
                    StatusBarNotification.class, RankingMap.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mDownloadProgressView != null) {
                        mDownloadProgressView.onNotificationUpdated((StatusBarNotification)param.args[0]);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_BASE_STATUSBAR, classLoader, "removeNotificationViews",
                    String.class, RankingMap.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mDownloadProgressView != null) {
                        mDownloadProgressView.onNotificationRemoved((StatusBarNotification)param.getResult());
                    }
                }
            });

            if (!Utils.hasLenovoVibeUI()) {
                XposedHelpers.findAndHookMethod(expandableNotifRowClass, "isUserExpanded", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (mNotifExpandAll) {
                            param.setResult(true);
                        }
                    }
                });
            }

            XposedBridge.hookAllConstructors(phoneStatusbarViewClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final Context context = (Context) param.args[0];
                    if (context == null) return;

                    mDoubletapGesture = new GestureDetector(context, 
                            new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            Intent intent = new Intent(ModHwKeys.ACTION_SLEEP);
                            context.sendBroadcast(intent);
                            return true;
                        }
                    });
                }
            });

            XposedHelpers.findAndHookMethod(phoneStatusbarViewClass, "onTouchEvent",
                    MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mDt2sEnabled && mDoubletapGesture != null) {
                        mDoubletapGesture.onTouchEvent((MotionEvent)param.args[0]);
                    }
                }
            });

            // fragment that takes care of notification icon layout for center clock
            try {
                final Class<?> classIconMerger = XposedHelpers.findClass(CLASS_ICON_MERGER, classLoader);

                XposedHelpers.findAndHookMethod(classIconMerger, "onMeasure", 
                        int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (mIconMergerView == null) {
                            mIconMergerView = (View) param.thisObject;
                        }

                        if ((mClock == null && mTrafficMeter == null) || 
                                mContext == null || mLayoutCenter == null || 
                                        mLayoutCenter.getChildCount() == 0) return;

                        Resources res = mContext.getResources();
                        int totalWidth = res.getDisplayMetrics().widthPixels;
                        int iconSize = XposedHelpers.getIntField(param.thisObject, "mIconSize");
                        Integer sbIconPad = (Integer) XposedHelpers.getAdditionalInstanceField(
                                param.thisObject, "gbSbIconPad");
                        if (sbIconPad == null) {
                            sbIconPad = 0;
                            int sbIconPadResId = res.getIdentifier("status_bar_icon_padding", "dimen", PACKAGE_NAME);
                            if (sbIconPadResId != 0) {
                                sbIconPad = res.getDimensionPixelSize(sbIconPadResId);
                            }
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "gbSbIconPad", sbIconPad);
                        } else {
                            sbIconPad = (Integer) XposedHelpers.getAdditionalInstanceField(
                                    param.thisObject, "gbSbIconPad");
                        }

                        // use clock or traffic meter for basic measurement
                        Paint p;
                        String text;
                        if (mClock != null) {
                            p = mClock.getClock().getPaint();
                            text = mClock.getClock().getText().toString();
                        } else {
                            p = mTrafficMeter.getPaint();
                            text = "00000000"; // dummy text in case traffic meter is used for measurement
                        }

                        int clockWidth = (int) p.measureText(text) + iconSize;
                        int availWidth = totalWidth/2 - clockWidth/2 - iconSize/2;
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, "gbAvailWidth", availWidth);
                        int newWidth = availWidth - (availWidth % (iconSize + 2 * sbIconPad));

                        Field fMeasuredWidth = View.class.getDeclaredField("mMeasuredWidth");
                        fMeasuredWidth.setAccessible(true);
                        Field fMeasuredHeight = View.class.getDeclaredField("mMeasuredHeight");
                        fMeasuredHeight.setAccessible(true);
                        Field fPrivateFlags = View.class.getDeclaredField("mPrivateFlags");
                        fPrivateFlags.setAccessible(true); 
                        fMeasuredWidth.setInt(param.thisObject, newWidth);
                        fMeasuredHeight.setInt(param.thisObject, ((View)param.thisObject).getMeasuredHeight());
                        int privateFlags = fPrivateFlags.getInt(param.thisObject);
                        privateFlags |= 0x00000800;
                        fPrivateFlags.setInt(param.thisObject, privateFlags);
                    }
                });

                XposedHelpers.findAndHookMethod(classIconMerger, "checkOverflow",
                        int.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        if (mLayoutCenter == null || mLayoutCenter.getChildCount() == 0 ||
                                XposedHelpers.getAdditionalInstanceField(param.thisObject, "gbAvailWidth") == null) {
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        }

                        try {
                            final View moreView = (View) XposedHelpers.getObjectField(param.thisObject, "mMoreView");
                            if (moreView == null) return null;
    
                            int iconSize = XposedHelpers.getIntField(param.thisObject, "mIconSize");
                            int availWidth = (Integer) XposedHelpers.getAdditionalInstanceField(
                                    param.thisObject, "gbAvailWidth");
                            int sbIconPad = (Integer) XposedHelpers.getAdditionalInstanceField(
                                    param.thisObject, "gbSbIconPad");
    
                            LinearLayout layout = (LinearLayout) param.thisObject;
                            final int N = layout.getChildCount();
                            int visibleChildren = 0;
                            for (int i=0; i<N; i++) {
                                if (layout.getChildAt(i).getVisibility() != View.GONE) visibleChildren++;
                            }
    
                            final boolean overflowShown = (moreView.getVisibility() == View.VISIBLE);
                            final boolean moreRequired = visibleChildren * (iconSize + 2 * sbIconPad) > availWidth;
                            if (moreRequired != overflowShown) {
                                layout.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        moreView.setVisibility(moreRequired ? View.VISIBLE : View.GONE);
                                    }
                                });
                            }
                            return null;
                        } catch (Throwable t) {
                            log("Error in IconMerger.checkOverflow: " + t.getMessage());
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);;
            }

            // Status bar Bluetooth icon policy
            mBroadcastSubReceivers.add(new StatusbarBluetoothIcon(classLoader, prefs));

            // Delete action for screenshot notification
            try {
                XposedBridge.hookAllMethods(XposedHelpers.findClass(CLASS_SAVE_IMG_TASK, classLoader),
                        "doInBackground", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (result == null || (Integer) XposedHelpers.getIntField(result, "result") != 0)
                            return;

                        Notification.Builder builder = (Notification.Builder)
                                XposedHelpers.getObjectField(param.thisObject, "mNotificationBuilder");
                        if (XposedHelpers.getAdditionalInstanceField(builder, "gbDeleteActionAdded") != null)
                            return;

                        prefs.reload();
                        if (!prefs.getBoolean(GravityBoxSettings.PREF_KEY_SCREENSHOT_DELETE, false))
                            return;

                        Uri uri = (Uri) XposedHelpers.getObjectField(result, "imageUri");
                        Intent deleteIntent = new Intent(ACTION_DELETE_SCREENSHOT);
                        deleteIntent.putExtra(SCREENSHOT_URI, uri.toString());
                        Context context = (Context) XposedHelpers.getObjectField(result, "context");
                        Context gbContext = context.createPackageContext(GravityBox.PACKAGE_NAME, 0);
                        builder.addAction(mDeleteIconId, gbContext.getString(R.string.delete),
                                PendingIntent.getBroadcast(context, 0, deleteIntent,
                                        PendingIntent.FLAG_CANCEL_CURRENT));

                        XposedHelpers.setAdditionalInstanceField(builder, "gbDeleteActionAdded", true);
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            // notification ticker policy
            try {
                XposedHelpers.findAndHookMethod(CLASS_PHONE_STATUSBAR, classLoader, "tick",
                        StatusBarNotification.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        switch (mTickerPolicy) {
                            case DEFAULT: return;
                            case DISABLED: param.setResult(null); return;
                            case LOCKED:
                            case SECURED:
                                KeyguardManager kg = (KeyguardManager) 
                                    mContext.getSystemService(Context.KEYGUARD_SERVICE);
                                if (mTickerPolicy == TickerPolicy.LOCKED && kg.isKeyguardLocked()) {
                                    param.setResult(null);
                                } else if (mTickerPolicy == TickerPolicy.SECURED &&
                                        kg.isKeyguardLocked() && kg.isKeyguardSecure()) {
                                    param.setResult(null);
                                }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
        catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void initMtkPlugin(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> pluginFactoryClass = XposedHelpers.findClass(CLASS_PLUGINFACTORY, classLoader);

            XposedHelpers.findAndHookMethod(pluginFactoryClass, "getStatusBarPlugin",
                    "android.content.Context", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mStatusBarPlugin = XposedHelpers.getStaticObjectField(pluginFactoryClass, "mStatusBarPlugin");

                    if (mGetDataNetworkTypeIconGeminiHook == null) {
                        mGetDataNetworkTypeIconGeminiHook = XposedHelpers.findAndHookMethod(mStatusBarPlugin.getClass(),
                                "getDataNetworkTypeIconGemini", CLASS_NETWORKTYPE, int.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (mDisableDataNetworkTypeIcons)
                                    param.setResult(Utils.hasLenovoCustomUI() ? 0 : -1);
                            }
                        });
                    }
                }
            });
        }
        catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setClockPosition(boolean center) {
        if (mClockCentered == center || mClock == null || 
                mIconArea == null || mLayoutCenter == null) {
            return;
        }

        if (center) {
            mClock.getClock().setGravity(Gravity.CENTER);
            mClock.getClock().setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            mClock.getClock().setPadding(0, 0, 0, 0);
            if (mClockInSbContents) {
                mSbContents.removeView(mClock.getClock());
            } else {
                mIconArea.removeView(mClock.getClock());
            }
            mLayoutCenter.addView(mClock.getClock());
            if (DEBUG) log("Clock set to center position");
        } else {
            mClock.getClock().setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            mClock.getClock().setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            mClock.resetOriginalPaddingLeft();
            mLayoutCenter.removeView(mClock.getClock());
            if (mClockInSbContents) {
                mSbContents.addView(mClock.getClock());
            } else {
                mIconArea.addView(mClock.getClock());
            }
            if (DEBUG) log("Clock set to normal position");
        }

        mClockCentered = center;
    }

    private static void setTrafficMeterMode(TrafficMeterMode mode) {
        if (mTrafficMeterMode == mode) return;

        mTrafficMeterMode = mode;

        removeTrafficMeterView();
        if (mTrafficMeter != null) {
            if (mBroadcastSubReceivers.contains(mTrafficMeter)) {
                mBroadcastSubReceivers.remove(mTrafficMeter);
            }
            if (SysUiManagers.IconManager != null) {
                SysUiManagers.IconManager.unregisterListener(mTrafficMeter);
            }
            if (mDownloadProgressView != null) {
                mDownloadProgressView.unregisterListener(mTrafficMeter);
            }
            mTrafficMeter = null;
        }

        if (mTrafficMeterMode != TrafficMeterMode.OFF) {
            mTrafficMeter = TrafficMeterAbstract.create(mContext, mTrafficMeterMode);
            mTrafficMeter.initialize(mPrefs);
            updateTrafficMeterPosition();
            if (SysUiManagers.IconManager != null) {
                SysUiManagers.IconManager.registerListener(mTrafficMeter);
            }
            if (mDownloadProgressView != null) {
                mDownloadProgressView.registerListener(mTrafficMeter);
            }
            mBroadcastSubReceivers.add(mTrafficMeter);
        }
    }

    private static void removeTrafficMeterView() {
        if (mTrafficMeter != null) {
            if (mSbContents != null) {
                mSbContents.removeView(mTrafficMeter);
            }
            if (mLayoutCenter != null) {
                mLayoutCenter.removeView(mTrafficMeter);
            }
            if (mIconArea != null) {
                mIconArea.removeView(mTrafficMeter);
            }
        }
    }

    private static void updateTrafficMeterPosition() {
        removeTrafficMeterView();

        if (mTrafficMeterMode != TrafficMeterMode.OFF && mTrafficMeter != null) {
            switch(mTrafficMeter.getTrafficMeterPosition()) {
                case GravityBoxSettings.DT_POSITION_AUTO:
                    if (mClockCentered) {
                        if (mClockInSbContents && mSbContents != null) {
                            mSbContents.addView(mTrafficMeter);
                        } else if (mIconArea != null) {
                            mIconArea.addView(mTrafficMeter, 0);
                        }
                    } else if (mLayoutCenter != null) {
                        mLayoutCenter.addView(mTrafficMeter);
                    }
                    break;
                case GravityBoxSettings.DT_POSITION_LEFT:
                    if (mSbContents != null) {
                        mSbContents.addView(mTrafficMeter, 0);
                    }
                    break;
                case GravityBoxSettings.DT_POSITION_RIGHT:
                    if (mClockInSbContents && mSbContents != null) {
                        mSbContents.addView(mTrafficMeter);
                    } else if (mIconArea != null) {
                        mIconArea.addView(mTrafficMeter, 0);
                    }
                    break;
            }
        }

        if (mIconMergerView != null) {
            mIconMergerView.requestLayout();
            mIconMergerView.invalidate();
        }
    }

    private static Intent getIntentFromClockLink() {
        if (mClockLink == null) return null;

        try {
            return Intent.parseUri(mClockLink, 0);
        } catch (Exception e) {
            log("Error getting ComponentName from clock link: " + e.getMessage());
            return null;
        }
    }

    private static Runnable mLongPressBrightnessChange = new Runnable() {
        @Override
        public void run() {
            try {
                XposedHelpers.callMethod(mStatusBarView, "performHapticFeedback", 
                        HapticFeedbackConstants.LONG_PRESS);
                adjustBrightness(mInitialTouchX);
                mLinger = BRIGHTNESS_CONTROL_LINGER_THRESHOLD + 1;
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private static void adjustBrightness(int x) {
        try {
            float raw = ((float) x) / mScreenWidth;
    
            // Add a padding to the brightness control on both sides to
            // make it easier to reach min/max brightness
            float padded = Math.min(1.0f - BRIGHTNESS_CONTROL_PADDING,
                    Math.max(BRIGHTNESS_CONTROL_PADDING, raw));
            float value = (padded - BRIGHTNESS_CONTROL_PADDING) /
                    (1 - (2.0f * BRIGHTNESS_CONTROL_PADDING));
    
            int newBrightness = mMinBrightness + (int) Math.round(value *
                    (BRIGHTNESS_ON - mMinBrightness));
            newBrightness = Math.min(newBrightness, BRIGHTNESS_ON);
            newBrightness = Math.max(newBrightness, mMinBrightness);

            Class<?> classSm = XposedHelpers.findClass("android.os.ServiceManager", null);
            Class<?> classIpm = XposedHelpers.findClass("android.os.IPowerManager.Stub", null);
            IBinder b = (IBinder) XposedHelpers.callStaticMethod(
                    classSm, "getService", Context.POWER_SERVICE);
            Object power = XposedHelpers.callStaticMethod(classIpm, "asInterface", b);
            if (power != null) {
                XposedHelpers.callMethod(power, "setTemporaryScreenBrightnessSettingOverride", newBrightness);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, newBrightness);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void brightnessControl(MotionEvent event) {
        try {
            final int action = event.getAction();
            final int x = (int) event.getRawX();
            final int y = (int) event.getRawY();
            Handler handler = (Handler) XposedHelpers.getObjectField(mPhoneStatusBar, "mHandler");
            int statusBarHeaderHeight = 
                    XposedHelpers.getIntField(mPhoneStatusBar, "mStatusBarHeaderHeight");
    
            if (action == MotionEvent.ACTION_DOWN) {
                if (y < statusBarHeaderHeight) {
                    mLinger = 0;
                    mInitialTouchX = x;
                    mInitialTouchY = y;
                    mJustPeeked = true;
                    handler.removeCallbacks(mLongPressBrightnessChange);
                    handler.postDelayed(mLongPressBrightnessChange,
                            BRIGHTNESS_CONTROL_LONG_PRESS_TIMEOUT);
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (y < statusBarHeaderHeight && mJustPeeked) {
                    if (mLinger > BRIGHTNESS_CONTROL_LINGER_THRESHOLD) {
                        adjustBrightness(x);
                    } else {
                        final int xDiff = Math.abs(x - mInitialTouchX);
                        final int yDiff = Math.abs(y - mInitialTouchY);
                        final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
                        if (xDiff > yDiff) {
                            mLinger++;
                        }
                        if (xDiff > touchSlop || yDiff > touchSlop) {
                            handler.removeCallbacks(mLongPressBrightnessChange);
                        }
                    }
                } else {
                    if (y > mPeekHeight) {
                        mJustPeeked = false;
                    }
                    handler.removeCallbacks(mLongPressBrightnessChange);
                }
            } else if (action == MotionEvent.ACTION_UP ||
                        action == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(mLongPressBrightnessChange);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void startSearchAssist() {
        try {
            Object searchPanelView = null;
            if (mPhoneStatusBar != null) {
                searchPanelView = XposedHelpers.getObjectField(mPhoneStatusBar, "mSearchPanelView");
            }
            if (searchPanelView != null) {
                XposedHelpers.callMethod(searchPanelView, "startAssistActivity");
            } else if (mContext != null) {
                boolean isKeyguardShowing = false;
                try {
                    KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                    isKeyguardShowing = km.isKeyguardLocked();
                } catch (Throwable t) { }

                if (isKeyguardShowing) {
                    // Have keyguard show the bouncer and launch the activity if the user succeeds.
                    Class<?> kgtDelCls = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.phone.KeyguardTouchDelegate",
                            mContext.getClassLoader());
                    Object kgtDel = XposedHelpers.callStaticMethod(kgtDelCls, "getInstance", mContext);
                    XposedHelpers.callMethod(kgtDel, "showAssistant");
                } else {
                    // Otherwise, keyguard isn't showing so launch it from here.
                    SearchManager sm = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
                    Intent intent = (Intent) XposedHelpers.callMethod(sm, "getAssistIntent", mContext, true, -2);
                    if (intent == null) return;

                    try {
                        Class<?> amnCls = XposedHelpers.findClass("android.app.ActivityManagerNative",
                                mContext.getClassLoader());
                        Object amn = XposedHelpers.callStaticMethod(amnCls, "getDefault");
                        XposedHelpers.callMethod(amn, "dismissKeyguardOnNextActivity");
                    } catch (Throwable t) { }

                    try {
                        Resources res = mContext.getResources();
                        int animEnter = res.getIdentifier("search_launch_enter", "anim", PACKAGE_NAME);
                        int animExit = res.getIdentifier("search_launch_exit", "anim", PACKAGE_NAME);
                        ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext, animEnter, animExit);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        Constructor<?> uhConst = XposedHelpers.findConstructorExact(UserHandle.class, int.class);
                        UserHandle uh = (UserHandle) uhConst.newInstance(-2);
                        XposedHelpers.callMethod(mContext, "startActivityAsUser", intent, opts.toBundle(), uh);
                    } catch (ActivityNotFoundException e) {
                        log("Activity not found for " + intent.getAction());
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void launchClockAction(String uri) {
        if (mContext == null) return;

        try {
            final Intent intent = Intent.parseUri(uri, 0);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
                if (mPhoneStatusBar != null) {
                    XposedHelpers.callMethod(mPhoneStatusBar, "animateCollapsePanels");
                }
            }
        } catch (ActivityNotFoundException e) {
            log("Error launching assigned app for long-press on clock: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
