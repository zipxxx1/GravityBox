package com.ceco.kitkat.gravitybox;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMsim extends StatusbarSignalCluster {
    protected static String[] sMobileViewNames = new String[] { "mMobileStrengthView", "mMobileStrengthView2" };
    protected static String[] sMobileTypeViewNames = new String[] { "mMobileTypeView", "mMobileTypeView2" };

    public StatusbarSignalClusterMsim(LinearLayout view, StatusBarIconManager iconManager) {
        super(view, iconManager);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();
        mConnectionStateEnabled = false;
        mDataActivityEnabled = false;
    }

    @Override
    protected void createHooks() {
        try {
            XposedHelpers.findAndHookMethod(mView.getClass(), "applySubscription", 
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    apply((Integer) param.args[0]);
                }
            });
        } catch (Throwable t) {
            log("Error hooking apply() method: " + t.getMessage());
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
            if (mobileVisible &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView mobile = (ImageView) XposedHelpers.getObjectField(mView, sMobileViewNames[simSlot]);
                if (mobile != null) {
                    int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileStrengthIconId"))[simSlot];
                    Drawable d = mIconManager.getMobileIcon(resId, true);
                    if (d != null) mobile.setImageDrawable(d);
                }
                if (mIconManager.isMobileIconChangeAllowed()) {
                    ImageView mobileType = (ImageView) XposedHelpers.getObjectField(mView, sMobileTypeViewNames[simSlot]);
                    if (mobileType != null) {
                        try {
                            int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileTypeIconId"))[simSlot];
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyColorFilter(d);
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

    @Override
    protected void updateWiFiIcon() {
        try {
            if (XposedHelpers.getBooleanField(mView, "mWifiVisible") &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView wifiIcon = (ImageView) XposedHelpers.getObjectField(mView, "mWifiStrengthView");
                if (wifiIcon != null) {
                    int resId = XposedHelpers.getIntField(mView, "mWifiStrengthIconId");
                    Drawable d = mIconManager.getWifiIcon(resId, true);
                    if (d != null) wifiIcon.setImageDrawable(d);
                }
            }
        } catch (Throwable t) {
            logAndMute("updateWiFiIcon", t);
        }
    }
}
