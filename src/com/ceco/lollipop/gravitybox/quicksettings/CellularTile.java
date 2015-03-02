package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.view.View;

public class CellularTile extends AospTile {
    public static final String AOSP_KEY = "cell";

    private Unhook mCreateTileViewHook;

    protected CellularTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    public String getKey() {
        return "aosp_tile_cell";
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.CellularTile";
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick(View view) {
        return false;
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
        try {
            mCreateTileViewHook = XposedHelpers.findAndHookMethod(getClassName(), mContext.getClassLoader(),
                    "createTileView", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onCreateTileView((View)param.getResult());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mCreateTileViewHook != null) {
            mCreateTileViewHook.unhook();
            mCreateTileViewHook = null;
        }
    }
}
