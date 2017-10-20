/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.nougat.gravitybox.quicksettings;

import com.ceco.nougat.gravitybox.quicksettings.QsTileEventDistributor.QsEventListener;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class AospTile extends BaseTile implements QsEventListener {

    protected Unhook mHandleUpdateStateHook;
    protected Unhook mHandleClickHook;
    protected Unhook mSetListeningHook;

    public static AospTile create(Object host, Object tile, String aospKey, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        // Stock tiles we modify
        if (AirplaneModeTile.AOSP_KEY.equals(aospKey))
            return new AirplaneModeTile(host, aospKey, tile, prefs, eventDistributor);
        else if (CastTile.AOSP_KEY.equals(aospKey))
            return new CastTile(host, aospKey, tile, prefs, eventDistributor);
        else if (CellularTile.AOSP_KEYS.contains(aospKey))
            return new CellularTile(host, aospKey, tile, prefs, eventDistributor);
        else if (HotspotTile.AOSP_KEY.equals(aospKey))
            return new HotspotTile(host, aospKey, tile, prefs, eventDistributor);
        else if (LocationTile.AOSP_KEY.equals(aospKey))
            return new LocationTile(host, aospKey, tile, prefs, eventDistributor);
        else if (DoNotDisturbTile.AOSP_KEY.equals(aospKey))
            return new DoNotDisturbTile(host, aospKey, tile, prefs, eventDistributor);
        else if (BatteryTile.AOSP_KEY.equals(aospKey))
            return new BatteryTile(host, aospKey, tile, prefs, eventDistributor);
        // Default wrapper for all other stock tiles we do not modify
        else
            return new AospTileDefault(host, aospKey, tile, prefs, eventDistributor);
    }

    protected AospTile(Object host, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);

        createHooks();
        if (DEBUG) log(mKey + ": aosp tile wrapper created");
    }

    protected final boolean disabledByPolicy() {
        return (mProtected && mKgMonitor.isShowing() && mKgMonitor.isLocked());
    }

    // Tiles can override click functionality
    // When true is returned, original click handler will be suppressed
    protected boolean onBeforeHandleClick() {
        return false;
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        final boolean disabledByPolicy =
                mProtected && mKgMonitor.isShowing() && mKgMonitor.isLocked();
        XposedHelpers.setBooleanField(state, "disabledByPolicy", disabledByPolicy);
    }

    @Override
    public void handleDestroy() {
        destroyHooks();
        super.handleDestroy();
        if (DEBUG) log(mKey + ": handleDestroy called");
    }

    private void createHooks() {
        try {
            if (DEBUG) log(mKey + ": Creating hooks");
            ClassLoader cl = mContext.getClassLoader();

            mHandleUpdateStateHook = XposedHelpers.findAndHookMethod(
                    mTile.getClass().getName(), cl,"handleUpdateState",
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
                    mTile.getClass().getName(), cl, "handleClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME)) &&
                            onBeforeHandleClick()) {
                        param.setResult(null);
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        handleClick();
                    }
                }
            });

            mSetListeningHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), cl,
                    "setListening", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        setListening((boolean)param.args[0]);
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
        if (mSetListeningHook != null) {
            mSetListeningHook.unhook();
            mSetListeningHook = null;
        }
    }
}
