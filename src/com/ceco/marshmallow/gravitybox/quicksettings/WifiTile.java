package com.ceco.marshmallow.gravitybox.quicksettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.provider.Settings;
import android.view.View;

public class WifiTile extends AospTile {
    public static final String AOSP_KEY = "wifi";
    public static final String KEY = "aosp_tile_wifi";

    private Unhook mCreateTileViewHook;
    private Unhook mSupportsDualTargetsHook;

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
    public boolean handleLongClick() {
        if (!mDualMode) {
            XposedHelpers.callMethod(mTile, "handleSecondaryClick");
        } else {
            startSettingsActivity(Settings.ACTION_WIFI_SETTINGS);
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
            final ClassLoader cl = mContext.getClassLoader();

            mCreateTileViewHook = XposedHelpers.findAndHookMethod(getClassName(), 
                    cl, "createTileView", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onCreateTileView((View)param.getResult());
                }
            });

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
        if (mCreateTileViewHook != null) {
            mCreateTileViewHook.unhook();
            mCreateTileViewHook = null;
        }
    }
}
