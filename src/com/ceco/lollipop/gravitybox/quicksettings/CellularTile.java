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
    public static final String MSIM_KEY1 = "cell1";
    public static final String MSIM_KEY2 = "cell2";

    private String mAospKey;
    private Unhook mCreateTileViewHook;

    protected CellularTile(Object host, String aospKey, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);

        mAospKey = aospKey;
        createHooks();
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.CellularTile";
    }

    @Override
    public String getAospKey() {
        return mAospKey;
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
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        onCreateTileView((View)param.getResult());
                    }
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
