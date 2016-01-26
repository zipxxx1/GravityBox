package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook.Unhook;

public class BluetoothTile extends AospTile {
    public static final String AOSP_KEY = "bt";
    public static final String KEY = "aosp_tile_bluetooth";

    private Unhook mSupportsDualTargetsHook;
    private Unhook mHandleSecondaryClickHook;
    private boolean mEnableDetailView;

    protected BluetoothTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, KEY, tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.BluetoothTile";
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
            startSettingsActivity(Settings.ACTION_BLUETOOTH_SETTINGS);
        }
        return true;
    }

    // SDK21 only
    @Override
    public boolean handleSecondaryClick() {
        if(!mEnableDetailView) return false;

        if (!isBtEnabled()) {
            setBtEnabled(true);
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
        ClassLoader cl = mContext.getClassLoader();

        // this seems to be unsupported on custom ROMs. Log one line and continue.
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

    private boolean isBtEnabled() {
        try {
            Object state = XposedHelpers.getObjectField(mTile, "mState");
            return XposedHelpers.getBooleanField(state, "value");
        } catch (Throwable t) {
            return false;
        }
    }

    private void setBtEnabled(boolean enabled) {
        try {
            Object state = XposedHelpers.getObjectField(mTile, "mState");
            XposedHelpers.setBooleanField(state, "value", enabled);
            Object ctrl = XposedHelpers.getObjectField(mTile, "mController");
            XposedHelpers.callMethod(ctrl, "setBluetoothEnabled", enabled);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mSupportsDualTargetsHook != null) {
            mSupportsDualTargetsHook.unhook();
            mSupportsDualTargetsHook = null;
        }
        if (mHandleSecondaryClickHook != null) {
            mHandleSecondaryClickHook.unhook();
            mHandleSecondaryClickHook = null;
        }
    }
}
