/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.oreo.gravitybox;

import com.ceco.oreo.gravitybox.R;

import java.util.ArrayList;
import java.util.List;

import com.ceco.oreo.gravitybox.ModStatusBar.ContainerType;

import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BatteryStyleController implements BroadcastSubReceiver {
    private static final String TAG = "GB:BatteryStyleController";
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String CLASS_BATTERY_CONTROLLER = 
            "com.android.systemui.statusbar.policy.BatteryControllerImpl";
    public static final String CLASS_BATTERY_METER_VIEW = "com.android.systemui.BatteryMeterView";
    private static final boolean DEBUG = false;

    private enum KeyguardMode { DEFAULT, ALWAYS_SHOW, HIDDEN };

    private ContainerType mContainerType;
    private ViewGroup mContainer;
    private ViewGroup mSystemIcons;
    private Context mContext;
    private XSharedPreferences mPrefs;
    private Object mStatusBar;
    private int mBatteryStyle;
    private boolean mBatteryPercentTextEnabledSb;
    private boolean mBatteryPercentTextOnRight;
    private KeyguardMode mBatteryPercentTextKgMode;
    private StatusbarBatteryPercentage mPercentText;
    private CmCircleBattery mCircleBattery;
    private StatusbarBattery mStockBattery;
    private boolean mBatterySaverIndicationDisabled;
    private boolean mDashIconHidden;
    private boolean mIsDashCharging;
    private List<Unhook> mHooks = new ArrayList<>();
    private Integer mOosSystemIconsMarginEndOriginal;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public BatteryStyleController(ContainerType containerType, ViewGroup container,
            XSharedPreferences prefs, Object statusBar) throws Throwable {
        mContainerType = containerType;
        mContainer = container;
        mStatusBar = statusBar;
        mContext = container.getContext();
        mSystemIcons = (ViewGroup) mContainer.findViewById(
                mContext.getResources().getIdentifier("system_icons", "id", PACKAGE_NAME));

        if (mSystemIcons != null) {
            initPreferences(prefs);
            initLayout();
            createHooks();
            updateBatteryStyle();
        }
    }

    public void destroy() {
        for (Unhook hook : mHooks) {
            hook.unhook();
        }
        mHooks.clear();
        mHooks = null;
        if (mPercentText != null) {
            mSystemIcons.removeView(mPercentText.getView());
            mPercentText.destroy();
            mPercentText = null;
        }
        if (mCircleBattery != null) {
            mSystemIcons.removeView(mCircleBattery);
            mCircleBattery = null;
        }
        if (mStockBattery != null) {
            mStockBattery.destroy();
            mStockBattery = null;
        }
        mSystemIcons = null;
        mContainer = null;
        mStatusBar = null;
        mPrefs = null;
        mContext = null;
    }

    private void initPreferences(XSharedPreferences prefs) {
        mPrefs = prefs;
        mBatteryStyle = Integer.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_STYLE, "1"));
        mBatteryPercentTextEnabledSb = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_STATUSBAR, false);
        mBatteryPercentTextKgMode = KeyguardMode.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_KEYGUARD, "DEFAULT"));
        mBatterySaverIndicationDisabled = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_BATTERY_SAVER_INDICATION_DISABLE, false);
        mDashIconHidden = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_BATTERY_HIDE_DASH_ICON, false);
        mBatteryPercentTextOnRight = "RIGHT".equals(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_POSITION, "RIGHT"));
    }

    private void initLayout() throws Throwable {
        Resources res = mContext.getResources();
        Resources gbRes = Utils.getGbContext(mContext).getResources();

        int bIconIndex = mSystemIcons.getChildCount();
        int bIconMarginStart = gbRes.getDimensionPixelSize(R.dimen.circle_battery_padding_left);
        int bIconMarginEnd = gbRes.getDimensionPixelSize(R.dimen.circle_battery_padding_right);

        // find stock battery
        View stockBatteryView = mSystemIcons.findViewById(
                res.getIdentifier("battery", "id", PACKAGE_NAME));
        if (stockBatteryView != null) {
            bIconIndex = mSystemIcons.indexOfChild(stockBatteryView);
            bIconMarginStart = ((MarginLayoutParams) stockBatteryView.getLayoutParams()).getMarginStart();
            bIconMarginEnd = ((MarginLayoutParams) stockBatteryView.getLayoutParams()).getMarginEnd();
            mStockBattery = new StatusbarBattery(stockBatteryView, this);
        }

        // inject circle battery view
        mCircleBattery = new CmCircleBattery(mContext, this);
        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lParams.gravity = Gravity.CENTER_VERTICAL;
        lParams.setMarginStart(bIconMarginStart);
        lParams.setMarginEnd(bIconMarginEnd);
        mCircleBattery.setLayoutParams(lParams);
        mCircleBattery.setVisibility(View.GONE);
        mSystemIcons.addView(mCircleBattery, bIconIndex);
        if (DEBUG) log("CmCircleBattery injected");

        // inject percent text 
        TextView percentTextView = new TextView(mContext);
        lParams = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        percentTextView.setLayoutParams(lParams);
        percentTextView.setPadding(
                gbRes.getDimensionPixelSize(mBatteryPercentTextOnRight ?
                        R.dimen.percent_text_padding_right :
                        R.dimen.percent_text_padding_left),
                0,
                gbRes.getDimensionPixelSize(mBatteryPercentTextOnRight ?
                        R.dimen.percent_text_padding_left :
                        R.dimen.percent_text_padding_right),
                0);
        percentTextView.setTextColor(Color.WHITE);
        percentTextView.setVisibility(View.GONE);
        mPercentText = new StatusbarBatteryPercentage(percentTextView, mPrefs, this);
        mSystemIcons.addView(mPercentText.getView(), mBatteryPercentTextOnRight ? bIconIndex+2 : bIconIndex);
        if (DEBUG) log("Battery percent text injected");
    }

    private void updateBatteryStyle() {
        try {
            if (mStockBattery != null) {
                if (mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_STOCK ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_STOCK_PERCENT) {
                    mStockBattery.setVisibility(View.VISIBLE);
                    mStockBattery.setShowPercentage(mBatteryStyle == 
                            GravityBoxSettings.BATTERY_STYLE_STOCK_PERCENT);
                } else {
                    mStockBattery.setVisibility(View.GONE);
                }
            }

            if (mCircleBattery != null) {
                mCircleBattery.setVisibility(isCurrentStyleCircleBattery() ?
                                View.VISIBLE : View.GONE);
                mCircleBattery.setPercentage(
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_PERCENT ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED_PERCENT);
                mCircleBattery.setStyle(
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED_PERCENT ?
                                CmCircleBattery.Style.DASHED : CmCircleBattery.Style.SOLID);
            }

            if (mPercentText != null) {
                switch (mContainerType) {
                    case STATUSBAR:
                        if (mBatteryPercentTextEnabledSb) {
                            mPercentText.setVisibility(View.VISIBLE);
                            mPercentText.updateText();
                        } else {
                            mPercentText.setVisibility(View.GONE);
                        }
                        break;
                    case KEYGUARD:
                        mPercentText.updateText();
                        XposedHelpers.callMethod(mContainer, "updateVisibilities");
                        break;
                    default: break;
                }
            }

            // adjust System Icons end margin on OOS
            if (mContainerType == ContainerType.STATUSBAR && Utils.isOxygenOsRom()) {
                Resources gbRes = Utils.getGbContext(mContext).getResources();
                int systemIconsMarginEnd = gbRes.getDimensionPixelSize(R.dimen.system_icons_margin_end_oos);
                MarginLayoutParams mlp = (MarginLayoutParams) mSystemIcons.getLayoutParams();
                if (mOosSystemIconsMarginEndOriginal == null) {
                    mOosSystemIconsMarginEndOriginal = mlp.getMarginEnd();
                }
                mlp.setMarginEnd(ModStatusBar.isCLockOnRight() &&
                            ((mBatteryStyle != GravityBoxSettings.BATTERY_STYLE_STOCK &&
                                    mBatteryStyle != GravityBoxSettings.BATTERY_STYLE_NONE) ||
                             (mBatteryPercentTextEnabledSb && mBatteryPercentTextOnRight)) ?
                        systemIconsMarginEnd : mOosSystemIconsMarginEndOriginal);
                mSystemIcons.setLayoutParams(mlp);
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createHooks() {
        if (mContainerType == ContainerType.STATUSBAR) {
            try {
                Class<?> batteryControllerClass = XposedHelpers.findClass(CLASS_BATTERY_CONTROLLER,
                        mContext.getClassLoader());
                mHooks.add(XposedHelpers.findAndHookMethod(batteryControllerClass, "onReceive", 
                        Context.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        updateBatteryStyle();
                    }
                }));
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }

        if (mContainerType == ContainerType.KEYGUARD) {
            try {
               if (Utils.isSamsungRom()) {
                   mHooks.add(XposedHelpers.findAndHookMethod(mContainer.getClass(), "onBatteryLevelChanged",
                          int.class, boolean.class, boolean.class, int.class, int.class, int.class,
                          int.class, boolean.class, new XC_MethodHook() {
                       @Override
                       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                           updateBatteryStyle();
                       }
                   }));
               } else {
                   mHooks.add(XposedHelpers.findAndHookMethod(mContainer.getClass(), "onBatteryLevelChanged",
                          int.class, boolean.class, boolean.class, new XC_MethodHook() {
                       @Override
                       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                           updateBatteryStyle();
                       }
                   }));
               }
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
            try {
                mHooks.add(XposedHelpers.findAndHookMethod(mContainer.getClass(), "onConfigurationChanged",
                        Configuration.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (mPercentText != null) {
                            mPercentText.setTextSize(Integer.valueOf(mPrefs.getString(
                                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_SIZE, "16")));
                        }
                    }
                }));
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
            try {
                mHooks.add(XposedHelpers.findAndHookMethod(CLASS_BATTERY_METER_VIEW, mContext.getClassLoader(),
                        "updateShowPercent", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (DEBUG) log(mContainerType + ": updateShowPercent");
                        if (((View)param.thisObject).getParent() == mSystemIcons) {
                            TextView v = (TextView) XposedHelpers.getObjectField(param.thisObject, "mBatteryPercentView");
                            if (v != null) v.setVisibility(View.GONE);
                            if (mBatteryPercentTextKgMode == KeyguardMode.DEFAULT) {
                                mPercentText.setVisibility(v == null ? View.GONE : View.VISIBLE);
                            } else if (mBatteryPercentTextKgMode == KeyguardMode.ALWAYS_SHOW) {
                                mPercentText.setVisibility(View.VISIBLE);
                            } else if (mBatteryPercentTextKgMode == KeyguardMode.HIDDEN) {
                                mPercentText.setVisibility(View.GONE);
                            }
                        }
                    }
                }));
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }

        if (Utils.isOxygenOsRom()) {
            try {
                mHooks.add(XposedHelpers.findAndHookMethod(ModStatusBar.CLASS_STATUSBAR,
                        mContainer.getClass().getClassLoader(),
                        "updateDashChargeView", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mIsDashCharging = XposedHelpers.getBooleanField(param.thisObject, "mFastCharge");
                        if (mDashIconHidden || isCurrentStyleCircleBattery()) {
                            ((View)XposedHelpers.getObjectField(param.thisObject,
                                    "mBatteryDashChargeView")).setVisibility(View.GONE);
                            ((View)XposedHelpers.getObjectField(param.thisObject,
                                    "mKeyguardBatteryDashChargeView")).setVisibility(View.GONE);
                        }
                        updateBatteryStyle();
                    }
                }));
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    }

    private boolean isCurrentStyleCircleBattery() {
        return (mCircleBattery != null &&
                (mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE ||
                 mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_PERCENT ||
                 mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED ||
                 mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED_PERCENT));
    }

    public boolean isBatterySaverIndicationDisabled() {
        return mBatterySaverIndicationDisabled;
    }

    public boolean isDashIconHidden() {
        return mDashIconHidden;
    }

    public boolean isDashCharging() {
        return mIsDashCharging;
    }

    public ContainerType getContainerType() {
        return mContainerType;
    }

    private void updateDashChargeView() {
        try {
            XposedHelpers.callMethod(mStatusBar, "updateDashChargeView");
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(GravityBoxSettings.ACTION_PREF_BATTERY_STYLE_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_STYLE)) {
                mBatteryStyle = intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_STYLE, 1);
                if (DEBUG) log("mBatteryStyle changed to: " + mBatteryStyle);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_HIDE_DASH)) {
                mDashIconHidden = intent.getBooleanExtra(GravityBoxSettings.EXTRA_HIDE_DASH, false);
            }
            updateBatteryStyle();
            if (Utils.isOxygenOsRom()) {
                updateDashChargeView();
            }
        } else if (action.equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_STATUSBAR)) {
                mBatteryPercentTextEnabledSb = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_STATUSBAR, false);
                if (DEBUG) log("mBatteryPercentTextEnabledSb changed to: " + mBatteryPercentTextEnabledSb);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_KEYGUARD)) {
                mBatteryPercentTextKgMode = KeyguardMode.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_KEYGUARD));
                if (DEBUG) log("mBatteryPercentTextEnabledKg changed to: " + mBatteryPercentTextKgMode);
            }
            updateBatteryStyle();
        } else if (action.equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_SIZE_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_SIZE) && mPercentText != null) {
                    int textSize = intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_SIZE, 0);
                    mPercentText.setTextSize(textSize);
                    if (DEBUG) log("PercentText size changed to: " + textSize);
        } else if (action.equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_STYLE_CHANGED)
                       && mPercentText != null) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_STYLE)) {
                    String percentSign = intent.getStringExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_STYLE);
                    mPercentText.setPercentSign(percentSign);
                    if (DEBUG) log("PercentText sign changed to: " + percentSign);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_CHARGING)) {
                int chargingStyle = intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_CHARGING,
                        StatusbarBatteryPercentage.CHARGING_STYLE_NONE);
                mPercentText.setChargingStyle(chargingStyle);
                if (DEBUG) log("PercentText charging style changed to: " + chargingStyle);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_CHARGING_COLOR)) {
                int chargingColor = intent.getIntExtra(
                        GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_CHARGING_COLOR, Color.GREEN);
                mPercentText.setChargingColor(chargingColor);
                if (DEBUG) log("PercentText charging color changed to: " + chargingColor);
            }
        } else if (action.equals(GravityBoxSettings.ACTION_BATTERY_SAVER_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_BS_INDICATION_DISABLE)) {
                mBatterySaverIndicationDisabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_BS_INDICATION_DISABLE, false);
                if (mCircleBattery != null && mCircleBattery.isAttachedToWindow()
                        && mContainerType == ContainerType.STATUSBAR) {
                    mCircleBattery.postInvalidate();
                }
            }
        }
    }
}
