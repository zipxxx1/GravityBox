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

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.PhoneWrapper;
import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Context;
import android.content.Intent;

public class NetworkModeTile extends QsTile {
    private int mNetworkType;
    private int mDefaultNetworkType;
    private boolean mAllow3gOnly;
    private boolean mAllow2g3g;
    private boolean mAllowLte;
    private boolean mUseCdma;
    private boolean mIsMsim;
    private int mSimSlot = 0;
    private int m2G3GMode;
    private boolean mIsReceiving;
    private boolean mInitialState;

    public NetworkModeTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState.label = mGbContext.getString(R.string.qs_tile_network_mode);
        mDefaultNetworkType = PhoneWrapper.getDefaultNetworkType();
        mIsMsim = PhoneWrapper.hasMsimSupport();
        mInitialState = true;
    }

    @Override
    public void initPreferences() {
        super.initPreferences();

        int value = 0;
        int nm2G3GValue = PhoneWrapper.NT_WCDMA_PREFERRED;
        try {
            value = Integer.valueOf(mPrefs.getString(GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_MODE, "0"));
        } catch (NumberFormatException nfe) {
            log(getKey() + ": onPreferenceInitialize: invalid value for network mode preference");
        }
        try {
            nm2G3GValue = Integer.valueOf(mPrefs.getString(
                    GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_2G3G_MODE,
                    String.valueOf(PhoneWrapper.NT_WCDMA_PREFERRED)));
        } catch (NumberFormatException nfe) {
            log(getKey() + ": onPreferenceInitialize: invalid value for 2G+3G mode preference");
        }
        updateFlags(value, nm2G3GValue, 
                mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_LTE, false),
                mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_NETWORK_MODE_TILE_CDMA, false));

        if (mIsMsim) {
            try {
                mSimSlot = Integer.valueOf(mPrefs.getString(
                        GravityBoxSettings.PREF_KEY_QS_NETWORK_MODE_SIM_SLOT, "0"));
            } catch (NumberFormatException nfe) {
                log(getKey() + ": Invalid value for SIM Slot preference: " + nfe.getMessage());
            }
            if (DEBUG) log(getKey() + ": mSimSlot = " + mSimSlot);
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

        if (intent.getAction().equals(PhoneWrapper.ACTION_NETWORK_TYPE_CHANGED)) {
            String tag = intent.getStringExtra(PhoneWrapper.EXTRA_RECEIVER_TAG);
            if (tag == null || tag.equals(TAG)) {
                int phoneId = intent.getIntExtra(PhoneWrapper.EXTRA_PHONE_ID, 0);
                if (phoneId == mSimSlot) {
                    mNetworkType = intent.getIntExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, m2G3GMode);
                    if (DEBUG) log(getKey() + ": ACTION_NETWORK_TYPE_CHANGED: mNetworkType = " + mNetworkType);
                    if (mIsReceiving) {
                        refreshState();
                    }
                }
            }
        }

        if (mIsMsim && intent.getAction().equals(
                GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED)) {
            mSimSlot = intent.getIntExtra(GravityBoxSettings.EXTRA_SIM_SLOT, 0);
            if (DEBUG) log(getKey() + ": received ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED broadcast: " +
                                "mSimSlot = " + mSimSlot);
        }
    }

    private void updateGsmFlags(int nmMode) {
        mAllow3gOnly = (nmMode == 0) || (nmMode == 2);
        mAllow2g3g = (nmMode < 2);
        if (DEBUG) log(getKey() + ": updateGsmFlags: mAllow3gOnly=" + mAllow3gOnly + "; mAllow2g3g=" + mAllow2g3g);
    }

    private void update2G3GMode(int nm2G3GMode) {
        m2G3GMode = nm2G3GMode;
        if (DEBUG) log(getKey() + ": update2G3GMode: m2G3GMode=" + m2G3GMode);
    }

    private void updateLteFlags(boolean allowLte) {
        mAllowLte = allowLte;
        if (DEBUG) log(getKey() + ": updateLteFlags: mAllowLte=" + mAllowLte);
    }

    private void updateCdmaFlags(boolean useCdma) {
        mUseCdma = useCdma;
        if (DEBUG) log(getKey() + ": updateCdmaFlags: mUseCdma=" + mUseCdma);
    }

    private void updateFlags(int nmMode, int nm2G3GMode, boolean allowLte, boolean useCdma) {
        updateGsmFlags(nmMode);
        update2G3GMode(nm2G3GMode);
        updateLteFlags(allowLte);
        updateCdmaFlags(useCdma);
    }

    private int getPreferredLteMode() {
        return (PhoneWrapper.isLteNetworkType(mDefaultNetworkType) ?
                        mDefaultNetworkType : PhoneWrapper.NT_LTE_CMDA_EVDO_GSM_WCDMA);
    }

    @Override
    public void setListening(boolean listening) {
        if (DEBUG) log(getKey() + ": setListening(" + listening + ")");
        if (listening && mEnabled) {
            if (DEBUG) log(getKey() + ": mNetworkType=" + mNetworkType + "; mDefaultNetworkType=" + mDefaultNetworkType);
            mIsReceiving = true;
            if (mInitialState) {
                mInitialState = false;
                Intent intent = new Intent(PhoneWrapper.ACTION_GET_CURRENT_NETWORK_TYPE);
                intent.putExtra(PhoneWrapper.EXTRA_PHONE_ID, mSimSlot);
                intent.putExtra(PhoneWrapper.EXTRA_RECEIVER_TAG, TAG);
                mContext.sendBroadcast(intent);
            }
        } else {
            mIsReceiving = false;
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        switch (mNetworkType) {
        case PhoneWrapper.NT_WCDMA_PREFERRED:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_3g2g_on);
            break;
        case PhoneWrapper.NT_GSM_WCDMA_AUTO:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_2g3g_on);
            break;
        case PhoneWrapper.NT_CDMA_EVDO:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_2g3g_on);
            break;
        case PhoneWrapper.NT_WCDMA_ONLY:
        case PhoneWrapper.NT_EVDO_ONLY:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_3g_on);
            break;
        case PhoneWrapper.NT_GSM_ONLY:
        case PhoneWrapper.NT_CDMA_ONLY:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_2g_on);
            break;
        default:
            if (PhoneWrapper.isLteNetworkType(mNetworkType)) {
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_lte);
            } else {
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_unexpected_network);
                log(getKey() + ": handleUpdateState: Unknown or unsupported network type: " +
                        "mNetworkType = " + mNetworkType);
            }
            break;
        }

        if (mIsMsim) {
            mState.label = mGbContext.getString(R.string.qs_tile_network_mode) + " (" +
                    String.valueOf(mSimSlot+1) + ")";
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleClick() {
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
                    log(getKey() + ": onClick: Unknown or unsupported network type: " +
                            "mNetworkType = " + mNetworkType);
                }
                break;
        }
        if (i.hasExtra(PhoneWrapper.EXTRA_NETWORK_TYPE)) {
            mContext.sendBroadcast(i);
        }
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        if (mIsMsim) {
            Intent intent = new Intent(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED);
            intent.putExtra(GravityBoxSettings.EXTRA_SIM_SLOT, mSimSlot == 0 ? 1 : 0);
            mContext.sendBroadcast(intent);
        } else {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("com.android.phone", "com.android.phone.Settings");
            startSettingsActivity(intent);
        }
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
    }
}
