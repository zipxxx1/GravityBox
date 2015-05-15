/*
 * Copyright (C) 2013 The SlimRoms Project
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.provider.Settings;

import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;

public class LocationTileSlimkat extends QsTile {
    private boolean mLocationEnabled;
    private int mLocationMode;
    private boolean mIsReceiving;

    public LocationTileSlimkat(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(LocationManager.MODE_CHANGED_ACTION) || 
                    action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
               locationSettingsChanged();
            }
        }
    };

    private void registerLocationManagerReceiver() {
        if (mIsReceiving) return;
        mLocationMode = getLocationMode();
        mLocationEnabled = isLocationEnabled(); 
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
        intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
        mIsReceiving = true;
        if (DEBUG) log(getKey() + ": Location manager registered");
    }

    private void unregisterLocationManagerReceiver() {
        if (mIsReceiving) {
            mContext.unregisterReceiver(mBroadcastReceiver);
            mIsReceiving = false;
            if (DEBUG) log(getKey() + ": Location manager unregistered");
        }
    }

    @Override
    public void setListening(boolean listening) {
        if (listening && mEnabled) {
            registerLocationManagerReceiver();
        } else {
            unregisterLocationManagerReceiver();
        }
    }

    private boolean isLocationEnabled() {
        return getLocationMode() != Settings.Secure.LOCATION_MODE_OFF;
    }

    private boolean setLocationEnabled(boolean enabled) {
        int mode = enabled ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : Settings.Secure.LOCATION_MODE_OFF;
        return setLocationMode(mode);
    }

    private int getLocationMode() {
        final ContentResolver resolver = mContext.getContentResolver();
        int mode = Settings.Secure.getInt(resolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        return mode;
    }

    private boolean setLocationMode(int mode) {
        final ContentResolver cr = mContext.getContentResolver();
        return Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, mode);
    }

    private boolean switchLocationMode(int currentMode) {
        switch (currentMode) {
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                currentMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                currentMode = Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                currentMode = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
                break;
        }
        return setLocationMode(currentMode);
    }

    private void locationSettingsChanged() {
        mLocationMode = getLocationMode();
        mLocationEnabled = isLocationEnabled();

//        Runnable collapsePanels = collapsePanels(mLocationMode, locationMode, mLocationEnabled, locationEnabled);
//        Handler handler = new Handler();
//        handler.post(collapsePanels);
        if (DEBUG) log(getKey() + ": mLocationEnabled = " + mLocationEnabled + 
                "; mLocationMode = " + mLocationMode);
        refreshState();
    }

//    private Runnable collapsePanels(final int locationModeOld, final int locationModeNew, 
//            final boolean locationEnabledOld, final boolean locationEnabledNew) {
//        Runnable collapsePanelsRunnable = new Runnable() {
//            @Override
//            public void run() {
//                if ((locationModeOld == Settings.Secure.LOCATION_MODE_SENSORS_ONLY 
//                            && locationModeNew == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
//                        || (!locationEnabledOld && locationEnabledNew
//                            && (locationModeNew == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
//                            || locationModeNew == Settings.Secure.LOCATION_MODE_BATTERY_SAVING))) {
//                   Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
//                   mContext.sendBroadcast(closeDialog);
//                }
//            }
//        };
//
//        return collapsePanelsRunnable;
//    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        switch (mLocationMode) {
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_on_gps);
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_on_wifi);
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_on);
                break;
            case Settings.Secure.LOCATION_MODE_OFF:
                mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_location_off);
                break;
        }
        mState.label = mLocationEnabled ? mGbContext.getString(R.string.quick_settings_location_on) :
                         mGbContext.getString(R.string.quick_settings_location_off);

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        setLocationEnabled(!mLocationEnabled);
        super.handleClick();
    }

    @Override
    public boolean handleLongClick() {
        if (mLocationEnabled) {
            switchLocationMode(mLocationMode);
        }
        return true;
    }
}
