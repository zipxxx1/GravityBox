/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.lollipop.gravitybox.quicksettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

public class StayAwakeTile extends QsTile {
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
    private int mLongestTimeoutIndex;

    private static class ScreenTimeout {
        final int mMillis;
        final int mLabelResId;
        boolean mEnabled;

        public ScreenTimeout(int millis, int labelResId) {
            mMillis = millis;
            mLabelResId = labelResId;
            mEnabled = false;
        }
    }

    public StayAwakeTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_stayawake_on);
        mSettingsObserver = new SettingsObserver(new Handler());

        getCurrentState();
        mPreviousTimeoutIndex = mCurrentTimeoutIndex == -1 ||
                SCREEN_TIMEOUT[mCurrentTimeoutIndex].mMillis == SCREEN_TIMEOUT[mLongestTimeoutIndex].mMillis ?
                        getIndexFromValue(FALLBACK_SCREEN_TIMEOUT_VALUE) : mCurrentTimeoutIndex;
    }

    private void getCurrentState() {
        mCurrentTimeoutIndex = getIndexFromValue(Settings.System.getInt(mContext.getContentResolver(), 
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE));
        if (DEBUG) log(getKey() + ": getCurrentState: mCurrentTimeoutIndex=" + mCurrentTimeoutIndex +
                "; mPreviousTimeoutIndex=" + mPreviousTimeoutIndex);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            getCurrentState();
            mSettingsObserver.observe();
            if (DEBUG) log(getKey() + ": observer registered");
        } else {
            mSettingsObserver.unobserve();
            if (DEBUG) log(getKey() + ": observer unregistered");
        }
    }

    @Override
    public void initPreferences() {
        super.initPreferences();

        Set<String> smodes = mPrefs.getStringSet(
                GravityBoxSettings.PREF_STAY_AWAKE_TILE_MODE,
                new HashSet<String>(Arrays.asList(new String[] { "0", "1", "2", "3", "4", "5", "6", "7" })));
        List<String> lmodes = new ArrayList<String>(smodes);
        Collections.sort(lmodes);
        int modes[] = new int[lmodes.size()];
        for (int i = 0; i < lmodes.size(); i++) {
            modes[i] = Integer.valueOf(lmodes.get(i));
        }
        if (DEBUG) log(getKey() + ": initPreferences: modes=" + modes);
        updateSettings(modes);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)
                && intent.hasExtra(GravityBoxSettings.EXTRA_SA_MODE)) {
            int[] modes = intent.getIntArrayExtra(GravityBoxSettings.EXTRA_SA_MODE);
            if (DEBUG) log(getKey() + ": onBroadcastReceived: modes=" + modes);
            updateSettings(modes);
        }
    }

    private void updateSettings(int[] modes) {
        for (ScreenTimeout s : SCREEN_TIMEOUT) {
            s.mEnabled = false;
        }
        if (modes != null && modes.length > 0) {
            for (int i=0; i<modes.length; i++) {
                int index = modes[i];
                ScreenTimeout s = index < SCREEN_TIMEOUT.length ? SCREEN_TIMEOUT[index] : null;
                if (s != null) {
                    s.mEnabled = true;
                    mLongestTimeoutIndex = index;
                }
            }
        } else {
            mCurrentTimeoutIndex = getIndexFromValue(Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE));
            mLongestTimeoutIndex = -1;
        }
    }

    private void toggleStayAwake() {
        toggleStayAwake(-1);
    }

    private void toggleStayAwake(int index) {
        if (mLongestTimeoutIndex < 0)
            return;
        final int startIndex = mCurrentTimeoutIndex;
        int i = 0;
        do {
            if (index != -1) {
                if (index == mLongestTimeoutIndex) {
                    mCurrentTimeoutIndex = mLongestTimeoutIndex;
                } else if (SCREEN_TIMEOUT[index].mEnabled) {
                    mCurrentTimeoutIndex = index;
                } else {
                    mCurrentTimeoutIndex = i++;
                    index = i;
                }
            } else {
                if (++mCurrentTimeoutIndex >= SCREEN_TIMEOUT.length) {
                    mCurrentTimeoutIndex = 0;
                }
            }
        } while(!SCREEN_TIMEOUT[mCurrentTimeoutIndex].mEnabled &&
                    startIndex != mCurrentTimeoutIndex);
        if (DEBUG) log(getKey() + ": mCurrentTimeoutIndex = " + mCurrentTimeoutIndex);

        if (startIndex != mCurrentTimeoutIndex) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT,
                        SCREEN_TIMEOUT[mCurrentTimeoutIndex].mMillis);
        } else {
            mCurrentTimeoutIndex = getIndexFromValue(Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE));
        }
    }

    private int getIndexFromValue(int value) {
        for (int i = 0; i < SCREEN_TIMEOUT.length; i++) {
            if (SCREEN_TIMEOUT[i].mMillis == value) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        if (mCurrentTimeoutIndex == -1) {
            mState.label = String.format("%ds", TimeUnit.MILLISECONDS.toSeconds(
                    Settings.System.getInt(mContext.getContentResolver(), 
                            Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE)));;
        } else {
            mState.label = mGbContext.getString(SCREEN_TIMEOUT[mCurrentTimeoutIndex].mLabelResId);
        }

        mState.applyTo(state);
    }

    @Override
    public void handleClick() {
        toggleStayAwake();
    }

    @Override
    public boolean handleLongClick(View view) {
        if (mLongestTimeoutIndex < 0)
            return false;
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT,
                    FALLBACK_SCREEN_TIMEOUT_VALUE) == SCREEN_TIMEOUT[mLongestTimeoutIndex].mMillis) {
            toggleStayAwake(mPreviousTimeoutIndex);
        } else {
            mPreviousTimeoutIndex = mCurrentTimeoutIndex == -1 ?
                    getIndexFromValue(FALLBACK_SCREEN_TIMEOUT_VALUE) : mCurrentTimeoutIndex;
            toggleStayAwake(mLongestTimeoutIndex);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mSettingsObserver = null;
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

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            getCurrentState();
            refreshState();
        }
    }
}
