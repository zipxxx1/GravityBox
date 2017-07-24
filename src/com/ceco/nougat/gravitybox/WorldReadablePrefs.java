/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.nougat.gravitybox;

import java.io.File;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class WorldReadablePrefs implements SharedPreferences {

    private String mPrefsName;
    private Context mContext;
    private SharedPreferences mPrefs;

    public WorldReadablePrefs(Context ctx, String prefsName) {
        mContext = ctx;
        mPrefsName = prefsName;
        mPrefs = ctx.getSharedPreferences(mPrefsName, 0);
        maybePreCreateFile();
        fixPermissions();
    }

    @Override
    public boolean contains(String key) {
        return mPrefs.contains(key);
    }

    @Override
    public Editor edit() {
        return new EditorWrapper(mPrefs.edit());
    }

    @Override
    public Map<String, ?> getAll() {
        return mPrefs.getAll();
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return mPrefs.getBoolean(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return mPrefs.getFloat(key, defValue);
    }

    @Override
    public int getInt(String key, int defValue) {
        return mPrefs.getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return mPrefs.getLong(key, defValue);
    }

    @Override
    public String getString(String key, String defValue) {
        return mPrefs.getString(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return mPrefs.getStringSet(key, defValues);
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mPrefs.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private void maybePreCreateFile() {
        try {
            File sharedPrefsFolder = new File(mContext.getFilesDir().getAbsolutePath()
                    + "/../shared_prefs");
            if (!sharedPrefsFolder.exists()) {
                sharedPrefsFolder.mkdir();
                sharedPrefsFolder.setExecutable(true, false);
                sharedPrefsFolder.setReadable(true, false);
            }
            File f = new File(sharedPrefsFolder.getAbsolutePath() + "/" + mPrefsName + ".xml");
            if (!f.exists()) {
                f.createNewFile();
                f.setExecutable(true, false);
                f.setReadable(true, false);
            }
        } catch (Exception e) {
            Log.e("GravityBox", "Error pre-creating prefs file " + mPrefsName + ": " + e.getMessage());
        }
    }

    public void fixPermissions() {
        File sharedPrefsFolder = new File(mContext.getFilesDir().getAbsolutePath()
                + "/../shared_prefs");
        if (sharedPrefsFolder.exists()) {
            sharedPrefsFolder.setExecutable(true, false);
            sharedPrefsFolder.setReadable(true, false);
            File f = new File(sharedPrefsFolder.getAbsolutePath() + "/" + mPrefsName + ".xml");
            if (f.exists()) {
                f.setExecutable(true, false);
                f.setReadable(true, false);
            }
        }
    }

    public class EditorWrapper implements SharedPreferences.Editor {

        private SharedPreferences.Editor mEditor;

        public EditorWrapper(SharedPreferences.Editor editor) {
            mEditor = editor;
        }

        @Override
        public android.content.SharedPreferences.Editor putString(String key,
                String value) {
            return mEditor.putString(key, value);
        }

        @Override
        public android.content.SharedPreferences.Editor putStringSet(String key,
                Set<String> values) {
            return mEditor.putStringSet(key, values);
        }

        @Override
        public android.content.SharedPreferences.Editor putInt(String key,
                int value) {
            return mEditor.putInt(key, value);
        }

        @Override
        public android.content.SharedPreferences.Editor putLong(String key,
                long value) {
            return mEditor.putLong(key, value);
        }

        @Override
        public android.content.SharedPreferences.Editor putFloat(String key,
                float value) {
            return mEditor.putFloat(key, value);
        }

        @Override
        public android.content.SharedPreferences.Editor putBoolean(String key,
                boolean value) {
            return mEditor.putBoolean(key, value);
        }

        @Override
        public android.content.SharedPreferences.Editor remove(String key) {
            return mEditor.remove(key);
        }

        @Override
        public android.content.SharedPreferences.Editor clear() {
            return mEditor.clear();
        }

        @Override
        public boolean commit() {
            boolean ret = mEditor.commit();
            fixPermissions();
            return ret;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
