package com.ceco.gm2.gravitybox;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMt65x2 extends StatusbarSignalClusterMtk {

    public StatusbarSignalClusterMt65x2(LinearLayout view, StatusBarIconManager iconManager) {
        super(view, iconManager);
    }

    @Override
    protected void updateWiFiIcon() {
        try {
            Object wifiStrengthId = null, wifiActivityId = null;
            int resId = 0;
            if (XposedHelpers.getBooleanField(mView, "mWifiVisible") &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView wifiIcon = (ImageView) XposedHelpers.getObjectField(mView, "mWifi");
                if (wifiIcon != null) {
                    wifiStrengthId = (Object) XposedHelpers.getObjectField(mView, "mWifiStrengthId");
                    if (wifiStrengthId instanceof Integer) {
                        resId = (Integer) wifiStrengthId;
                    } else {
                        resId = (Integer) XposedHelpers.callMethod(wifiStrengthId, "getIconId");
                    }
                    Drawable d = mIconManager.getWifiIcon(resId);
                    if (d != null) wifiIcon.setImageDrawable(d);
                }
                ImageView wifiActivity = (ImageView) XposedHelpers.getObjectField(mView, "mWifiActivity");
                if (wifiActivity != null) {
                    try {
                        wifiActivityId = (Object) XposedHelpers.getObjectField(mView, "mWifiActivityId");
                        if (wifiStrengthId instanceof Integer) {
                            resId = (Integer) wifiActivityId;
                        } else {
                            resId = (Integer) XposedHelpers.callMethod(wifiActivityId, "getIconId");
                        }
                        Drawable d = mResources.getDrawable(resId).mutate();
                        d = mIconManager.applyDataActivityColorFilter(d);
                        wifiActivity.setImageDrawable(d);
                    } catch (Resources.NotFoundException e) {
                        wifiActivity.setImageDrawable(null);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    protected void updateMobileIcon() {
        updateMobileIcon(0);
        updateMobileIcon(1);
    }

    private void updateMobileIcon(int slot) {
        try {
            boolean mobileVisible = ((boolean[])XposedHelpers.getObjectField(mView, "mMobileVisible"))[slot];
            if (mobileVisible &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView mobile = ((ImageView[])XposedHelpers.getObjectField(mView, "mMobile"))[slot];
                if (mobile != null) {
                    Object[][] mobileIconIds = (Object[][]) XposedHelpers.getObjectField(mView, "mMobileStrengthId");
                    int resId = (Integer) XposedHelpers.callMethod(mobileIconIds[slot][0], "getIconId");
                    Drawable d = mIconManager.getMobileIcon(slot, resId);
                    if (d != null) mobile.setImageDrawable(d);
                }
                if (mIconManager.isMobileIconChangeAllowed(slot)) {
                    ImageView mobileActivity = 
                            ((ImageView[])XposedHelpers.getObjectField(mView, "mMobileActivity"))[slot];
                    if (mobileActivity != null) {
                        try {
                            Object[] mobileActivityIds = 
                                    (Object[]) XposedHelpers.getObjectField(mView, "mMobileActivityId");
                            int resId = (Integer) XposedHelpers.callMethod(mobileActivityIds[slot], "getIconId");
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyDataActivityColorFilter(slot, d);
                            mobileActivity.setImageDrawable(d);
                        } catch (Resources.NotFoundException e) { 
                            mobileActivity.setImageDrawable(null);
                        }
                    }
                    ImageView mobileType = ((ImageView[])XposedHelpers.getObjectField(mView, "mMobileType"))[slot];
                    if (mobileType != null) {
                        try {
                            Object[] mobileTypeIds = (Object[]) XposedHelpers.getObjectField(mView, "mMobileTypeId");
                            int resId = (Integer) XposedHelpers.callMethod(mobileTypeIds[slot], "getIconId");
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyColorFilter(slot, d);
                            mobileType.setImageDrawable(d);
                        } catch (Resources.NotFoundException e) { 
                            mobileType.setImageDrawable(null);
                        }
                    }
                    boolean roaming = ((boolean[]) XposedHelpers.getObjectField(mView, "mRoaming"))[slot];
                    if (roaming) {
                        ImageView mobileRoam = 
                                ((ImageView[])XposedHelpers.getObjectField(mView, "mMobileRoam"))[slot];
                        if (mobileRoam != null) {
                            try {
                                int resId = ((int[]) XposedHelpers.getObjectField(mView, "mRoamingId"))[slot];
                                Drawable d = mResources.getDrawable(resId).mutate();
                                d = mIconManager.applyColorFilter(slot, d);
                                mobileRoam.setImageDrawable(d);
                            } catch (Resources.NotFoundException e) { 
                                mobileRoam.setImageDrawable(null);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    protected void updateRoamingIndicator() {
        try {
            if (mRoamingIndicatorsDisabled) {
                ImageView[] mobileRoam = (ImageView[]) XposedHelpers.getObjectField(mView, "mMobileRoam");
                if (mobileRoam != null) {
                    for (ImageView iv : mobileRoam) {
                        if (iv != null) {
                            iv.setVisibility(View.GONE);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
