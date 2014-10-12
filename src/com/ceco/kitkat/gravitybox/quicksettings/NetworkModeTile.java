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

    private int mNetworkType;
    private int mDefaultNetworkType;
    private boolean mAllow3gOnly;
    private boolean mAllow2g3g;
    private boolean mAllowLte;
    private boolean mUseCdma;
    private boolean mIsMsim;
    private int mSimSlot;
    private TextView mSimSlotTextView;
    private int m2G3GMode;

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
                    PhoneWrapper.PREFERRED_NETWORK_MODE, m2G3GMode);

            if (DEBUG) log("SettingsObserver onChange; mNetworkType = " + mNetworkType);

            updateResources();
        }
    }

    public NetworkModeTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
                switch (mNetworkType) {
                    case PhoneWrapper.NT_WCDMA_PREFERRED:
                    case PhoneWrapper.NT_GSM_WCDMA_AUTO:
                    case PhoneWrapper.NT_CDMA_EVDO:
                        i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, 
                                mAllowLte ? getPreferredLteMode() : 
                                    mUseCdma ? PhoneWrapper.NT_CDMA_ONLY : PhoneWrapper.NT_GSM_ONLY);
                        break;
                    case PhoneWrapper.NT_WCDMA_ONLY:
                    case PhoneWrapper.NT_EVDO_ONLY:
                        if (!mAllow2g3g) {
                            i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE,
                                    mAllowLte ? getPreferredLteMode() : 
                                        mUseCdma ? PhoneWrapper.NT_CDMA_ONLY : PhoneWrapper.NT_GSM_ONLY);
                        } else {
                            i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mUseCdma ?
                                    PhoneWrapper.NT_CDMA_EVDO : m2G3GMode);
                        }
                        break;
                    case PhoneWrapper.NT_GSM_ONLY:
                    case PhoneWrapper.NT_CDMA_ONLY:
                        i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mAllow3gOnly ?
                                    mUseCdma ? PhoneWrapper.NT_EVDO_ONLY : PhoneWrapper.NT_WCDMA_ONLY : 
                                        mUseCdma ? PhoneWrapper.NT_CDMA_EVDO : m2G3GMode);
                        break;
                    default:
                        if (PhoneWrapper.isLteNetworkType(mNetworkType)) {
                            i.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, 
                                    mUseCdma ? PhoneWrapper.NT_CDMA_ONLY : PhoneWrapper.NT_GSM_ONLY);
                        } else {
                            log("onClick: Unknown or unsupported network type: mNetworkType = " + mNetworkType);
                        }
                        break;
                }
                if (i.hasExtra(PhoneWrapper.EXTRA_NETWORK_TYPE)) {
                    mContext.sendBroadcast(i);
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

        mLabel = mGbResources.getString(R.string.qs_tile_network_mode);
        mDefaultNetworkType = PhoneWrapper.getDefaultNetworkType();
        ContentResolver cr = mContext.getContentResolver();
        mNetworkType = Settings.Global.getInt(cr, 
                PhoneWrapper.PREFERRED_NETWORK_MODE, mDefaultNetworkType);
        if (DEBUG) log("mNetworkType=" + mNetworkType + "; mDefaultNetworkType=" + mDefaultNetworkType);

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
        int value = 0;
        int nm2G3GValue = PhoneWrapper.NT_WCDMA_PREFERRED;
        try {
            value = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_MODE, "0"));
        } catch (NumberFormatException nfe) {
            log("onPreferenceInitialize: invalid value for network mode preference");
        }
        try {
            nm2G3GValue = Integer.valueOf(prefs.getString(
                    GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_2G3G_MODE,
                    String.valueOf(PhoneWrapper.NT_WCDMA_PREFERRED)));
        } catch (NumberFormatException nfe) {
            log("onPreferenceInitialize: invalid value for 2G+3G mode preference");
        }
        updateFlags(value, nm2G3GValue, 
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_LTE, false),
                prefs.getBoolean(GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_CDMA, false));

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
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NMT_MODE)) {
                updateGsmFlags(intent.getIntExtra(GravityBoxSettings.EXTRA_NMT_MODE, 0));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NMT_LTE)) {
                updateLteFlags(intent.getBooleanExtra(GravityBoxSettings.EXTRA_NMT_LTE, false));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NMT_CDMA)) {
                updateCdmaFlags(intent.getBooleanExtra(GravityBoxSettings.EXTRA_NMT_CDMA, false));
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_NMT_2G3G_MODE)) {
                update2G3GMode(intent.getIntExtra(GravityBoxSettings.EXTRA_NMT_2G3G_MODE,
                        PhoneWrapper.NT_WCDMA_PREFERRED));
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

    private void updateGsmFlags(int nmMode) {
        mAllow3gOnly = (nmMode == 0) || (nmMode == 2);
        mAllow2g3g = (nmMode < 2);
        if (DEBUG) log("updateGsmFlags: mAllow3gOnly=" + mAllow3gOnly + "; mAllow2g3g=" + mAllow2g3g);
    }

    private void update2G3GMode(int nm2G3GMode) {
        m2G3GMode = nm2G3GMode;
        if (DEBUG) log("update2G3GMode: m2G3GMode=" + m2G3GMode);
    }

    private void updateLteFlags(boolean allowLte) {
        mAllowLte = allowLte;
        if (DEBUG) log("updateLteFlags: mAllowLte=" + mAllowLte);
    }

    private void updateCdmaFlags(boolean useCdma) {
        mUseCdma = useCdma;
        if (DEBUG) log("updateCdmaFlags: mUseCdma=" + mUseCdma);
    }

    private void updateFlags(int nmMode, int nm2G3GMode, boolean allowLte, boolean useCdma) {
        updateGsmFlags(nmMode);
        update2G3GMode(nm2G3GMode);
        updateLteFlags(allowLte);
        updateCdmaFlags(useCdma);
    }

    @Override
    protected synchronized void updateTile() {

        switch (mNetworkType) {
            case PhoneWrapper.NT_WCDMA_PREFERRED:
                mDrawableId = R.drawable.ic_qs_3g2g_on;
                break;
            case PhoneWrapper.NT_GSM_WCDMA_AUTO:
                mDrawableId = R.drawable.ic_qs_2g3g_on;
                break;
            case PhoneWrapper.NT_CDMA_EVDO:
                mDrawableId = R.drawable.ic_qs_2g3g_on;
                break;
            case PhoneWrapper.NT_WCDMA_ONLY:
            case PhoneWrapper.NT_EVDO_ONLY:
                mDrawableId = R.drawable.ic_qs_3g_on;
                break;
            case PhoneWrapper.NT_GSM_ONLY:
            case PhoneWrapper.NT_CDMA_ONLY:
                mDrawableId = R.drawable.ic_qs_2g_on;
                break;
            default:
                if (PhoneWrapper.isLteNetworkType(mNetworkType)) {
                    mDrawableId = R.drawable.ic_qs_lte;
                } else {
                    mDrawableId = R.drawable.ic_qs_unexpected_network;
                    log("updateTile: Unknown or unsupported network type: mNetworkType = " + mNetworkType);
                }
                break;
        }

        super.updateTile();
    }

    private int getPreferredLteMode() {
        return (PhoneWrapper.isLteNetworkType(mDefaultNetworkType) ?
                        mDefaultNetworkType : PhoneWrapper.NT_LTE_CMDA_EVDO_GSM_WCDMA);
    }
}
