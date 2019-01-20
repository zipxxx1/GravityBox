/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.oreo.gravitybox.managers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XResources;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;

import com.ceco.oreo.gravitybox.BroadcastSubReceiver;
import com.ceco.oreo.gravitybox.GravityBox;
import com.ceco.oreo.gravitybox.tuner.TuneableItem;
import com.ceco.oreo.gravitybox.tuner.TunerBlacklist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class TunerManager implements BroadcastSubReceiver {
    public static final String TAG="GB:TunerManager";
    private static boolean DEBUG = false;

    public static final String SETTING_TUNER_TRIAL_COUNTDOWN = "gravitybox_tuner_trial_countdown";
    public static final String ACTION_GET_TUNEABLES = "gravitybox.intent.action.TUNER_GET_TUNABLES";
    public static final String EXTRA_TUNER_CATEGORY = "tunerCategory";
    public static final String EXTRA_TUNEABLES = "tunerTuneables";

    public enum Category { FRAMEWORK, SYSTEMUI }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    private Context mContext;

    TunerManager(Context context) {
        mContext = context;

        updateTrialCountdown();
        if (DEBUG) log("created");
    }

    private void updateTrialCountdown() {
        try {
            final ContentResolver cr = mContext.getContentResolver();
            int trialCountdown = Settings.System.getInt(cr,
                    SETTING_TUNER_TRIAL_COUNTDOWN, -1);
            if (trialCountdown == -1) {
                Settings.System.putInt(cr,
                        SETTING_TUNER_TRIAL_COUNTDOWN, 30);
            } else {
                if (--trialCountdown >= 0) {
                    Settings.System.putInt(cr,
                            SETTING_TUNER_TRIAL_COUNTDOWN, trialCountdown);
                }
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (ACTION_GET_TUNEABLES.equals(intent.getAction()) &&
                intent.hasExtra("receiver") &&
                intent.hasExtra(EXTRA_TUNER_CATEGORY)) {
            if (DEBUG) log("Request for tuneables received");
            ResultReceiver receiver = intent.getParcelableExtra("receiver");
            Category category = Category.valueOf(intent.getStringExtra(EXTRA_TUNER_CATEGORY));
            sendTuneables(category, receiver);
        }
    }

    private void sendTuneables(Category category, ResultReceiver receiver) {
        String pkgName = getPackageNameFor(category);
        Resources res = getResourcesFor(category);
        String className = getResourceClassNameFor(category);
        ArrayList<TuneableItem> tiList = new ArrayList<>();
        Bundle data = new Bundle();
        Class<?> clazz;

        // bools
        clazz = XposedHelpers.findClassIfExists(className + ".bool",
                    mContext.getClassLoader());
        if (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (TunerBlacklist.isBlacklisted(category, f.getName()))
                    continue;
                try {
                    TuneableItem ti = new TuneableItem(Boolean.class, category, f.getName(),
                            res.getBoolean(res.getIdentifier(f.getName(), "bool", pkgName)));
                    tiList.add(ti);
                } catch (Resources.NotFoundException ignore) {
                }
            }
        } else {
            GravityBox.log(TAG, "Boolean resource class name not found for " + category);
        }

        // integers
        clazz = XposedHelpers.findClassIfExists(className + ".integer",
                mContext.getClassLoader());
        if (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (TunerBlacklist.isBlacklisted(category, f.getName()))
                    continue;
                try {
                    TuneableItem ti = new TuneableItem(Integer.class, category, f.getName(),
                            res.getInteger(res.getIdentifier(
                                    f.getName(), "integer", pkgName)));
                    tiList.add(ti);
                } catch (Resources.NotFoundException ignore) {
                }
            }
        } else {
            GravityBox.log(TAG, "Integer resource class name not found for " + category);
        }

        data.putParcelableArrayList(EXTRA_TUNEABLES, tiList);
        receiver.send(0, data);
        if (DEBUG) log("Tuneables sent to receiver");
    }

    private String getPackageNameFor(Category category) {
        switch(category) {
            default:
            case FRAMEWORK: return "android";
            case SYSTEMUI: return "com.android.systemui";
        }
    }

    private Resources getResourcesFor(Category category) {
        switch (category) {
            default:
            case FRAMEWORK: return XResources.getSystem();
            case SYSTEMUI: return mContext.getResources();
        }
    }

    private String getResourceClassNameFor(Category category) {
        switch (category) {
            default:
            case FRAMEWORK: return "com.android.internal.R";
            case SYSTEMUI: return "com.android.systemui.plugins.R";
        }
    }

    private static List<TuneableItem> getUserItemList(Category category, SharedPreferences prefs) {
        List<TuneableItem> out = new ArrayList<>();
        Map<String, ?> prefMap = prefs.getAll();
        for (Map.Entry<String, ?> pref : prefMap.entrySet()) {
            if (pref.getKey().startsWith("tuneable:")) {
                TuneableItem item = TuneableItem.createUserInstance(pref.getKey(), prefs);
                if (item != null && item.getCategory() == category && item.isOverridden() &&
                        !TunerBlacklist.isBlacklisted(category, item.getKey())) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    public static void applyFrameworkConfiguration(SharedPreferences prefs) {
        for (TuneableItem item : getUserItemList(Category.FRAMEWORK, prefs)) {
            try {
                XResources.setSystemWideReplacement("android",
                        item.getResourceType(), item.getKey(), item.getUserValue());
                if (DEBUG) log("Framework replacement: key=" + item.getKey() +
                        "; value=" + item.getUserValue());
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    }

    public static void applySystemUiConfiguration(SharedPreferences prefs, XResources res) {
        for (TuneableItem item : getUserItemList(Category.SYSTEMUI, prefs)) {
            try {
                res.setReplacement("com.android.systemui",
                        item.getResourceType(), item.getKey(), item.getUserValue());
                if (DEBUG) log("System UI replacement: key=" + item.getKey() +
                        "; value=" + item.getUserValue());
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }
    }
}
