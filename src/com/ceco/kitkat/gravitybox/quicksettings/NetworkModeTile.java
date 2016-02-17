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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ceco.kitkat.gravitybox.GravityBoxSettings;
import com.ceco.kitkat.gravitybox.PhoneWrapper;
import com.ceco.kitkat.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

public class NetworkModeTile extends BasicTile {
    private static final String TAG = "GB:NetworkModeTile";
    private static final boolean DEBUG = false;

    private static class NetworkMode {
        int value;
        boolean enabled;
        int labelRes;
        int iconRes;
        NetworkMode(int v, int l, int i) {
            value = v;
            labelRes = l;
            iconRes = i;
        }
    }

    private static NetworkMode[] MODES = new NetworkMode[] {
            new NetworkMode(0, R.string.network_mode_0, R.drawable.ic_qs_3g2g_on),
            new NetworkMode(1, R.string.network_mode_1, R.drawable.ic_qs_2g_on),
            new NetworkMode(2, R.string.network_mode_2, R.drawable.ic_qs_3g_on),
            new NetworkMode(3, R.string.network_mode_3, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(4, R.string.network_mode_4, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(5, R.string.network_mode_5, R.drawable.ic_qs_2g_on),
            new NetworkMode(6, R.string.network_mode_6, R.drawable.ic_qs_3g_on),
            new NetworkMode(7, R.string.network_mode_7, R.drawable.ic_qs_2g3g_on),
            new NetworkMode(8, R.string.network_mode_8, R.drawable.ic_qs_lte),
            new NetworkMode(9, R.string.network_mode_9, R.drawable.ic_qs_lte),
            new NetworkMode(10, R.string.network_mode_10, R.drawable.ic_qs_lte),
            new NetworkMode(11, R.string.network_mode_11, R.drawable.ic_qs_lte),
            new NetworkMode(12, R.string.network_mode_12, R.drawable.ic_qs_lte)
    };

    private int mNetworkType;
    private boolean mIsMsim;
    private int mSimSlot;
    private TextView mSimSlotTextView;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(PhoneWrapper.PREFERRED_NETWORK_MODE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            ContentResolver cr = mContext.getContentResolver();
            mNetworkType = Settings.Global.getInt(cr, 
                    PhoneWrapper.PREFERRED_NETWORK_MODE, PhoneWrapper.getDefaultNetworkType());

            if (DEBUG) log("SettingsObserver onChange; mNetworkType = " + mNetworkType);

            updateResources();
        }
    }

    public NetworkModeTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentIndex = findIndexForMode(mNetworkType);
                final int startIndex = currentIndex;
                do {
                    if (++currentIndex >= MODES.length) {
                        currentIndex = 0;
                    }
                } while(!MODES[currentIndex].enabled &&
                        currentIndex != startIndex);

                if (currentIndex != startIndex) {
                    setNetworkMode(MODES[currentIndex].value);
                }
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mIsMsim) {
                    Intent intent = new Intent(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED);
                    intent.putExtra(GravityBoxSettings.EXTRA_SIM_SLOT, mSimSlot == 0 ? 1 : 0);
                    mContext.sendBroadcast(intent);
                } else {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.android.phone", "com.android.phone.Settings");
                    startActivity(intent);
                }
                return true;
            }
        };

        ContentResolver cr = mContext.getContentResolver();
        mNetworkType = Settings.Global.getInt(cr, 
                PhoneWrapper.PREFERRED_NETWORK_MODE, PhoneWrapper.getDefaultNetworkType());
        if (DEBUG) log("mNetworkType=" + mNetworkType);

        mIsMsim = PhoneWrapper.hasMsimSupport();
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_network_mode;
    }

    @Override
    protected void onTileCreate() {
        super.onTileCreate();
        mSimSlotTextView = (TextView) mTile.findViewById(R.id.simSlot);
        if (mIsMsim) {
            mSimSlotTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onTilePostCreate() {
        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();

        super.onTilePostCreate();
    }

    @Override
    protected void onPreferenceInitialize(XSharedPreferences prefs) {
        Set<String> smodes = prefs.getStringSet(
                GravityBoxSettings.PREF_KEY_NM_TILE_ENABLED_MODES,
                new HashSet<String>(Arrays.asList(new String[] { "0", "1", "2", "10" })));
        List<String> lmodes = new ArrayList<String>(smodes);
        Collections.sort(lmodes);
        int modes[] = new int[lmodes.size()];
        for (int i=0; i<lmodes.size(); i++) {
            modes[i] = Integer.valueOf(lmodes.get(i));
        }
        if (DEBUG) log("onPreferenceInitialize: modes=" + Arrays.toString(modes));
        setEnabledModes(modes);

        if (mIsMsim) {
            try {
                mSimSlot = Integer.valueOf(prefs.getString(
                        GravityBoxSettings.PREF_KEY_QS_NETWORK_MODE_SIM_SLOT, "0"));
            } catch (NumberFormatException nfe) {
                log("Invalid value for SIM Slot preference: " + nfe.getMessage());
            }
            if (DEBUG) log("mSimSlot = " + mSimSlot);
            mSimSlotTextView.setText(String.valueOf(mSimSlot+1));
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) { 
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NM_TILE_ENABLED_MODES)) {
                int[] modes = intent.getIntArrayExtra(GravityBoxSettings.EXTRA_NM_TILE_ENABLED_MODES);
                if (DEBUG) log("onBroadcastReceived: modes=" + Arrays.toString(modes));
                setEnabledModes(modes);
            }
        }

        if (mIsMsim && intent.getAction().equals(
                GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED)) {
            mSimSlot = intent.getIntExtra(GravityBoxSettings.EXTRA_SIM_SLOT, 0);
            if (DEBUG) log("received ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED broadcast: " +
                                "mSimSlot = " + mSimSlot);
            mSimSlotTextView.setText(String.valueOf(mSimSlot+1));
        }
    }

    private void setEnabledModes(int[] modes) {
        // disable all first
        for (NetworkMode nm : MODES) {
            nm.enabled = false;
        }

        // enable only those present in the list
        if (modes != null && modes.length > 0) {
            for (int i=0; i<modes.length; i++) {
                NetworkMode nm = findNetworkMode(modes[i]);
                if (nm != null) {
                    nm.enabled = true;
                }
            }
        }
    }

    private int findIndexForMode(int mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].value == mode)
                return i;
        }
        return -1;
    }

    private NetworkMode findNetworkMode(int mode) {
        int index = findIndexForMode(mode);
        return index >= 0 && index < MODES.length ? MODES[index] : null;
    }

    private void setNetworkMode(int mode) {
        Intent i = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
        i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mode);
        mContext.sendBroadcast(i);
    }

    @Override
    protected synchronized void updateTile() {
        NetworkMode nm = findNetworkMode(mNetworkType);
        if (nm != null) {
            mLabel = stripLabel(mGbContext.getString(nm.labelRes));
            mDrawableId = nm.iconRes;
        } else {
            mLabel = mGbContext.getString(R.string.network_mode_unknown);
            mDrawableId = R.drawable.ic_qs_unexpected_network;
        }

        super.updateTile();
    }

    private String stripLabel(String label) {
        if (label == null) return null;

        int index = label.lastIndexOf("(");
        return index > 0 ? label.substring(0, index-1) : label;
    }
}
