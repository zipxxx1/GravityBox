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
package com.ceco.pie.gravitybox.managers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.Settings;

import com.ceco.pie.gravitybox.BroadcastSubReceiver;
import com.ceco.pie.gravitybox.GravityBox;
import com.ceco.pie.gravitybox.ResourceProxy;
import com.ceco.pie.gravitybox.tuner.TuneableItem;
import com.ceco.pie.gravitybox.tuner.TunerBlacklist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static Map<Category,List<TuneableItem>> sUserItemsCache = new HashMap<>();

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

    private static Category getCategoryFor(String pkgName) {
        switch (pkgName) {
            case "android": return Category.FRAMEWORK;
            case "com.android.systemui": return Category.SYSTEMUI;
            default: return null;
        }
    }

    private Resources getResourcesFor(Category category) {
        switch (category) {
            default:
            case FRAMEWORK: return Resources.getSystem();
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
        if (sUserItemsCache.get(category) == null) {
            List<TuneableItem> list = new ArrayList<>();
            Map<String, ?> prefMap = prefs.getAll();
            for (Map.Entry<String, ?> pref : prefMap.entrySet()) {
                if (pref.getKey().startsWith(category.toString() + ":") ||
                        pref.getKey().startsWith("tuneable:")) {
                    TuneableItem item = TuneableItem.createUserInstance(pref.getKey(), prefs);
                    if (item != null && item.getCategory() == category && item.isOverridden() &&
                            !TunerBlacklist.isBlacklisted(category, item.getKey())) {
                        list.add(item);
                    }
                }
            }
            sUserItemsCache.put(category, list);
        }
        return sUserItemsCache.get(category);
    }

    public static void addUserItemKeysToList(Category category, SharedPreferences prefs, List<String> list) {
        for (TuneableItem item : TunerManager.getUserItemList(category, prefs)) {
            if (!list.contains(item.getKey())) {
                list.add(item.getKey());
            }
        }
    }

    private static TuneableItem findUserItemByKey(Category category, SharedPreferences prefs, String key) {
        for (TuneableItem item : getUserItemList(category, prefs)) {
            if (item.getKey().equals(key))
                return item;
        }
        return null;
    }

    public static boolean onIntercept(SharedPreferences prefs,
                                      ResourceProxy.ResourceSpec spec) {
        Category category = getCategoryFor(spec.pkgName);
        if (category != null) {
            TuneableItem item = findUserItemByKey(category, prefs, spec.name);
            if (item != null) {
                spec.value = item.getUserValue();
                if (DEBUG) log("onIntercept: key=" + spec.name +
                        "; value=" + spec.value);
                return true;
            }
        }
        return false;
    }
}
