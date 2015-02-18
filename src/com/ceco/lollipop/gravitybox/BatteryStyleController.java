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

import com.ceco.lollipop.gravitybox.ModStatusBar.ContainerType;

import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BatteryStyleController implements BroadcastSubReceiver {
    private static final String TAG = "GB:BatteryStyleController";
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String CLASS_BATTERY_CONTROLLER = 
            "com.android.systemui.statusbar.policy.BatteryController";
    private static final boolean DEBUG = false;

    public static final String ACTION_MTK_BATTERY_PERCENTAGE_SWITCH = 
            "mediatek.intent.action.BATTERY_PERCENTAGE_SWITCH";
    public static final String EXTRA_MTK_BATTERY_PERCENTAGE_STATE = "state";
    public static final String SETTING_MTK_BATTERY_PERCENTAGE = "battery_percentage";

    private ContainerType mContainerType;
    private ViewGroup mContainer;
    private ViewGroup mSystemIcons;
    private Context mContext;
    private XSharedPreferences mPrefs;
    private int mBatteryStyle;
    private boolean mBatteryPercentTextEnabled;
    private boolean mMtkPercentTextEnabled;
    private StatusbarBatteryPercentage mPercentText;
    private CmCircleBattery mCircleBattery;
    private StatusbarBattery mStockBattery;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public BatteryStyleController(ContainerType containerType, ViewGroup container, XSharedPreferences prefs) {
        mContainerType = containerType;
        mContainer = container;
        mContext = container.getContext();
        mSystemIcons = (ViewGroup) mContainer.findViewById(
                mContext.getResources().getIdentifier("system_icons", "id", PACKAGE_NAME));

        initPreferences(prefs);
        initLayout();
        updateBatteryStyle();
        createHooks();
    }

    private void initPreferences(XSharedPreferences prefs) {
        mPrefs = prefs;
        mBatteryStyle = Integer.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_STYLE, "1"));
        mBatteryPercentTextEnabled = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT, false);
        mMtkPercentTextEnabled = Utils.isMtkDevice() ?
                Settings.Secure.getInt(mContext.getContentResolver(), 
                        SETTING_MTK_BATTERY_PERCENTAGE, 0) == 1 : false;
    }

    private void initLayout() {
        final String[] batteryPercentTextIds = new String[] { "percentage", "battery_text" };
        Resources res = mContext.getResources();

        // inject percent text if it doesn't exist
        for (String bptId : batteryPercentTextIds) {
            final int bptResId = res.getIdentifier(bptId, "id", PACKAGE_NAME);
            if (bptResId != 0) {
                View v = mContainer.findViewById(bptResId);
                if (v != null && v instanceof TextView) {
                    mPercentText = new StatusbarBatteryPercentage((TextView) v, mPrefs);
                    if (DEBUG) log("Battery percent text found as: " + bptId);
                    break;
                }
            }
        }
        if (mPercentText == null) {
            TextView percentTextView = new TextView(mContext);
            LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            percentTextView.setLayoutParams(lParams);
            percentTextView.setPadding(6, 0, 0, 0);
            percentTextView.setTextColor(Color.WHITE);
            percentTextView.setVisibility(View.GONE);
            mPercentText = new StatusbarBatteryPercentage(percentTextView, mPrefs);
            mSystemIcons.addView(mPercentText.getView(), mSystemIcons.getChildCount()-1);
            if (DEBUG) log("Battery percent text injected");
        }

        // inject circle battery view
        mCircleBattery = new CmCircleBattery(mContext);
        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lParams.gravity = Gravity.CENTER_VERTICAL;
        mCircleBattery.setLayoutParams(lParams);
        mCircleBattery.setPadding(6, 0, 0, 0);
        mCircleBattery.setVisibility(View.GONE);
        mSystemIcons.addView(mCircleBattery);
        if (DEBUG) log("CmCircleBattery injected");

        // find battery
        View stockBatteryView = mSystemIcons.findViewById(
                res.getIdentifier("battery", "id", PACKAGE_NAME));
        if (stockBatteryView != null) {
            mStockBattery = new StatusbarBattery(stockBatteryView);
        }
    }

    private void updateBatteryStyle() {
        try {
            if (mStockBattery != null) {
                if (mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_STOCK ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_STOCK_PERCENT) {
                    mStockBattery.getView().setVisibility(View.VISIBLE);
                    mStockBattery.setShowPercentage(mBatteryStyle == 
                            GravityBoxSettings.BATTERY_STYLE_STOCK_PERCENT);
                    
                } else {
                    mStockBattery.getView().setVisibility(View.GONE);
                }
            }

            if (mCircleBattery != null) {
                mCircleBattery.setVisibility((mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_PERCENT ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED ||
                        mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_DASHED_PERCENT) ?
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
                if (Utils.isMtkDevice()) {
                    mPercentText.update();
                }
                mPercentText.setVisibility(
                        (mBatteryPercentTextEnabled || mMtkPercentTextEnabled) ?
                                View.VISIBLE : View.GONE);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void createHooks() {
        if (Utils.isMtkDevice()) {
            Class<?> batteryControllerClass = XposedHelpers.findClass(CLASS_BATTERY_CONTROLLER,
                    mContext.getClassLoader());
            XposedHelpers.findAndHookMethod(batteryControllerClass, "onReceive", 
                    Context.class, Intent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    updateBatteryStyle();
                }
            });
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_STYLE_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_STYLE)) {
                    mBatteryStyle = intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_STYLE, 1);
                    if (DEBUG) log("mBatteryStyle changed to: " + mBatteryStyle);
                    updateBatteryStyle();
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT)) {
                    mBatteryPercentTextEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT, false);
                    if (DEBUG) log("mPercentText changed to: " + mBatteryPercentTextEnabled);
                    updateBatteryStyle();
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_SIZE_CHANGED) &&
                intent.hasExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_SIZE) && mPercentText != null) {
                    int textSize = intent.getIntExtra(GravityBoxSettings.EXTRA_BATTERY_PERCENT_TEXT_SIZE, 16);
                    mPercentText.setTextSize(textSize);
                    if (DEBUG) log("PercentText size changed to: " + textSize);
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_PERCENT_TEXT_STYLE_CHANGED)
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
        } else if (intent.getAction().equals(ACTION_MTK_BATTERY_PERCENTAGE_SWITCH)) {
            mMtkPercentTextEnabled = intent.getIntExtra(EXTRA_MTK_BATTERY_PERCENTAGE_STATE, 0) == 1;
            if (DEBUG) log("mMtkPercentText changed to: " + mMtkPercentTextEnabled);
            updateBatteryStyle();
        }
    }
}
