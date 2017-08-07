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

import java.util.Arrays;
import java.util.List;

import com.ceco.nougat.gravitybox.ConnectivityServiceWrapper;
import com.ceco.nougat.gravitybox.GravityBoxSettings;
import com.ceco.nougat.gravitybox.Utils;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class CellularTile extends AospTile {
    public static final String AOSP_KEY = "cell";
    public static final String MSIM_KEY1 = "cell1";
    public static final String MSIM_KEY2 = "cell2";
    public static final List<String> AOSP_KEYS = Arrays.asList(AOSP_KEY, MSIM_KEY1, MSIM_KEY2);

    public static enum DataToggle { DISABLED, SINGLEPRESS, LONGPRESS };

    private static final Intent CELLULAR_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.phone", "com.android.phone.MobileNetworkSettings"));

    private boolean mClickHookBlocked;
    private DataToggle mDataToggle;

    protected CellularTile(Object host, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);
    }

    private boolean isPrimary() {
        return AOSP_KEY.equals(mKey) ||
                MSIM_KEY1.equals(mKey);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();

        mDataToggle = Utils.isOxygenOs35Rom() ? DataToggle.valueOf("DISABLED") :
                DataToggle.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_CELL_TILE_DATA_TOGGLE, "DISABLED"));
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CELL_TILE_DATA_TOGGLE)) {
                mDataToggle = DataToggle.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_CELL_TILE_DATA_TOGGLE));
            }
        }
    }

    @Override
    public String getSettingsKey() {
        return "aosp_tile_cell";
    }

    private void toggleMobileData() {
        if (!isPrimary()) {
            showDetail();
            return;
        }

        // toggle mobile data
        Intent intent = new Intent(ConnectivityServiceWrapper.ACTION_TOGGLE_MOBILE_DATA);
        mContext.sendBroadcast(intent);
    }

    @Override
    protected boolean onBeforeHandleClick() {
        if (Utils.isOxygenOs35Rom())
            return false;

        if (mClickHookBlocked) {
            mClickHookBlocked = false;
            return false;
        }

        if (mDataToggle == DataToggle.SINGLEPRESS) {
            toggleMobileData();
        } else {
            showDetail();
        }
 
        return true;
    }

    @Override
    public boolean handleLongClick() {
        if (mDataToggle == DataToggle.LONGPRESS) {
            toggleMobileData();
        } else if (mDataToggle != DataToggle.DISABLED){
            return showDetail();
        } else {
            startSettingsActivity(CELLULAR_SETTINGS);
        }
        return true;
    }

    private boolean showDetail() {
        try {
            mClickHookBlocked = true;
            XposedHelpers.callMethod(mTile, "handleClick");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mDataToggle = null;
    }
}
