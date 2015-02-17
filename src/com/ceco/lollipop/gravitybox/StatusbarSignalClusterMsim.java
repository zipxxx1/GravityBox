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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
    protected static String[] sMobileViewNames = new String[] { "mMobileStrengthView", "mMobileStrengthView2" };
    protected static String[] sMobileTypeViewNames = new String[] { "mMobileTypeView", "mMobileTypeView2" };

    protected Object mNetworkControllerCallback2;
    protected SignalActivity[] mMobileActivity;
    protected boolean mSignalIconAutohide;
    protected boolean mHideSimLabels;

    public StatusbarSignalClusterMsim(ContainerType containerType, LinearLayout view) {
        super(containerType, view);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();
        mSignalIconAutohide = sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_ICON_AUTOHIDE, false);
        mHideSimLabels = sPrefs.getBoolean(GravityBoxSettings.PREF_KEY_SIGNAL_CLUSTER_HIDE_SIM_LABELS, false);
    }

    @Override
    protected void createHooks() {
        try {
            XposedHelpers.findAndHookMethod(mView.getClass(), "applySubscription", 
                    int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mView != param.thisObject) return;
                    if (mSignalIconAutohide) {
                        int simSlot = (Integer) param.args[0];
                        ((boolean[])XposedHelpers.getObjectField(mView, "mMobileVisible"))[simSlot] =
                                PhoneWrapper.isMsimCardInserted(simSlot);
                    }
                }
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

                            View v = (View) XposedHelpers.getObjectField(mView, "mWifiStrengthView");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mWifiActivity = new SignalActivity((FrameLayout)v.getParent(), SignalType.WIFI);
                                if (DEBUG) log("onAttachedToWindow: mWifiActivity created");
                            }

                            if (mMobileActivity == null) {
                                mMobileActivity = new SignalActivity[2];
                            }
                            v = (View) XposedHelpers.getObjectField(mView, "mMobileStrengthView");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mMobileActivity[0] = new SignalActivity((FrameLayout)v.getParent(), SignalType.MOBILE,
                                        Gravity.BOTTOM | Gravity.END);
                                if (DEBUG) log("onAttachedToWindow: mMobileActivity created");
                            }

                            v = (View) XposedHelpers.getObjectField(mView, "mMobileStrengthView2");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mMobileActivity[1] = new SignalActivity((FrameLayout)v.getParent(), SignalType.MOBILE,
                                        Gravity.BOTTOM | Gravity.END);
                                if (DEBUG) log("onAttachedToWindow: mMobileActivity2 created");
                            }
                        }
                    });

                    XposedHelpers.findAndHookMethod(mView.getClass(), "onDetachedFromWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (mView != param.thisObject) return;

                            mWifiActivity = null;
                            if (mMobileActivity != null) {
                                mMobileActivity[0] = mMobileActivity[1] = null;
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
                "com.android.systemui.statusbar.policy.MSimNetworkController.MSimNetworkSignalChangedCallback", 
                classLoader);
        mNetworkControllerCallback = Proxy.newProxyInstance(classLoader, 
                new Class<?>[] { networkCtrlCbClass }, new NetworkControllerCallbackMsim(networkController));
        XposedHelpers.callMethod(networkController, "addNetworkSignalChangedCallback",
                mNetworkControllerCallback, 0);
        mNetworkControllerCallback2 = Proxy.newProxyInstance(classLoader, 
                new Class<?>[] { networkCtrlCbClass }, new NetworkControllerCallbackMsim(networkController));
        XposedHelpers.callMethod(networkController, "addNetworkSignalChangedCallback",
                mNetworkControllerCallback2, 1);
        if (DEBUG) log("setNetworkController: callback registered");
    }

    @Override
    protected void apply(int simSlot) {
        super.apply(simSlot);
        if (mHideSimLabels) {
            hideSimLabel(simSlot);
        }
    }

    @Override
    protected void update() {
        if (mView != null) {
            try {
                XposedHelpers.callMethod(mView, "applySubscription", 0);
                XposedHelpers.callMethod(mView, "applySubscription", 1);
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
                ImageView mobile = (ImageView) XposedHelpers.getObjectField(mView, sMobileViewNames[simSlot]);
                if (mobile != null) {
                    int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileStrengthIconId"))[simSlot];
                    Drawable d = mIconManager.getMobileIcon(simSlot, resId, true);
                    if (d != null) mobile.setImageDrawable(d);
                }
                if (mIconManager.isMobileIconChangeAllowed()) {
                    ImageView mobileType = (ImageView) XposedHelpers.getObjectField(mView, sMobileTypeViewNames[simSlot]);
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
            String fieldName = simSlot == 0 ? "mMobileSlotLabelView" : "mMobileSlotLabelView2";
            View simLabel = (View) XposedHelpers.getObjectField(mView, fieldName);
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
                ImageView wifiIcon = (ImageView) XposedHelpers.getObjectField(mView, "mWifiStrengthView");
                if (wifiIcon != null) {
                    int resId = XposedHelpers.getIntField(mView, "mWifiStrengthIconId");
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
            if (mMobileActivity[0] != null) {
                mMobileActivity[0].updateDataActivityColor();
            }
            if (mMobileActivity[1] != null) {
                mMobileActivity[1].updateDataActivityColor();
            }
        }
    }

    protected class NetworkControllerCallbackMsim implements InvocationHandler {
        private Object mController;

        public NetworkControllerCallbackMsim(Object controller) {
            mController = controller;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            try {
                if (methodName.equals("onWifiSignalChanged")) {
                    if (mWifiActivity != null) {
                        mWifiActivity.update((Boolean)args[0], 
                                (Boolean)args[3], (Boolean)args[4]);
                    }
                } else if (methodName.equals("onMobileDataSignalChanged")) {
                    if (mMobileActivity != null) {
                        int slot = PhoneWrapper.getMsimPreferredDataSubscription();
                        boolean in = ((boolean[]) XposedHelpers.getObjectField(mController, "mMSimDataActivityIn"))[slot];
                        boolean out = ((boolean[]) XposedHelpers.getObjectField(mController, "mMSimDataActivityOut"))[slot];
                        if (DEBUG) log("NetworkControllerCallbackMsim: onMobileDataSignalChanged " + 
                                slot + "; enabled:" + args[0] + "; in:" + in + "; out:" + out);
                        if (mMobileActivity[slot] != null) {
                            mMobileActivity[slot].update((Boolean)args[0], in, out);
                        }
                    }
                }
            } catch (Throwable t) {
                logAndMute("NetworkControllerCallbackMsim", t);
            }

            return null;
        }
    }
}
