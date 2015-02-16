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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ceco.lollipop.gravitybox.ledcontrol.QuietHours;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHoursActivity;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Surface;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModLockscreen {
    private static final String CLASS_PATH = "com.android.keyguard";
    private static final String TAG = "GB:ModLockscreen";
    public static final String PACKAGE_NAME = "com.android.keyguard";

    private static final String CLASS_KGVIEW_MANAGER = CLASS_PATH + ".KeyguardViewManager";
    private static final String CLASS_KGVIEW_MANAGER_HOST = CLASS_KGVIEW_MANAGER + ".ViewManagerHost";
    private static final String CLASS_KG_HOSTVIEW = CLASS_PATH + ".KeyguardHostView";
    private static final String CLASS_KG_ABS_KEY_INPUT_VIEW = CLASS_PATH + ".KeyguardAbsKeyInputView";
    private static final String CLASS_KGVIEW_MEDIATOR = CLASS_PATH + ".KeyguardViewMediator";
    private static final String CLASS_KG_UPDATE_MONITOR = CLASS_PATH + ".KeyguardUpdateMonitor";
    private static final String CLASS_KG_WIDGET_PAGER = CLASS_PATH + ".KeyguardWidgetPager";
    private static final String CLASS_CARRIER_TEXT = CLASS_PATH + (Utils.isMtkDevice() ? 
            ".MediatekCarrierText" : ".CarrierText");
    private static final String CLASS_LOCK_PATTERN_VIEW = "com.android.internal.widget.LockPatternView";
    private static final String ENUM_DISPLAY_MODE = "com.android.internal.widget.LockPatternView.DisplayMode";
    private static final String CLASS_LOCK_PATTERN_UTILS = "com.android.internal.widget.LockPatternUtils";
    private static final String CLASS_KG_UTILS = CLASS_PATH + ".KeyguardUtils";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_KIS = false;

    private static final int STATUSBAR_DISABLE_RECENT = 0x01000000;
    private static final int STATUSBAR_DISABLE_NOTIFICATION_TICKER = 0x00080000;
    private static final int STATUSBAR_DISABLE_EXPAND = 0x00010000;
    private static final int STATUSBAR_DISABLE_SEARCH = 0x02000000;
    private static final int STATUSBAR_DISABLE_CLOCK = 0x00800000;

    private static final List<String> CLOCK_WIDGETS = new ArrayList<String>(Arrays.asList(
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.dvtonder.chronus",
            "net.nurik.roman.dashclock",
            "com.roymam.android.notificationswidget",
            "org.zooper.zwfree",
            "com.devexpert.weather",
            "ch.bitspin.timely"
    ));

    private static XSharedPreferences mPrefs;
    private static XSharedPreferences mQhPrefs;
    private static Object mKeyguardHostView;
    private static Context mGbContext;
    private static Class<?> mKgUpdateMonitorClass;
    private static boolean mBackgroundAlreadySet;
    private static boolean mIsLastScreenBackground;
    private static Object mKgViewManagerHost;
    private static String mCarrierText[];
    private static QuietHours mQuietHours;

    private static boolean mInStealthMode;
    private static Object mPatternDisplayMode; 

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(GravityBoxSettings.ACTION_LOCKSCREEN_SETTINGS_CHANGED) ||
                    action.equals(GravityBoxSettings.ACTION_PREF_LOCKSCREEN_BG_CHANGED)) {
                mPrefs.reload();
                if (DEBUG) log("Settings reloaded");
            } else if (action.equals(KeyguardImageService.ACTION_KEYGUARD_IMAGE_UPDATED)) {
                if (DEBUG_KIS) log("ACTION_KEYGUARD_IMAGE_UPDATED received");
                setLastScreenBackground(context);
            } else if (action.equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQhPrefs.reload();
                mQuietHours = new QuietHours(mQhPrefs);
                if (DEBUG) log("QuietHours settings reloaded");
            }
        }
    };

    public static void initPackageResources(final XSharedPreferences prefs, final InitPackageResourcesParam resparam) {
        try {
            boolean enableMenuKey = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_LOCKSCREEN_MENU_KEY, false);
            resparam.res.setReplacement(PACKAGE_NAME, "bool", "config_disableMenuKeyInLockScreen", !enableMenuKey);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            mPrefs = prefs;
            mQhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
            mQuietHours = new QuietHours(mQhPrefs);

            final Class<?> kgViewManagerClass = XposedHelpers.findClass(CLASS_KGVIEW_MANAGER, classLoader);
            final Class<?> kgHostViewClass = XposedHelpers.findClass(CLASS_KG_HOSTVIEW, classLoader);
            final Class<?> kgAbsKeyInputViewClass = XposedHelpers.findClass(CLASS_KG_ABS_KEY_INPUT_VIEW, classLoader);
            final Class<?> kgViewMediatorClass = XposedHelpers.findClass(CLASS_KGVIEW_MEDIATOR, classLoader);
            mKgUpdateMonitorClass = XposedHelpers.findClass(CLASS_KG_UPDATE_MONITOR, classLoader);
            final Class<?> kgWidgetPagerClass = XposedHelpers.findClass(CLASS_KG_WIDGET_PAGER, classLoader);
            final Class<?> kgViewManagerHostClass = XposedHelpers.findClass(CLASS_KGVIEW_MANAGER_HOST, classLoader);
            final Class<?> carrierTextClass = XposedHelpers.findClass(CLASS_CARRIER_TEXT, classLoader);
            final Class<?> lockPatternViewClass = XposedHelpers.findClass(CLASS_LOCK_PATTERN_VIEW, classLoader);
            final Class<? extends Enum> displayModeEnum = (Class<? extends Enum>) XposedHelpers.findClass(ENUM_DISPLAY_MODE, classLoader);

            XposedBridge.hookAllConstructors(kgViewMediatorClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    mGbContext = context.createPackageContext(GravityBox.PACKAGE_NAME, 0);

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_LOCKSCREEN_SETTINGS_CHANGED);
                    intentFilter.addAction(KeyguardImageService.ACTION_KEYGUARD_IMAGE_UPDATED);
                    intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_LOCKSCREEN_BG_CHANGED);
                    context.registerReceiver(mBroadcastReceiver, intentFilter);
                    if (DEBUG) log("Keyguard mediator constructed");
                }
            });

            XposedHelpers.findAndHookMethod(kgViewManagerClass, "maybeCreateKeyguardLocked", 
                    boolean.class, boolean.class, Bundle.class, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");

                    final String bgType = mPrefs.getString(
                            GravityBoxSettings.PREF_KEY_LOCKSCREEN_BACKGROUND,
                            GravityBoxSettings.LOCKSCREEN_BG_DEFAULT);

                    Bitmap customBg = null;
                    mBackgroundAlreadySet = false;
                    if (bgType.equals(GravityBoxSettings.LOCKSCREEN_BG_COLOR)) {
                        int color = mPrefs.getInt(
                                GravityBoxSettings.PREF_KEY_LOCKSCREEN_BACKGROUND_COLOR, Color.BLACK);
                        customBg = Utils.drawableToBitmap(new ColorDrawable(color));
                    } else if (bgType.equals(GravityBoxSettings.LOCKSCREEN_BG_IMAGE)) {
                        String wallpaperFile = mGbContext.getFilesDir() + "/lockwallpaper";
                        customBg = BitmapFactory.decodeFile(wallpaperFile);
                    } else if (bgType.equals(GravityBoxSettings.LOCKSCREEN_BG_LAST_SCREEN)) {
                        setLastScreenBackground(context);
                    }

                    if (customBg != null) {
                        if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_LOCKSCREEN_BACKGROUND_BLUR_EFFECT, false)) {
                            customBg = Utils.blurBitmap(context, customBg, mPrefs.getInt(
                                    GravityBoxSettings.PREF_KEY_LOCKSCREEN_BACKGROUND_BLUR_INTENSITY, 14));
                        }
                        Object kgUpdateMonitor = XposedHelpers.callStaticMethod(mKgUpdateMonitorClass, 
                                "getInstance", context);
                        XposedHelpers.callMethod(kgUpdateMonitor, "dispatchSetBackground", customBg);
                        if (DEBUG) log("maybeCreateKeyguardLocked: custom wallpaper set");
                    }
                }
            });

            XposedHelpers.findAndHookMethod(kgViewManagerHostClass, "setCustomBackground",
                    Drawable.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mKgViewManagerHost == null) {
                        mKgViewManagerHost = param.thisObject;
                    }
                    final Drawable d = (Drawable) param.args[0];
                    if (d != null) {
                        mBackgroundAlreadySet = !mIsLastScreenBackground;
                        d.clearColorFilter();
                        final int alpha = (int) ((1 - mPrefs.getInt(
                                GravityBoxSettings.PREF_KEY_LOCKSCREEN_BACKGROUND_OPACITY, 50) / 100f) * 255);
                        final int overlayColor = Color.argb(alpha, 0, 0, 0);
                        d.setColorFilter(overlayColor, PorterDuff.Mode.SRC_OVER);
                        ((View)param.thisObject).invalidate();
                        if (DEBUG) log("setCustomBackground: custom background opacity set");
                    }
                    mIsLastScreenBackground = false;
                }
            });

            try {
                XposedHelpers.findAndHookMethod(kgViewManagerClass, "shouldEnableScreenRotation", 
                        shouldEnableScreenRotationHook);
            } catch (NoSuchMethodError nme) {
                try {
                    XposedHelpers.findAndHookMethod(CLASS_KG_UTILS, classLoader, 
                            "shouldEnableScreenRotation", Context.class, shouldEnableScreenRotationHook);
                } catch (NoSuchMethodError nme2) {
                    XposedBridge.log(nme2);
                }
            }

            XposedHelpers.findAndHookMethod(kgHostViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mKeyguardHostView = param.thisObject; 
                    Object slidingChallenge = XposedHelpers.getObjectField(
                            param.thisObject, "mSlidingChallengeLayout");

                    if (slidingChallenge != null) {
                        try {
                            // find lock button and assign long click listener
                            // we assume there's only 1 ImageButton in sliding challenge layout
                            // we have to do it this way since there's no ID assigned in layout XML
                            final ViewGroup vg = (ViewGroup) slidingChallenge;
                            final int childCount = vg.getChildCount();
                            for (int i = 0; i < childCount; i++) {
                                View v = vg.getChildAt(i);
                                if (v instanceof ImageButton) {
                                    v.setOnLongClickListener(mLockButtonLongClickListener);
                                    v.bringToFront();
                                    break;
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(kgAbsKeyInputViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final String className = param.thisObject.getClass().getName();
                    if (!className.endsWith("KeyguardPasswordView") && !className.endsWith("KeyguardPINView")) {
                        return;
                    }

                    final TextView passwordEntry = 
                            (TextView) XposedHelpers.getObjectField(param.thisObject, "mPasswordEntry");
                    if (passwordEntry != null) {
                        passwordEntry.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void afterTextChanged(Editable s) {
                                if (!mPrefs.getBoolean(
                                        GravityBoxSettings.PREF_KEY_LOCKSCREEN_QUICK_UNLOCK, false)) return;

                                final Object callback = 
                                        XposedHelpers.getObjectField(param.thisObject, "mCallback");
                                final Object lockPatternUtils = 
                                        XposedHelpers.getObjectField(param.thisObject, "mLockPatternUtils");
                                String entry = passwordEntry.getText().toString();

                                if (callback != null && lockPatternUtils != null &&
                                        entry.length() > 3 && 
                                        (Boolean) XposedHelpers.callMethod(
                                                lockPatternUtils, "checkPassword", entry)) {
                                    XposedHelpers.callMethod(callback, "reportSuccessfulUnlockAttempt");
                                    XposedHelpers.callMethod(callback, "dismiss", true);
                                }
                            }
                            @Override
                            public void beforeTextChanged(CharSequence arg0,
                                    int arg1, int arg2, int arg3) {
                            }
                            @Override
                            public void onTextChanged(CharSequence arg0,
                                    int arg1, int arg2, int arg3) {
                            }
                        });
                    }
                }
            });

            XposedHelpers.findAndHookMethod(kgViewMediatorClass, "adjustStatusBarLocked", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    int policy = GravityBoxSettings.SBL_POLICY_DEFAULT;
                    try {
                        policy = Integer.valueOf(mPrefs.getString(
                            GravityBoxSettings.PREF_KEY_STATUSBAR_LOCK_POLICY, "0"));
                    } catch (NumberFormatException nfe) {
                        //
                    }
                    if (DEBUG) log("Statusbar lock policy = " + policy);
                    if (policy == GravityBoxSettings.SBL_POLICY_DEFAULT) return;

                    final Object sbManager = 
                            XposedHelpers.getObjectField(param.thisObject, "mStatusBarManager");
                    final Context context = 
                            (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    final boolean showing = XposedHelpers.getBooleanField(param.thisObject, "mShowing");

                    if (showing && sbManager != null && !(context instanceof Activity)) {
                        int flags = STATUSBAR_DISABLE_RECENT;
                        if ((Boolean) XposedHelpers.callMethod(param.thisObject, "isSecure") &&
                                policy != GravityBoxSettings.SBL_POLICY_UNLOCKED_SECURED) {
                            flags |= STATUSBAR_DISABLE_EXPAND;
                            flags |= STATUSBAR_DISABLE_NOTIFICATION_TICKER;
                        } else if (policy == GravityBoxSettings.SBL_POLICY_LOCKED) {
                            flags |= STATUSBAR_DISABLE_EXPAND;
                        }

                        try {
                            Method m = XposedHelpers.findMethodExact(
                                    kgViewMediatorClass, "isAssistantAvailable");
                            if (!(Boolean) m.invoke(param.thisObject)) {
                                flags |= STATUSBAR_DISABLE_SEARCH;
                            }
                        } catch(NoSuchMethodError nme) {
                            if (DEBUG) log("isAssistantAvailable method doesn't exist (Android < 4.2.2?)");
                        }

                        XposedHelpers.callMethod(sbManager, "disable", flags);
                        if (DEBUG) log("adjustStatusBarLocked: new flags = " + flags);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(kgWidgetPagerClass, "onPageSwitched",
                    View.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final View v = (View) param.thisObject;
                    final int visibilityMode = Integer.valueOf(mPrefs.getString(
                            GravityBoxSettings.PREF_KEY_LOCKSCREEN_STATUSBAR_CLOCK, "0"));

                    if (visibilityMode != 0) {
                        v.setSystemUiVisibility(visibilityMode == 1 ?
                                v.getSystemUiVisibility() | STATUSBAR_DISABLE_CLOCK :
                                    v.getSystemUiVisibility() & ~STATUSBAR_DISABLE_CLOCK);
                    } else if (param.args[0] instanceof ViewGroup) {
                        final ViewGroup vg = (ViewGroup) param.args[0];
                        if (vg.getChildAt(0) instanceof AppWidgetHostView) {
                            final AppWidgetProviderInfo info = 
                                    ((AppWidgetHostView) vg.getChildAt(0)).getAppWidgetInfo();
                            final String widgetPackage = info.provider.getPackageName();
                            if (DEBUG) log("onPageSwitched: widget package = " + widgetPackage);
                            final boolean disableClock = CLOCK_WIDGETS.contains(widgetPackage);
                            v.setSystemUiVisibility(v.getSystemUiVisibility() |
                                    (disableClock ? STATUSBAR_DISABLE_CLOCK : 0));
                        }
                    }
                }
            });

            if (Utils.isMtkDevice()) {
                if (Utils.hasGeminiSupport()) {
                    XposedHelpers.findAndHookMethod(carrierTextClass, "showOrHideCarrier", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            TextView carrierDivider;
                            Object divider = XposedHelpers.getObjectField(param.thisObject, "mCarrierDivider");
                            if (divider instanceof TextView[]) {
                                carrierDivider = (TextView) ((TextView[])divider)[0];
                            } else {
                                carrierDivider = (TextView) divider;
                            }
                            mCarrierText = new String[] {
                                    mPrefs.getString(GravityBoxSettings.PREF_KEY_LOCKSCREEN_CARRIER_TEXT, ""),
                                    mPrefs.getString(GravityBoxSettings.PREF_KEY_LOCKSCREEN_CARRIER2_TEXT, "")};

                            if (carrierDivider != null) {
                                if ((!mCarrierText[0].isEmpty() && mCarrierText[0].trim().isEmpty()) ||
                                        (!mCarrierText[1].isEmpty() && mCarrierText[1].trim().isEmpty()))
                                    carrierDivider.setVisibility(View.GONE);
                            }
                        }
                    });

                    String updateCarrierTextMethod;
                    try {
                        updateCarrierTextMethod = XposedHelpers.findMethodExact(carrierTextClass, "updateCarrierTextGemini",
                                "com.android.internal.telephony.IccCardConstants$State",
                                    CharSequence.class, CharSequence.class, int.class).getName();
                    } catch (NoSuchMethodError nme) {
                        if (DEBUG) log("updateCarrierTextGemini method doesn't exist, fallback to updateCarrierText");
                        updateCarrierTextMethod = "updateCarrierText";
                    }
                    XposedHelpers.findAndHookMethod(carrierTextClass, updateCarrierTextMethod,
                            "com.android.internal.telephony.IccCardConstants$State",
                                CharSequence.class, CharSequence.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            TextView carrierTextView[] = new TextView[2];
                            Object carrierView = XposedHelpers.getObjectField(param.thisObject, "mCarrierView");
                            if (carrierView instanceof TextView[]) {
                                carrierTextView[0] = (TextView) ((TextView[])carrierView)[0];
                                carrierTextView[1] = (TextView) ((TextView[])carrierView)[1];
                            } else {
                                carrierTextView[0] = (TextView) carrierView;
                                carrierTextView[1] = (TextView) XposedHelpers.getObjectField(
                                        param.thisObject, "mCarrierGeminiView");
                            }

                            int[] origVisibility = new int[] {
                                    carrierTextView[0] == null ? View.GONE : carrierTextView[0].getVisibility(),
                                    carrierTextView[1] == null ? View.GONE : carrierTextView[1].getVisibility()
                            };

                            if (mCarrierText != null) {
                                for (int i=0; i<2; i++) {
                                    if (carrierTextView[i] == null) continue;
                                    if (mCarrierText[i].isEmpty()) {
                                        carrierTextView[i].setVisibility(origVisibility[i]);
                                    } else {
                                        if (mCarrierText[i].trim().isEmpty()) {
                                            carrierTextView[i].setText("");
                                            carrierTextView[i].setVisibility(View.GONE);
                                        } else {
                                            carrierTextView[i].setText(mCarrierText[i]);
                                            carrierTextView[i].setVisibility(View.VISIBLE);
                                        }
                                    }
                                }
                                if ((carrierTextView[0] != null &&
                                     carrierTextView[0].getVisibility() == View.VISIBLE) &&
                                    (carrierTextView[1] != null && 
                                     carrierTextView[1].getVisibility() == View.VISIBLE)) {
                                    carrierTextView[0].setGravity(Gravity.RIGHT);
                                    carrierTextView[1].setGravity(Gravity.LEFT);
                                } else {
                                    if (carrierTextView[0] != null) {
                                        carrierTextView[0].setGravity(Gravity.CENTER);
                                    }
                                    if (carrierTextView[1] != null) {
                                        carrierTextView[1].setGravity(Gravity.CENTER);
                                    }
                                }
                            }
                        }
                    });
                } else {
                    XposedHelpers.findAndHookMethod(carrierTextClass, "updateCarrierText",
                            "com.android.internal.telephony.IccCardConstants$State", CharSequence.class, CharSequence.class,
                            new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            TextView carrierTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mCarrierView");
                            String carrierText = mPrefs.getString(GravityBoxSettings.PREF_KEY_LOCKSCREEN_CARRIER_TEXT, null);
                            if (carrierText != null && !carrierText.isEmpty()) {
                                carrierTextView.setText(carrierText.trim());
                            }
                        }
                    });
                }
            } else {
                XposedBridge.hookAllMethods(carrierTextClass, "getCarrierTextForSimState", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        String carrierText = mPrefs.getString(GravityBoxSettings.PREF_KEY_LOCKSCREEN_CARRIER_TEXT, null);
                        if (carrierText != null && !carrierText.isEmpty()) {
                            param.setResult(carrierText.trim());
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(lockPatternViewClass, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    beforeLockPatternDraw(displayModeEnum, param.thisObject);
                }

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    afterLockPatternDraw(param.thisObject);
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_LOCK_PATTERN_UTILS, classLoader, "updateEmergencyCallButtonState",
                    Button.class, int.class, boolean.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_LOCKSCREEN_DISABLE_ECB, false) &&
                            (Integer) param.args[1] != TelephonyManager.CALL_STATE_OFFHOOK) {
                        param.args[2] = false;
                    }
                }
            });

            if (Utils.isMtkDevice()) {
                XposedHelpers.findAndHookMethod(mKgUpdateMonitorClass, "handleBootCompleted", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mKgViewManagerHost != null) {
                            XposedHelpers.callMethod(mKgViewManagerHost, "setCustomBackground",
                                    XposedHelpers.getObjectField(mKgViewManagerHost, "mCustomBackground"));
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(kgViewMediatorClass, "playSounds", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.SCREEN_LOCK)) {
                        XposedHelpers.setBooleanField(param.thisObject, "mSuppressNextLockSound", false);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static XC_MethodReplacement shouldEnableScreenRotationHook = new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            try {
                if (Utils.isMtkDevice()) {
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                } else {
                    return mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_LOCKSCREEN_ROTATION, false);
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
                return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
            }
        }
    };

    private static final OnLongClickListener mLockButtonLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            if (mKeyguardHostView == null) return false;
            try {
                if (Utils.isMtkDevice()) {
                    XposedHelpers.callMethod(mKeyguardHostView, "showNextSecurityScreenOrFinish", false, true);
                } else {
                    XposedHelpers.callMethod(mKeyguardHostView, "showNextSecurityScreenOrFinish", false);
                }
                return true;
            } catch (Throwable t) {
                XposedBridge.log(t);
                return false;
            }
        }
    };

    static class HandleDrawable {
        Object mHd;

        public HandleDrawable(Object handleDrawable) {
            mHd = handleDrawable;
        }

        public void set(Object handleDrawable) {
            mHd = handleDrawable;
        }

        public float getAlpha() {
            return (Float) XposedHelpers.callMethod(mHd, "getAlpha");
        }

        public float getPositionX() {
            return (Float) XposedHelpers.callMethod(mHd, "getPositionX");
        }

        public float getPositionY() {
            return (Float) XposedHelpers.callMethod(mHd, "getPositionY");
        }

        public int getWidth() {
            return (Integer) XposedHelpers.callMethod(mHd, "getWidth");
        }

        public int getHeight() {
            return (Integer) XposedHelpers.callMethod(mHd, "getHeight");
        }
    }

    private static void setLastScreenBackground(Context context) {
        if (mBackgroundAlreadySet) {
            if (DEBUG_KIS) log("setLastScreenBackground: Background has been already set (album art?)");
            return;
        }
        try {
            String kisImageFile = mGbContext.getFilesDir() + "/kis_image.png";
            Bitmap customBg = BitmapFactory.decodeFile(kisImageFile);
            if (customBg != null) {
                int rotation = Utils.SystemProp.getInt("ro.sf.hwrotation", 0);
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                switch (wm.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_90: rotation -= 90; break;
                    case Surface.ROTATION_270: rotation += 90; break;
                    case Surface.ROTATION_180: rotation -= 180; break;
                }
                if (rotation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotation);
                    customBg = Bitmap.createBitmap(customBg, 0, 0, customBg.getWidth(), 
                            customBg.getHeight(), matrix, true);
                }
                Object kgUpdateMonitor = XposedHelpers.callStaticMethod(mKgUpdateMonitorClass, 
                        "getInstance", context);
                mIsLastScreenBackground = true;
                XposedHelpers.callMethod(kgUpdateMonitor, "dispatchSetBackground", customBg);
                if (DEBUG_KIS) log("setLastScreenBackground: Last screen background updated");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void beforeLockPatternDraw(final Class<? extends Enum> displayModeEnum, final Object thisObject) {
        final Object patternDisplayMode = XposedHelpers.getObjectField(thisObject, "mPatternDisplayMode");
        final Boolean inStealthMode = XposedHelpers.getBooleanField(thisObject, "mInStealthMode");  

        if (!mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_LOCKSCREEN_SHOW_PATTERN_ERROR, true) &&
                    mPatternDisplayMode == null && patternDisplayMode == Enum.valueOf(displayModeEnum, "Wrong")) {
            mInStealthMode = inStealthMode;
            mPatternDisplayMode = patternDisplayMode;
            XposedHelpers.setBooleanField(thisObject, "mInStealthMode", true);
            XposedHelpers.setObjectField(thisObject, "mPatternDisplayMode", Enum.valueOf(displayModeEnum, "Correct"));
        } else {
            mPatternDisplayMode = null;
        }
    }

    private static void afterLockPatternDraw(final Object thisObject) {
        if (null != mPatternDisplayMode) {
            XposedHelpers.setBooleanField(thisObject, "mInStealthMode", mInStealthMode);
            XposedHelpers.setObjectField(thisObject, "mPatternDisplayMode", mPatternDisplayMode);
            mInStealthMode = false;
            mPatternDisplayMode = null;
        }
    }
}
