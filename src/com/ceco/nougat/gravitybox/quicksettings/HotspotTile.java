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

import com.ceco.nougat.gravitybox.Utils;

import android.content.ComponentName;
import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook.Unhook;

public class HotspotTile extends AospTile {
    public static final String AOSP_KEY = "hotspot";

    private static final Intent TETHER_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.TetherSettings"));

    private Unhook mLongClickHook;

    protected HotspotTile(Object host, String aospKey, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, aospKey, tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    public String getSettingsKey() {
        return "aosp_tile_hotspot";
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(TETHER_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
        if (Utils.isOxygenOs35Rom())
            return;

        try {
            mLongClickHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), 
                    mContext.getClassLoader(), "handleLongClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (handleLongClick()) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mLongClickHook != null) {
            mLongClickHook.unhook();
            mLongClickHook = null;
        }
    }
}
