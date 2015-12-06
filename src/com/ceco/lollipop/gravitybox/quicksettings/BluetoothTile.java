package com.ceco.lollipop.gravitybox.quicksettings;

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
    public boolean handleLongClick() {
        if (!mDualMode) {
            XposedHelpers.callMethod(mTile, "handleSecondaryClick");
        } else {
            startSettingsActivity(Settings.ACTION_BLUETOOTH_SETTINGS);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
        try {
            mSupportsDualTargetsHook = XposedHelpers.findAndHookMethod(getClassName(), 
                    mContext.getClassLoader(), "supportsDualTargets", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(mDualMode);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mSupportsDualTargetsHook != null) {
            mSupportsDualTargetsHook.unhook();
            mSupportsDualTargetsHook = null;
        }
    }
}
