/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.pie.gravitybox.quicksettings;

import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import com.ceco.pie.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;

public class OpScreenRefreshRateTile extends QsTile {
    public static final class Service extends QsTileServiceBase {
        static final String KEY = OpScreenRefreshRateTile.class.getSimpleName()+"$Service";
    }

    private static final String SETTING_SCREEN_REFRESH_RATE = "oneplus_screen_refresh_rate";
    private static final int MODE_90HZ = 0;
    private static final int MODE_60HZ = 1;
    private static final int MODE_AUTO = 2;

    private Handler mHandler;
    private SettingsObserver mSettingsObserver;

    protected OpScreenRefreshRateTile(Object host, String key, Object tile, XSharedPreferences prefs,
                          QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);

        mHandler = new Handler();
        mSettingsObserver = new SettingsObserver(mHandler);
        mState.icon = iconFromResId(R.drawable.ic_qs_op_screen_refresh_rate);
    }

    class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(SETTING_SCREEN_REFRESH_RATE), false, this);
        }

        public void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    }

    private int getCurrentMode() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                    SETTING_SCREEN_REFRESH_RATE, MODE_AUTO);
    }

    private String getLabelForMode(int state) {
        switch (state) {
            default:
            case MODE_AUTO: return mGbContext.getString(R.string.qs_tile_label_auto);
            case MODE_60HZ: return "60Hz";
            case MODE_90HZ: return "90Hz";
        }
    }

    @Override
    public String getSettingsKey() {
        return "gb_tile_op_screen_refresh_rate";
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mSettingsObserver.observe();
        } else {
            mSettingsObserver.unobserve();
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.label = getLabelForMode(getCurrentMode());
        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        int nextMode = getCurrentMode() + 1;
        if (nextMode > MODE_AUTO) nextMode = MODE_90HZ;
        Settings.Global.putInt(mContext.getContentResolver(), SETTING_SCREEN_REFRESH_RATE, nextMode);
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_DISPLAY_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mSettingsObserver = null;
        mHandler = null;
    }
}
