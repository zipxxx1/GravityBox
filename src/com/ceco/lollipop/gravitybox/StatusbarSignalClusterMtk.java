package com.ceco.lollipop.gravitybox;

import com.ceco.lollipop.gravitybox.ModStatusBar.ContainerType;

import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMtk extends StatusbarSignalCluster {
    protected boolean mRoamingIndicatorsDisabled;
    protected boolean mDisableDataNetworkTypeIcons;
    protected static ImageView[] mMobileRoam = null;

    public StatusbarSignalClusterMtk(ContainerType containerType, LinearLayout view) {
        super(containerType, view);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_DISABLE_ROAMING_INDICATORS_CHANGED)) {
            mRoamingIndicatorsDisabled = intent.getBooleanExtra(
                    GravityBoxSettings.EXTRA_INDICATORS_DISABLED, false);
            update();
        } else if(intent.getAction().equals(GravityBoxSettings.ACTION_DISABLE_DATA_NETWORK_TYPE_ICONS_CHANGED)
                && intent.hasExtra(GravityBoxSettings.EXTRA_DATA_NETWORK_TYPE_ICONS_DISABLED)) {
            mDisableDataNetworkTypeIcons = intent.getBooleanExtra(
                    GravityBoxSettings.EXTRA_DATA_NETWORK_TYPE_ICONS_DISABLED, false);
            update();
        }
    }

    @Override
    public void initPreferences() {
        super.initPreferences();

        mRoamingIndicatorsDisabled = sPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_DISABLE_ROAMING_INDICATORS, false);
        mDataActivityEnabled = false;
        mDisableDataNetworkTypeIcons = sPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_DISABLE_DATA_NETWORK_TYPE_ICONS, false);
    }

    @Override
    protected void apply(int simSlot) {
        try {
            super.apply();
            updateRoamingIndicator();
        } catch (Throwable t) {
            logAndMute("apply", t);
        }
    }

    protected void updateRoamingIndicator() {
        try {
            if (mRoamingIndicatorsDisabled) {
                Object mobileRoam = XposedHelpers.getObjectField(mView, "mMobileRoam");
                if (mMobileRoam == null) {
                    mMobileRoam = (mobileRoam instanceof ImageView) ?
                            new ImageView[] { (ImageView) mobileRoam, Utils.hasGeminiSupport() ?
                                    (ImageView) XposedHelpers.getObjectField(mView, "mMobileRoamGemini") : null } :
                                        (ImageView[]) mobileRoam;
                } else {
                    for (ImageView iv : mMobileRoam) {
                        if (iv != null) {
                            iv.setVisibility(View.GONE);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logAndMute("updateRoamingIndicator", t);
        }
    }
}
