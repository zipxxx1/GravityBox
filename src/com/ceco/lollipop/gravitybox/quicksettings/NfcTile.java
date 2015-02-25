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

import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.view.View;

public class NfcTile extends QsTile {
    private static NfcAdapter sNfcAdapter;
    private static final int NFC_ADAPTER_UNKNOWN = -100;
    private static final int STATE_OFF = 1;
    private static final int STATE_TURNING_ON = 2;
    private static final int STATE_ON = 3;
    private static final int STATE_TURNING_OFF = 4;
    private static final String ACTION_ADAPTER_STATE_CHANGED = 
            "android.nfc.action.ADAPTER_STATE_CHANGED";

    private int mNfcState = NFC_ADAPTER_UNKNOWN;
    private boolean mIsReceiving;

    private BroadcastReceiver mStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            final int oldNfcState = mNfcState;
            getNfcState();
            if (mNfcState != oldNfcState) {
                refreshState();
            }
        }
    };

    public NfcTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    private void registerNfcReceiver() {
        if (!mIsReceiving) {
            IntentFilter intentFilter = new IntentFilter(ACTION_ADAPTER_STATE_CHANGED);
            mContext.registerReceiver(mStateChangeReceiver, intentFilter);
            mIsReceiving = true;
            mStateChangeReceiver.onReceive(null, null);
            if (DEBUG) log(getKey() + ": registerNfcReceiver");
        }
    }

    private void unregisterNfcReceiver() {
        if (mIsReceiving) {
            mContext.unregisterReceiver(mStateChangeReceiver);
            mIsReceiving = false;
            if (DEBUG) log(getKey() + ": unregisterNfcReceiver");
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mState.visible) {
            registerNfcReceiver();
        } else {
            unregisterNfcReceiver();
        }
    }

    protected void toggleState() {
        getNfcState();
        switch (mNfcState) {
            case STATE_ON:
                mNfcState = STATE_TURNING_OFF;
                refreshState();
                try {
                    XposedHelpers.callMethod(sNfcAdapter, "disable");
                } catch (Throwable t) {
                    if (DEBUG) log(getKey() + ": Error calling disable() on NFC adapter: " + t.getMessage());
                }
                break;
            case STATE_OFF:
                mNfcState = STATE_TURNING_ON;
                refreshState();
                try {
                    XposedHelpers.callMethod(sNfcAdapter, "enable");
                } catch (Throwable t) {
                    if (DEBUG) log(getKey() + ": Error calling enable() on NFC adapter: " + t.getMessage());
                }
                break;
        }
    }

    private void getNfcState() {
        try {
            if (sNfcAdapter == null) {
                sNfcAdapter = (NfcAdapter) XposedHelpers.callStaticMethod(
                        NfcAdapter.class, "getNfcAdapter", mContext);
            }
            mNfcState = (Integer) XposedHelpers.callMethod(sNfcAdapter, "getAdapterState");
        } catch (Throwable t) {
            mNfcState = NFC_ADAPTER_UNKNOWN;
        }
        if (DEBUG) log(getKey() + ": getNfcState: mNfcState = " + mNfcState);
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        switch (mNfcState) {
        case STATE_ON:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_nfc_on);
            mState.label = mGbContext.getString(R.string.quick_settings_nfc_on);
            break;
        case STATE_OFF:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_nfc_off);
            mState.label = mGbContext.getString(R.string.quick_settings_nfc_off);
            break;
        case STATE_TURNING_ON:
        case STATE_TURNING_OFF:
        default:
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_nfc_trans);
            mState.label = "----";
        }

        mState.applyTo(state);
    }

    @Override
    public void handleClick() {
        toggleState();
    }

    @Override
    public boolean handleLongClick(View view) {
        startSettingsActivity(Settings.ACTION_NFC_SETTINGS);
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        sNfcAdapter = null;
    }
}
