package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor.QsEventListener;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class AospTile extends BaseTile implements QsEventListener {

    protected Unhook mHandleUpdateStateHook;
    protected Unhook mHandleClickHook;

    public static AospTile create(Object host, Object tile, String aospKey, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        if (AirplaneModeTile.AOSP_KEY.equals(aospKey))
            return new AirplaneModeTile(host, tile, prefs, eventDistributor);
        else if (BluetoothTile.AOSP_KEY.equals(aospKey))
            return new BluetoothTile(host, tile, prefs, eventDistributor);
        else if (CastTile.AOSP_KEY.equals(aospKey))
            return new CastTile(host, tile, prefs, eventDistributor);
        else if (CellularTile.AOSP_KEY.equals(aospKey))
            return new CellularTile(host, aospKey, CellularTile.KEY, tile, prefs, eventDistributor);
        else if (CellularTile.MSIM_KEY1.equals(aospKey))
            return new CellularTile(host, aospKey, CellularTile.KEY, tile, prefs, eventDistributor);
        else if (CellularTile.MSIM_KEY2.equals(aospKey))
            return new CellularTile(host, aospKey, CellularTile.KEY2, tile, prefs, eventDistributor);
        else if (ColorInversionTile.AOSP_KEY.equals(aospKey))
            return new ColorInversionTile(host, tile, prefs, eventDistributor);
        else if (FlashlightTile.AOSP_KEY.equals(aospKey))
            return new FlashlightTile(host, tile, prefs, eventDistributor);
        else if (HotspotTile.AOSP_KEY.equals(aospKey))
            return new HotspotTile(host, tile, prefs, eventDistributor);
        else if (LocationTile.AOSP_KEY.equals(aospKey))
            return new LocationTile(host, tile, prefs, eventDistributor);
        else if (RotationLockTile.AOSP_KEY.equals(aospKey))
            return new RotationLockTile(host, tile, prefs, eventDistributor);
        else if (WifiTile.AOSP_KEY.equals(aospKey))
            return new WifiTile(host, tile, prefs, eventDistributor);
        else if (MtkAudioProfileTile.AOSP_KEY.equals(aospKey))
            return new MtkAudioProfileTile(host, tile, prefs, eventDistributor);
        else if (MtkMobileDataTile.AOSP_KEY.equals(aospKey))
            return new MtkMobileDataTile(host, tile, prefs, eventDistributor);
        else if (MtkHotKnotTile.AOSP_KEY.equals(aospKey))
            return new MtkHotKnotTile(host, tile, prefs, eventDistributor);

        return null;
    }

    protected AospTile(Object host, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mTile = tile;
        XposedHelpers.setAdditionalInstanceField(tile, BaseTile.TILE_KEY_NAME, mKey);

        createHooks();
        if (DEBUG) log(mKey + ": aosp tile wrapper created");
    }

    protected abstract String getClassName();
    public abstract String getAospKey();

    @Override
    public void handleUpdateState(Object state, Object arg) {
        final boolean visible = mEnabled &&
                (!mLocked || !mKgMonitor.isShowing()) &&
                (!mLockedOnly || mKgMonitor.isShowing()) &&
                (!mSecured || !(mKgMonitor.isShowing() && mKgMonitor.isLocked()));
        XposedHelpers.setBooleanField(state, "visible", visible);
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
        if (DEBUG) log(mKey + ": handleDestroy called");
    }

    private void createHooks() {
        try {
            if (DEBUG) log(mKey + ": Creating hooks");
            ClassLoader cl = mContext.getClassLoader();

            mHandleUpdateStateHook = XposedHelpers.findAndHookMethod(
                    getClassName(), cl,"handleUpdateState",
                    BaseTile.CLASS_TILE_STATE, Object.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        handleUpdateState(param.args[0], param.args[1]);
                    }
                }
            });

            mHandleClickHook = XposedHelpers.findAndHookMethod(
                    getClassName(), cl, "handleClick", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        handleClick();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mHandleUpdateStateHook != null) {
            mHandleUpdateStateHook.unhook();
            mHandleUpdateStateHook = null;
        }
        if (mHandleClickHook != null) {
            mHandleClickHook.unhook();
            mHandleClickHook = null;
        }
    }
}
