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

import android.content.Context;
import android.content.Intent;
import android.view.ViewGroup;

import com.ceco.lollipop.gravitybox.BroadcastSubReceiver;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsPanel implements BroadcastSubReceiver {
    private static final String TAG = "GB:QsPanel";
    private static final boolean DEBUG = false;

    private static final String CLASS_QS_PANEL = "com.android.systemui.qs.QSPanel";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Context mContext;
    private XSharedPreferences mPrefs;
    private boolean mNormalized;
    private ViewGroup mQsPanel;

    public QsPanel(Context context, XSharedPreferences prefs, 
            QsTileEventDistributor eventDistributor) {
        mContext = context;
        mPrefs = prefs;
        eventDistributor.registerBroadcastSubReceiver(this);

        initPreferences();
        createHooks();
        if (DEBUG) log("QsPanel wrapper created");
    }

    private void initPreferences() {
        mNormalized = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_NORMALIZE, false);
        if (DEBUG) log("initPreferences: mNormalized=" + mNormalized);
    }

    private void updateResources() {
        try {
            if (mQsPanel != null) {
                XposedHelpers.callMethod(mQsPanel, "updateResources");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_NORMALIZED)) {
                mNormalized = intent.getBooleanExtra(GravityBoxSettings.EXTRA_QS_NORMALIZED, false);
                updateResources();
                if (DEBUG) log("onBroadcastReceived: mNormalized=" + mNormalized);
            }
        } 
    }

    private void createHooks() {
        try {
            ClassLoader cl = mContext.getClassLoader();

            XposedHelpers.findAndHookMethod(CLASS_QS_PANEL, cl, "updateResources",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mQsPanel == null) {
                        mQsPanel = (ViewGroup) param.thisObject;
                    }
                    if (mNormalized) {
                        XposedHelpers.setIntField(mQsPanel, "mLargeCellHeight",
                                XposedHelpers.getIntField(mQsPanel, "mCellHeight"));
                        XposedHelpers.setIntField(mQsPanel, "mLargeCellWidth",
                                XposedHelpers.getIntField(mQsPanel, "mCellWidth"));
                        mQsPanel.postInvalidate();
                        if (DEBUG) log("updateResources: Updated first row dimensions due to normalized tiles");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
