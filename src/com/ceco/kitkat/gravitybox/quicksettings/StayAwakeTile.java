/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.kitkat.gravitybox.quicksettings;

import java.util.concurrent.TimeUnit;

import com.ceco.kitkat.gravitybox.R;

import de.robv.android.xposed.XposedBridge;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

public class StayAwakeTile extends BasicTile {
    private static final String TAG = "GB:StayAwakeTile";
    private static final boolean DEBUG = false;

    private static final int NEVER_SLEEP = Integer.MAX_VALUE;
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static ScreenTimeout[] SCREEN_TIMEOUT = new ScreenTimeout[] {
        new ScreenTimeout(15000, R.string.stay_awake_15s),
        new ScreenTimeout(30000, R.string.stay_awake_30s),
        new ScreenTimeout(60000, R.string.stay_awake_1m),
        new ScreenTimeout(120000, R.string.stay_awake_2m),
        new ScreenTimeout(300000, R.string.stay_awake_5m),
        new ScreenTimeout(600000, R.string.stay_awake_10m),
        new ScreenTimeout(1800000, R.string.stay_awake_30m),
        new ScreenTimeout(NEVER_SLEEP, R.string.quick_settings_stay_awake_on),
    };

    private SettingsObserver mSettingsObserver;
    private int mCurrentTimeoutIndex;
    private int mPreviousTimeoutIndex;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static class ScreenTimeout {
        final int mMillis;
        final int mLabelResId;

        public ScreenTimeout(int millis, int labelResId) {
            mMillis = millis;
            mLabelResId = labelResId;
        }
    }

    public StayAwakeTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mDrawableId = R.drawable.ic_qs_stayawake_on;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStayAwake();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT,
                            FALLBACK_SCREEN_TIMEOUT_VALUE) == NEVER_SLEEP) {
                    toggleStayAwake(mPreviousTimeoutIndex);
                } else {
                    mPreviousTimeoutIndex = mCurrentTimeoutIndex == -1 ?
                            getIndexFromValue(FALLBACK_SCREEN_TIMEOUT_VALUE) : mCurrentTimeoutIndex;
                    toggleStayAwake(getIndexFromValue(NEVER_SLEEP));
                }
                return true;
            }
        };

        mCurrentTimeoutIndex = getIndexFromValue(Settings.System.getInt(mContext.getContentResolver(), 
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE));

        mPreviousTimeoutIndex = mCurrentTimeoutIndex == -1 || 
                SCREEN_TIMEOUT[mCurrentTimeoutIndex].mMillis == NEVER_SLEEP ?
                        getIndexFromValue(FALLBACK_SCREEN_TIMEOUT_VALUE) : mCurrentTimeoutIndex;

        if (DEBUG) log("mCurrentTimeoutIndex = " + mCurrentTimeoutIndex +
                "; mPreviousTimeoutIndex = " + mPreviousTimeoutIndex);
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_stay_awake;
    }

    @Override
    protected void onTilePostCreate() {
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();

        super.onTilePostCreate();
    }

    @Override
    protected synchronized void updateTile() {
        if (mCurrentTimeoutIndex == -1) {
            mLabel = String.format("%ds", TimeUnit.MILLISECONDS.toSeconds(
                    Settings.System.getInt(mContext.getContentResolver(), 
                            Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE)));;
        } else {
            mLabel = mGbContext.getString(SCREEN_TIMEOUT[mCurrentTimeoutIndex].mLabelResId);
        }

        super.updateTile();
    }

    private void toggleStayAwake() {
        toggleStayAwake(-1);
    }

    private void toggleStayAwake(int index) {
        if (index != -1) {
            mCurrentTimeoutIndex = index;
        } else {
            if (++mCurrentTimeoutIndex >= SCREEN_TIMEOUT.length) {
                mCurrentTimeoutIndex = 0;
            }
        }
        if (DEBUG) log("mCurrentTimeoutIndex = " + mCurrentTimeoutIndex);
        Settings.System.putInt(mContext.getContentResolver(), 
                Settings.System.SCREEN_OFF_TIMEOUT,
                    SCREEN_TIMEOUT[mCurrentTimeoutIndex].mMillis);
    }

    private int getIndexFromValue(int value) {
        for (int i = 0; i < SCREEN_TIMEOUT.length; i++) {
            if (SCREEN_TIMEOUT[i].mMillis == value) {
                return i;
            }
        }
        return -1;
    }

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), 
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mCurrentTimeoutIndex = getIndexFromValue(
                    Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE));
            if (DEBUG) log("SettingsObserver onChange; mCurrentTimeoutIndex = " + mCurrentTimeoutIndex);
            updateResources();
        }
    }
}
