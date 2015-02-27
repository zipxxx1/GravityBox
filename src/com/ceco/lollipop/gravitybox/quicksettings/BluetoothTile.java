package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import android.provider.Settings;
import android.view.View;

public class BluetoothTile extends AospTile {
    public static final String AOSP_KEY = "bt";

    private Unhook mSupportsDualTargetsHook;

    protected BluetoothTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    public String getKey() {
        return "aosp_tile_bluetooth";
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
    public boolean handleLongClick(View view) {
        if (mNormalized) {
            view.setPressed(false);
            startSettingsActivity(Settings.ACTION_BLUETOOTH_SETTINGS);
            return true;
        }
        return false;
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
                    if (mNormalized) param.setResult(false);
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
