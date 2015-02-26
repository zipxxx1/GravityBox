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
import de.robv.android.xposed.XposedBridge;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.provider.Settings;
import android.view.View;

@SuppressWarnings("deprecation")
public class LockScreenTile extends QsTile {
    private boolean mLockScreenEnabled;
    private KeyguardLock mKeyguardLock;

    public LockScreenTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mLockScreenEnabled = true;
    }

    private void toggleLockscreenState() {
        try {
            if (mKeyguardLock == null) {
                final KeyguardManager kgManager = 
                        (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                mKeyguardLock = kgManager.newKeyguardLock(getKey());
            }
            if (mKeyguardLock != null) {
                mLockScreenEnabled = !mLockScreenEnabled;
                if (mLockScreenEnabled) {
                    mKeyguardLock.reenableKeyguard();
                } else {
                    mKeyguardLock.disableKeyguard();
                }
                refreshState();
            }
        } catch (Throwable t) {
            log(getKey() + ": Error toggling lock screen state: ");
            XposedBridge.log(t);
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = !mEventDistributor.isKeyguardShowingAndSecured();
        if (mLockScreenEnabled) {
            mState.label = mGbContext.getString(R.string.quick_settings_lock_screen_on);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_lock_screen_on);
        } else {
            mState.label = mGbContext.getString(R.string.quick_settings_lock_screen_off);
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_lock_screen_off);
        }

        super.handleUpdateState(state, arg);
    }

    @Override
    public void handleClick() {
        toggleLockscreenState();
    }

    @Override
    public boolean handleLongClick(View view) {
        startSettingsActivity(Settings.ACTION_SECURITY_SETTINGS);
        return true;
    }
}
