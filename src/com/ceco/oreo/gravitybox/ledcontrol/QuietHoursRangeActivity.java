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
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.ceco.oreo.gravitybox.GravityBoxActivity;
import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.Utils;
import com.ceco.oreo.gravitybox.preference.TimePreference;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.View;
import android.preference.PreferenceFragment;
import android.widget.Button;

public class QuietHoursRangeActivity extends GravityBoxActivity {

    public static final String PREF_QH_RANGE_DAYS = "pref_lc_qh_range_days";
    public static final String PREF_QH_RANGE_START = "pref_lc_qh_range_start";
    public static final String PREF_QH_RANGE_END = "pref_lc_qh_range_end";
    public static final String EXTRA_QH_RANGE = "qhRange";

    private Button mBtnCancel;
    private Button mBtnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String rangeValue = null;
        final Intent intent = getIntent();
        if (intent != null) {
            rangeValue = intent.getStringExtra(EXTRA_QH_RANGE);
        }

        setContentView(R.layout.quiet_hours_range_activity);

        mBtnCancel = findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        mBtnSave = findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_QH_RANGE, getFragment().getRange().getValue());
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        getFragment().setRange(QuietHours.Range.parse(rangeValue));
    }

    private PrefsFragment getFragment() {
        return (PrefsFragment) getFragmentManager()
                .findFragmentById(R.id.prefs_fragment);
    }

    public static class PrefsFragment extends PreferenceFragment
                                      implements OnPreferenceChangeListener {

        private MultiSelectListPreference mPrefDays;
        private TimePreference mPrefStartTime;
        private TimePreference mPrefEndTime;
        private QuietHours.Range mRange;

        public PrefsFragment() { }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (Utils.USE_DEVICE_PROTECTED_STORAGE) {
                getPreferenceManager().setStorageDeviceProtected();
            }
            addPreferencesFromResource(R.xml.quiet_hours_range_settings);

            mPrefDays = (MultiSelectListPreference) findPreference(PREF_QH_RANGE_DAYS); 
            String[] days = new DateFormatSymbols(Locale.getDefault()).getWeekdays();
            CharSequence[] entries = new CharSequence[7];
            CharSequence[] entryValues = new CharSequence[7];
            for (int i=1; i<=7; i++) {
                entries[i-1] = days[i];
                entryValues[i-1] = String.valueOf(i);
            }
            mPrefDays.setEntries(entries);
            mPrefDays.setEntryValues(entryValues);
            mPrefDays.setOnPreferenceChangeListener(this);

            mPrefStartTime = (TimePreference) findPreference(PREF_QH_RANGE_START);
            mPrefStartTime.setOnPreferenceChangeListener(this);

            mPrefEndTime = (TimePreference) findPreference(PREF_QH_RANGE_END);
            mPrefEndTime.setOnPreferenceChangeListener(this);
        }

        void setRange(QuietHours.Range range) {
            mRange = range;
            mPrefDays.setValues(mRange.days);
            mPrefStartTime.setValue(mRange.startTime);
            mPrefEndTime.setValue(mRange.endTime);
            updateSummaries();
        }

        QuietHours.Range getRange() {
            return mRange;
        }

        private void updateSummaries() {
            String[] days = new DateFormatSymbols(Locale.getDefault()).getWeekdays();
            Set<String> values = new TreeSet<String>(mRange.days);
            String summary = "";
            for (String wday : values) {
                if (!summary.isEmpty()) summary += ", ";
                try {
                    summary += days[Integer.valueOf(wday)];
                } catch (NumberFormatException e) { }
            }
            mPrefDays.setSummary(summary);

            mPrefEndTime.setSummarySuffix(mRange.endsNextDay() ?
                    getString(R.string.next_day) : null);
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mPrefDays) {
                mRange.days = (Set<String>) newValue;
            } else if (preference == mPrefStartTime) {
                mRange.startTime = (int) newValue;
            } else if (preference == mPrefEndTime) {
                mRange.endTime = (int) newValue;
            }
            updateSummaries();
            return true;
        }
    }
}
