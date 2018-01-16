/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.oreo.gravitybox.ledcontrol;

import java.util.Locale;

import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.GravityBoxActivity;
import com.ceco.oreo.gravitybox.GravityBoxApplication;
import com.ceco.oreo.gravitybox.ModHwKeys;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class LedSettingsActivity extends GravityBoxActivity implements OnClickListener {
    protected static final String EXTRA_PACKAGE_NAME = "packageName";
    protected static final String EXTRA_APP_NAME = "appName";

    private static int NOTIF_ID = 2049;

    private LedSettings mLedSettings;
    private LedSettingsFragment mPrefsFragment;
    private Button mBtnPreview;
    private Button mBtnSave;
    private Button mBtnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(EXTRA_PACKAGE_NAME) ||
                intent.getStringExtra(EXTRA_PACKAGE_NAME) == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        mLedSettings = LedSettings.deserialize(this, intent.getStringExtra(EXTRA_PACKAGE_NAME));
        setContentView(R.layout.led_settings_activity);

        mPrefsFragment = (LedSettingsFragment) getFragmentManager().findFragmentById(R.id.prefs_fragment);
        mPrefsFragment.initialize(mLedSettings);

        mBtnPreview = (Button) findViewById(R.id.btnPreview);
        mBtnPreview.setOnClickListener(this);

        mBtnSave = (Button) findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(this);

        mBtnCancel = (Button) findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(this);

        setTitle(intent.getStringExtra(EXTRA_APP_NAME));
    }

    @Override
    public void onResume() {
        super.onResume();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIF_ID);
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnPreview) {
            previewSettings();
        } else if (v == mBtnSave) {
            saveSettings();
        } else if (v == mBtnCancel) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mLedSettings.getPackageName().equals("default")) {
            getMenuInflater().inflate(R.menu.led_settings_activity_menu, menu);
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.lc_settings_menu_reset:
                resetToDefaults();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void resetToDefaults() {
        LedSettings newLs = LedSettings.getDefault(this);
        newLs.setPackageName(mLedSettings.getPackageName());
        newLs.setEnabled(mLedSettings.getEnabled());
        mLedSettings = newLs;
        mPrefsFragment.initialize(mLedSettings);
    }

    private void previewSettings() {
        saveSettings(true);
    }

    private void saveSettings() {
        saveSettings(false);
    }

    private void saveSettings(boolean isPreview) {
        if (mLedSettings.getPackageName().equals("default")) {
            mLedSettings.setEnabled(isPreview || mPrefsFragment.getDefaultSettingsEnabled());
        }
        mLedSettings.setColor(mPrefsFragment.getColor());
        mLedSettings.setLedOnMs(mPrefsFragment.getLedOnMs());
        mLedSettings.setLedOffMs(mPrefsFragment.getLedOffMs());
        mLedSettings.setOngoing(mPrefsFragment.getOngoing());
        mLedSettings.setSoundOverride(mPrefsFragment.getSoundOverride());
        mLedSettings.setSoundUri(mPrefsFragment.getSoundUri());
        mLedSettings.setSoundOnlyOnce(mPrefsFragment.getSoundOnlyOnce());
        mLedSettings.setSoundOnlyOnceTimeout(mPrefsFragment.getSoundOnlyOnceTimeout());
        mLedSettings.setInsistent(mPrefsFragment.getInsistent());
        mLedSettings.setVibrateOverride(mPrefsFragment.getVibrateOverride());
        mLedSettings.setVibratePatternFromString(mPrefsFragment.getVibratePatternAsString());
        mLedSettings.setActiveScreenMode(mPrefsFragment.getActiveScreenMode());
        mLedSettings.setActiveScreenIgnoreUpdate(mPrefsFragment.getActiveScreenIgnoreUpdate());
        mLedSettings.setLedMode(mPrefsFragment.getLedMode());
        mLedSettings.setQhIgnore(mPrefsFragment.getQhIgnore());
        mLedSettings.setQhIgnoreList(mPrefsFragment.getQhIgnoreList());
        mLedSettings.setHeadsUpMode(mPrefsFragment.getHeadsUpMode());
        mLedSettings.setHeadsUpDnd(mPrefsFragment.getHeadsUpDnd());
        mLedSettings.setHeadsUpTimeout(mPrefsFragment.getHeadsUpTimeout());
        mLedSettings.setProgressTracking(mPrefsFragment.getProgressTracking());
        mLedSettings.setVisibility(mPrefsFragment.getVisibility());
        mLedSettings.setVisibilityLs(mPrefsFragment.getVisibilityLs());
        mLedSettings.setSoundToVibrateDisabled(mPrefsFragment.getSoundToVibrateDisabled());
        mLedSettings.setVibrateReplace(mPrefsFragment.getVibrateReplace());
        mLedSettings.setSoundReplace(mPrefsFragment.getSoundReplace());
        mLedSettings.setHidePersistent(mPrefsFragment.getHidePersistent());
        mLedSettings.setLedDnd(mPrefsFragment.getLedDnd());
        mLedSettings.setLedIgnoreUpdate(mPrefsFragment.getLedIgnoreUpdate());
        if (!isPreview) {
            mLedSettings.serialize();
            Intent intent = new Intent();
            intent.putExtra(EXTRA_PACKAGE_NAME, mLedSettings.getPackageName());
            setResult(RESULT_OK, intent);
            finish();
        } else {
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            Notification.Builder builder = new Notification.Builder(this, GravityBoxApplication.NOTIF_CHANNEL_UNC)
                .setContentTitle(getString(R.string.lc_preview_notif_title))
                .setContentText(String.format(Locale.getDefault(),
                        getString(R.string.lc_preview_notif_text), getTitle()))
                .setSmallIcon(R.drawable.ic_notif_gravitybox)
                .setLargeIcon(Icon.createWithResource(this, R.drawable.ic_launcher));

            final Notification n = builder.build();
            n.extras.putBoolean("gbUncPreviewNotification", true);
            mLedSettings.serialize(true, new LedSettings.Callback() {
                @Override
                public void onSerialized() {
                    Intent intent = new Intent(ModHwKeys.ACTION_SLEEP);
                    sendBroadcast(intent);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            nm.notify(++NOTIF_ID,  n);
                        }
                    }, 1000);
                }
            });
        }
    }
}
