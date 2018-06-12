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

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.GravityBoxActivity;
import com.ceco.oreo.gravitybox.SettingsManager;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.WorldReadablePrefs;
import com.ceco.oreo.gravitybox.WorldReadablePrefs.OnPreferencesCommitedListener;
import com.ceco.oreo.gravitybox.WorldReadablePrefs.OnSharedPreferenceChangeCommitedListener;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

public class QuietHoursActivity extends GravityBoxActivity {

    public static final String PREF_KEY_QH_LOCKED = "pref_lc_qh_locked";
    public static final String PREF_KEY_QH_ENABLED = "pref_lc_qh_enabled";
    public static final String PREF_KEY_QH_START = "pref_lc_qh_start2";
    public static final String PREF_KEY_QH_END = "pref_lc_qh_end2";
    public static final String PREF_KEY_QH_START_ALT = "pref_lc_qh_start_alt2";
    public static final String PREF_KEY_QH_END_ALT = "pref_lc_qh_end_alt2";
    public static final String PREF_KEY_QH_MUTE_LED = "pref_lc_qh_mute_led";
    public static final String PREF_KEY_QH_MUTE_VIBE = "pref_lc_qh_mute_vibe";
    public static final String PREF_KEY_QH_MUTE_SYSTEM_SOUNDS = "pref_lc_qh_mute_system_sounds";
    public static final String PREF_KEY_QH_RINGER_WHITELIST = "pref_lc_qh_ringer_whitelist";
    public static final String PREF_KEY_QH_STATUSBAR_ICON = "pref_lc_qh_statusbar_icon";
    public static final String PREF_KEY_QH_MODE = "pref_lc_qh_mode";
    public static final String PREF_KEY_QH_INTERACTIVE = "pref_lc_qh_interactive";
    public static final String PREF_KEY_QH_WEEKDAYS = "pref_lc_qh_weekdays";
    public static final String PREF_KEY_MUTE_SYSTEM_VIBE = "pref_lc_qh_mute_system_vibe";

    public static final String ACTION_QUIET_HOURS_CHANGED = 
            "gravitybox.intent.action.QUIET_HOURS_CHANGED";
    public static final String ACTION_SET_QUIET_HOURS_MODE = 
            "gravitybox.intent.action.SET_QUIET_HOURS_MODE";

    public static final String EXTRA_QH_LOCKED = "qhLocked";
    public static final String EXTRA_QH_ENABLED = "qhEnabled";
    public static final String EXTRA_QH_START = "qhStart";
    public static final String EXTRA_QH_END = "qhEnd";
    public static final String EXTRA_QH_START_ALT = "qhStartAlt";
    public static final String EXTRA_QH_END_ALT = "qhEndAlt";
    public static final String EXTRA_QH_MUTE_LED = "qhMuteLed";
    public static final String EXTRA_QH_MUTE_VIBE = "qhMuteVibe";
    public static final String EXTRA_QH_MUTE_SYSTEM_SOUNDS = "qhMuteSystemSounds";
    public static final String EXTRA_QH_STATUSBAR_ICON = "qhStatusbarIcon";
    public static final String EXTRA_QH_MODE = "qhMode";
    public static final String EXTRA_QH_INTERACTIVE = "qhInteractive";
    public static final String EXTRA_QH_WEEKDAYS = "qhWeekDays";
    public static final String EXTRA_QH_MUTE_SYSTEM_VIBE = "qhMuteSystemVibe";
    public static final String EXTRA_QH_RINGER_WHITELIST = "qhRingerWhitelist";

    protected static void broadcastSettings(Context ctx, SharedPreferences prefs) {
        Intent intent = new Intent(ACTION_QUIET_HOURS_CHANGED);
        intent.putExtra(EXTRA_QH_LOCKED, prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_LOCKED, false));
        intent.putExtra(EXTRA_QH_ENABLED, prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_ENABLED, false));
        intent.putExtra(EXTRA_QH_START, prefs.getInt(QuietHoursActivity.PREF_KEY_QH_START, 1380));
        intent.putExtra(EXTRA_QH_END, prefs.getInt(QuietHoursActivity.PREF_KEY_QH_END, 360));
        intent.putExtra(EXTRA_QH_START_ALT, prefs.getInt(QuietHoursActivity.PREF_KEY_QH_START_ALT, 1380));
        intent.putExtra(EXTRA_QH_END_ALT, prefs.getInt(QuietHoursActivity.PREF_KEY_QH_END_ALT, 360));
        intent.putExtra(EXTRA_QH_MUTE_LED, prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_MUTE_LED, false));
        intent.putExtra(EXTRA_QH_MUTE_VIBE, prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_MUTE_VIBE, true));
        intent.putStringArrayListExtra(EXTRA_QH_MUTE_SYSTEM_SOUNDS, new ArrayList<String>(
                 prefs.getStringSet(QuietHoursActivity.PREF_KEY_QH_MUTE_SYSTEM_SOUNDS, new HashSet<String>())));
        intent.putExtra(EXTRA_QH_STATUSBAR_ICON, prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_STATUSBAR_ICON, true));
        intent.putExtra(EXTRA_QH_MODE, prefs.getString(QuietHoursActivity.PREF_KEY_QH_MODE, "AUTO"));
        intent.putExtra(EXTRA_QH_INTERACTIVE, prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_INTERACTIVE, false));
        intent.putStringArrayListExtra(EXTRA_QH_WEEKDAYS, new ArrayList<String>(prefs.getStringSet(
                QuietHoursActivity.PREF_KEY_QH_WEEKDAYS, new HashSet<String>(Arrays.asList("2","3","4","5","6")))));
        intent.putExtra(EXTRA_QH_MUTE_SYSTEM_VIBE, prefs.getBoolean(QuietHoursActivity.PREF_KEY_MUTE_SYSTEM_VIBE, false));
        intent.putStringArrayListExtra(EXTRA_QH_RINGER_WHITELIST, new ArrayList<String>(
                prefs.getStringSet(PREF_KEY_QH_RINGER_WHITELIST, new HashSet<String>())));
        ctx.sendBroadcast(intent);
    }

    public static void setQuietHoursMode(final Context context, String mode, boolean showToast) {
        try {
            final WorldReadablePrefs prefs = SettingsManager.getInstance(context).getQuietHoursPrefs();
            QuietHours qh = new QuietHours(prefs);
            if (qh.uncLocked || !qh.enabled) {
                return;
            }

            final QuietHours.Mode qhMode;
            if (mode != null) {
                qhMode = QuietHours.Mode.valueOf(mode);
            } else {
                switch (qh.mode) {
                    default:
                    case ON:
                        if (Utils.isAppInstalled(context, QuietHours.PKG_WEARABLE_APP)) {
                            qhMode = QuietHours.Mode.WEAR;
                        } else {
                            qhMode = QuietHours.Mode.OFF;
                        }
                        break;
                    case AUTO:
                        qhMode = qh.quietHoursActive() ? 
                                QuietHours.Mode.OFF : QuietHours.Mode.ON;
                        break;
                    case OFF:
                        qhMode = QuietHours.Mode.ON;
                        break;
                    case WEAR:
                        qhMode = QuietHours.Mode.OFF;
                        break;
                }
            }
            prefs.edit().putString(QuietHoursActivity.PREF_KEY_QH_MODE,
                    qhMode.toString()).commit(new OnPreferencesCommitedListener() {
                @Override
                public void onPreferencesCommited() {
                    if (WorldReadablePrefs.DEBUG)
                        Log.d("GravityBox", "QuietHoursActivity: setQuietHoursMode() onPreferencesCommited");
                    broadcastSettings(context, prefs);
                }
            });
            if (showToast) {
                Toast.makeText(context, QuietHoursActivity.getToastResIdFromMode(qhMode),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getToastResIdFromMode(QuietHours.Mode mode) {
        switch (mode) {
            case ON: return R.string.quiet_hours_on;
            case OFF: return R.string.quiet_hours_off;
            case AUTO: return R.string.quiet_hours_auto;
            case WEAR: return R.string.quiet_hours_wear;
            default: return 0;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
        }
    }

    public static class PrefsFragment extends PreferenceFragment implements
                OnSharedPreferenceChangeListener, OnSharedPreferenceChangeCommitedListener {

        private WorldReadablePrefs mPrefs;
        private MultiSelectListPreference mPrefWeekDays;
        private MultiSelectListPreference mPrefSystemSounds;
        private Preference mPrefRingerWhitelist;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName("quiet_hours");
            if (Utils.USE_DEVICE_PROTECTED_STORAGE) {
                getPreferenceManager().setStorageDeviceProtected();
            }
            mPrefs = SettingsManager.getInstance(getActivity()).getQuietHoursPrefs();

            addPreferencesFromResource(R.xml.led_control_quiet_hours_settings);
            setupWeekDaysPref();
            mPrefSystemSounds = (MultiSelectListPreference) findPreference(PREF_KEY_QH_MUTE_SYSTEM_SOUNDS);

            mPrefRingerWhitelist = findPreference(PREF_KEY_QH_RINGER_WHITELIST);
        }

        private void setupWeekDaysPref() {
            mPrefWeekDays = (MultiSelectListPreference) findPreference(PREF_KEY_QH_WEEKDAYS); 
            String[] days = new DateFormatSymbols(Locale.getDefault()).getWeekdays();
            CharSequence[] entries = new CharSequence[7];
            CharSequence[] entryValues = new CharSequence[7];
            for (int i=1; i<=7; i++) {
                entries[i-1] = days[i];
                entryValues[i-1] = String.valueOf(i);
            }
            mPrefWeekDays.setEntries(entries);
            mPrefWeekDays.setEntryValues(entryValues);
            if (mPrefs.getStringSet(PREF_KEY_QH_WEEKDAYS, null) == null) {
                Set<String> value = new HashSet<String>(Arrays.asList("2","3","4","5","6"));
                mPrefs.edit().putStringSet(PREF_KEY_QH_WEEKDAYS, value).commit();
                mPrefWeekDays.setValues(value);
            }
        }

        private void updateSummaries() {
            String[] days = new DateFormatSymbols(Locale.getDefault()).getWeekdays();
            Set<String> values = new TreeSet<String>(mPrefWeekDays.getValues());
            String summary = "";
            for (String wday : values) {
                if (!summary.isEmpty()) summary += ", ";
                summary += days[Integer.valueOf(wday)];
            }
            mPrefWeekDays.setSummary(summary);

            CharSequence[] entries = mPrefSystemSounds.getEntries();
            CharSequence[] entryValues = mPrefSystemSounds.getEntryValues();
            values = mPrefSystemSounds.getValues();
            summary = "";
            if (values != null) {
                for (String value : values) {
                    for (int i=0; i<entryValues.length; i++) {
                        if (entryValues[i].equals(value)) {
                            if (!summary.isEmpty()) summary += ", ";
                            summary += entries[i];
                            break;
                        }
                    }
                }
            }
            mPrefSystemSounds.setSummary(summary);

            mPrefRingerWhitelist.setEnabled(values != null && values.contains("ringer"));
        }

        @Override
        public void onResume() {
            super.onResume();
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            mPrefs.setOnSharedPreferenceChangeCommitedListener(this);
            updateSummaries();
        }

        @Override
        public void onPause() {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            mPrefs.setOnSharedPreferenceChangeCommitedListener(null);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            updateSummaries();
        }

        @Override
        public void onSharedPreferenceChangeCommited() {
            if (WorldReadablePrefs.DEBUG)
                Log.d("GravityBox", "QuietHoursActivity: onSharedPreferenceChangeCommited");
            broadcastSettings(getActivity(), mPrefs);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                Preference preference) {
            if (preference == mPrefRingerWhitelist) {
                Intent intent = new Intent(getActivity(), RingerWhitelistActivity.class);
                getActivity().startActivity(intent);
                return true;
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
    }
}
