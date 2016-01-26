package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.Utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.view.View;

public class WifiTile extends AospTile {
    public static final String AOSP_KEY = "wifi";
    public static final String KEY = "aosp_tile_wifi";

    private Unhook mCreateTileViewHook;
    private Unhook mSupportsDualTargetsHook;
    private Unhook mHandleSecondaryClickHook;
    private boolean mEnableDetailView;

    protected WifiTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, KEY, tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.WifiTile";
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();

        mEnableDetailView = Build.VERSION.SDK_INT == 21 &&
                mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QS_ENABLE_DETAIL_VIEW, false);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) { 
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_ENABLE_DETAIL_VIEW)) {
                mEnableDetailView = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_QS_ENABLE_DETAIL_VIEW, false);
            }
        }
    }

    @Override
    public boolean handleLongClick() {
        if (!mDualMode) {
            XposedHelpers.callMethod(mTile, "handleSecondaryClick");
        } else {
            startSettingsActivity(Settings.ACTION_WIFI_SETTINGS);
        }
        return true;
    }

    // SDK21 only
    @Override
    public boolean handleSecondaryClick() {
        if(!mEnableDetailView) return false;

        if (!canConfigWifi()) {
            startSettingsActivity(Settings.ACTION_WIFI_SETTINGS);
            return true;
        }

        if (!isWifiEnabled()) {
            setWifiEnabled(true);
        }
        showDetail(true);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
            final ClassLoader cl = mContext.getClassLoader();

        try {
            mCreateTileViewHook = XposedHelpers.findAndHookMethod(getClassName(), 
                    cl, "createTileView", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onCreateTileView((View)param.getResult());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            mSupportsDualTargetsHook = XposedHelpers.findAndHookMethod(getClassName(), 
                    cl, "supportsDualTargets", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(mDualMode);
                }
            });
        } catch (Throwable t) {
            log(getKey() + ": Your system does not seem to support standard AOSP dual mode");
        }

        try {
            if (Build.VERSION.SDK_INT == 21) {
                mHandleSecondaryClickHook = XposedHelpers.findAndHookMethod(getClassName(),
                        cl, "handleSecondaryClick", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (handleSecondaryClick()) {
                            param.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean canConfigWifi() {
        try {
            UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            return !(boolean) XposedHelpers.callMethod(um, "hasUserRestriction",
                    UserManager.DISALLOW_CONFIG_WIFI, Utils.getUserHandle(
                            Utils.getCurrentUser()));
        } catch (Throwable t) {
            XposedBridge.log(t);
            return true;
        }
    }

    private boolean isWifiEnabled() {
        try {
            Object state = XposedHelpers.getObjectField(mTile, "mState");
            return XposedHelpers.getBooleanField(state, "enabled");
        } catch (Throwable t) {
            return false;
        }
    }

    private void setWifiEnabled(boolean enabled) {
        try {
            Object ctrl = XposedHelpers.getObjectField(mTile, "mController");
            XposedHelpers.callMethod(ctrl, "setWifiEnabled", enabled);
            Object state = XposedHelpers.getObjectField(mTile, "mState");
            XposedHelpers.setBooleanField(state, "enabled", enabled);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mSupportsDualTargetsHook != null) {
            mSupportsDualTargetsHook.unhook();
            mSupportsDualTargetsHook = null;
        }
        if (mCreateTileViewHook != null) {
            mCreateTileViewHook.unhook();
            mCreateTileViewHook = null;
        }
        if (mHandleSecondaryClickHook != null) {
            mHandleSecondaryClickHook.unhook();
            mHandleSecondaryClickHook = null;
        }
    }
}
