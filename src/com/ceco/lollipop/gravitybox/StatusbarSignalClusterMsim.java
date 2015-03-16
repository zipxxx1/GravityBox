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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.ceco.lollipop.gravitybox.ModStatusBar.ContainerType;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager.ColorInfo;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMsim extends StatusbarSignalCluster {
    protected Object mNetworkControllerCallback2;
    protected SignalActivity[] mMobileActivity;
    protected boolean mHideSimLabels;

    public StatusbarSignalClusterMsim(ContainerType containerType, LinearLayout view) {
        super(containerType, view);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();
        mHideSimLabels = sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_HIDE_SIM_LABELS, false);
    }

    @Override
    protected void createHooks() {
        try {
            XposedHelpers.findAndHookMethod(mView.getClass(), "apply", 
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mView != param.thisObject) return;
                    apply((Integer) param.args[0]);
                }
            });

            if (mDataActivityEnabled) {
                try {
                    XposedHelpers.findAndHookMethod(mView.getClass(), "onAttachedToWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (mView != param.thisObject) return;

                            View v = (View) XposedHelpers.getObjectField(mView, "mWifiSignalView");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mWifiActivity = new SignalActivity((FrameLayout)v.getParent(), SignalType.WIFI);
                                if (DEBUG) log("onAttachedToWindow: mWifiActivity created");
                            }

                            if (mMobileActivity == null) {
                                mMobileActivity = new SignalActivity[2];
                            }

                            Object mtm = XposedHelpers.callStaticMethod(
                                    XposedHelpers.findClass("android.telephony.TelephonyManager", null),
                                        "getDefault");
                            int j = (Integer) XposedHelpers.callMethod(mtm, "getPhoneCount");
                            for (int i=0; i < j; i++) {
                                v = (View) ((View[])XposedHelpers.getObjectField(mView, "mMobileSignalView"))[i];
                                if (v != null && v.getParent() instanceof FrameLayout) {
                                    mMobileActivity[i] = new SignalActivity((FrameLayout)v.getParent(), SignalType.MOBILE,
                                            Gravity.BOTTOM | Gravity.END);
                                    if (DEBUG) log("onAttachedToWindow: mMobileActivity" + i + " created");
                                }
                            }
                        }
                    });

                    XposedHelpers.findAndHookMethod(mView.getClass(), "onDetachedFromWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (mView != param.thisObject) return;

                            mWifiActivity = null;
                            if (mMobileActivity != null) {
                                for (int i=0; i < mMobileActivity.length; i++) {
                                    mMobileActivity[i] = null;
                                }
                            }
                            if (DEBUG) log("onDetachedFromWindow: signal activities destoyed");
                        }
                    });
                } catch (Throwable t) {
                    log("Error hooking SignalActivity related methods: " + t.getMessage());
                }
            }

        } catch (Throwable t) {
            log("Error hooking apply() method: " + t.getMessage());
        }
    }

    @Override
    protected void setNetworkController(Object networkController) {
        final ClassLoader classLoader = mView.getClass().getClassLoader();
        final Class<?> networkCtrlCbClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback", 
                classLoader);
        final Object mtm = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.telephony.TelephonyManager", null),
                    "getDefault");
        final int j = (Integer) XposedHelpers.callMethod(mtm, "getPhoneCount");
        for (int i=0; i < j; i++) {
            XposedHelpers.callMethod(networkController, "addNetworkSignalChangedCallback",
                    Proxy.newProxyInstance(classLoader, new Class<?>[] { networkCtrlCbClass },
                        new NetworkControllerCallbackMsim()), i);
        }
        if (DEBUG) log("setNetworkController: callback registered");
    }

    @Override
    protected void apply(int simSlot) {
        try {
            boolean doApply = true;
            if (mFldWifiGroup != null) {
                doApply = mFldWifiGroup.get(mView) != null;
            }
            if (doApply) {
                if (mIconManager != null && mIconManager.isColoringEnabled()) {
                    updateWiFiIcon();
                    if (!XposedHelpers.getBooleanField(mView, "mIsAirplaneModeEnabled")) {
                        updateMobileIcon(simSlot);
                    }
                    if (DEBUG) log("Signal icon colors updated");
                }
                updateAirplaneModeIcon();
            }
        } catch (Throwable t) {
            logAndMute("apply", t);
        }

        if (mHideSimLabels) {
            hideSimLabel(simSlot);
        }
    }

    @Override
    protected void update() {
        if (mView != null) {
            try {
                Object mtm = XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.telephony.TelephonyManager", null),
                            "getDefault");
                int j = (Integer) XposedHelpers.callMethod(mtm, "getPhoneCount");
                for (int i=0; i < j; i++) {
                    XposedHelpers.callMethod(mView, "apply", i);
                }
            } catch (Throwable t) {
                logAndMute("invokeApply", t);
            }
        }
    }

    @Override
    protected void updateMobileIcon(int simSlot) {
        try {
            boolean mobileVisible = ((boolean[])XposedHelpers.getObjectField(mView, "mMobileVisible"))[simSlot];
            if (DEBUG) log("Mobile visible for slot " + simSlot + ": " + mobileVisible);
            if (mobileVisible && mIconManager != null &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView mobile = (ImageView) ((ImageView[])XposedHelpers.getObjectField(
                        mView, "mMobileSignalView"))[simSlot];
                if (mobile != null) {
                    int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileSignalIconId"))[simSlot];
                    Drawable d = mIconManager.getMobileIcon(simSlot, resId, true);
                    if (d != null) mobile.setImageDrawable(d);
                }
                if (mIconManager.isMobileIconChangeAllowed()) {
                    ImageView mobileType = (ImageView) ((ImageView[])XposedHelpers.getObjectField(
                            mView, "mMobileTypeView"))[simSlot];
                    if (mobileType != null) {
                        try {
                            int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileTypeIconId"))[simSlot];
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyColorFilter(simSlot, d);
                            mobileType.setImageDrawable(d);
                        } catch (Resources.NotFoundException e) { 
                            mobileType.setImageDrawable(null);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logAndMute("updateMobileIcon", t);
        }
    }

    private void hideSimLabel(int simSlot) {
        try {
            View simLabel = (View) ((View[])XposedHelpers.getObjectField(mView, "mMobilePhoneView"))[simSlot];
            if (simLabel != null) {
                simLabel.setVisibility(View.GONE);
            }
        } catch (Throwable t) {
            logAndMute("hideSimLabel", t);
        }
    }

    @Override
    protected void updateWiFiIcon() {
        try {
            if (XposedHelpers.getBooleanField(mView, "mWifiVisible") && mIconManager != null &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView wifiIcon = (ImageView) XposedHelpers.getObjectField(mView, "mWifiSignalView");
                if (wifiIcon != null) {
                    int resId = XposedHelpers.getIntField(mView, "mWifiSignalIconId");
                    Drawable d = mIconManager.getWifiIcon(resId);
                    if (d != null) wifiIcon.setImageDrawable(d);
                }
            }
        } catch (Throwable t) {
            logAndMute("updateWiFiIcon", t);
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        super.onIconManagerStatusChanged(flags, colorInfo);

        if ((flags & StatusBarIconManager.FLAG_DATA_ACTIVITY_COLOR_CHANGED) != 0 &&
                    mDataActivityEnabled && mMobileActivity != null) {
            if (mMobileActivity != null) {
                for (int i=0; i < mMobileActivity.length; i++) {
                    if (mMobileActivity[i] != null) mMobileActivity[i].updateDataActivityColor();
                }
            }
        }
    }

    protected class NetworkControllerCallbackMsim implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();

            try {
                if (methodName.equals("onWifiSignalChanged")) {
                    if (DEBUG) {
                        log("WiFi enabled: " + args[0]);
                        log("WiFi activity in: " + (Boolean)args[4]);
                        log("WiFi activity out: " + (Boolean)args[5]);
                    }
                    if (mWifiActivity != null) {
                        mWifiActivity.update((Boolean)args[0],
                                (Boolean)args[4], (Boolean)args[5]);
                    }
                } else if (methodName.equals("onMobileDataSignalChanged")) {
                    if (DEBUG) {
                        log("Mobile SIM slot: " + args[22]);
                        log("Mobile data enabled: " + args[0]);
                        log("Mobile data activity in: " + (Boolean)args[7]);
                        log("Mobile data activity out: " + (Boolean)args[8]);
                    }
                    int simSlot = (int) args[22];
                    if (mMobileActivity != null && mMobileActivity[simSlot] != null) {
                        mMobileActivity[simSlot].update((Boolean)args[0], 
                                (Boolean)args[7], (Boolean)args[8]);
                    }
                }
            } catch (Throwable t) {
                logAndMute("NetworkControllerCallback", t);
            }

            return null;
        }
    }
}
