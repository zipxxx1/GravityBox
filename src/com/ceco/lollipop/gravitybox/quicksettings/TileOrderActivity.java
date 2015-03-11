/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.R;
import com.ceco.lollipop.gravitybox.TouchInterceptor;
import com.ceco.lollipop.gravitybox.Utils;
import com.ceco.lollipop.gravitybox.ledcontrol.LedSettings;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

public class TileOrderActivity extends ListActivity implements View.OnClickListener {
    private static final String PREF_KEY_TILE_ORDER = "pref_qs_tile_order3";
    public static final String PREF_KEY_TILE_ENABLED = "pref_qs_tile_enabled";
    public static final String PREF_KEY_TILE_SECURED = "pref_qs_tile_secured";
    public static final String EXTRA_QS_ORDER_CHANGED = "qsTileOrderChanged";
    private static final String INFO_DISMISSED = "pref_qs_info_dismissed";

    private ListView mTileList;
    private TileAdapter mTileAdapter;
    private Context mContext;
    private Resources mResources;
    private SharedPreferences mPrefs;
    private Map<String, String> mTileTexts;
    private List<TileInfo> mOrderedTileList;
    private Button mBtnSave;
    private Button mBtnCancel;
    private Button mBtnInfoOk;

    static class TileInfo {
        String key;
        String name;
        boolean enabled;
        boolean secured;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(R.style.AppThemeDark);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.order_tile_list_activity);

        mContext = this;
        mResources = mContext.getResources();
        final String prefsName = mContext.getPackageName() + "_preferences";
        mPrefs = mContext.getSharedPreferences(prefsName, Context.MODE_WORLD_READABLE);

        mBtnSave = (Button) findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(this);
        mBtnCancel = (Button) findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(this);

        final View info = findViewById(R.id.info);
        info.setVisibility(mPrefs.getBoolean(INFO_DISMISSED, false) ?
                View.GONE : View.VISIBLE);
        mBtnInfoOk = (Button) findViewById(R.id.btnInfoOk);
        mBtnInfoOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.edit().putBoolean(INFO_DISMISSED, true).commit();
                info.setVisibility(View.GONE);
            }
        });

        mTileList = getListView();
        ((TouchInterceptor) mTileList).setDropListener(mDropListener);
        mTileAdapter = new TileAdapter(mContext);

        String[] allTileKeys = mResources.getStringArray(R.array.qs_tile_values);
        String[] allTileNames = mResources.getStringArray(R.array.qs_tile_entries);
        mTileTexts = new HashMap<String, String>();
        for (int i = 0; i < allTileKeys.length; i++) {
            mTileTexts.put(allTileKeys[i], allTileNames[i]);
        }

        if (mPrefs.getString(PREF_KEY_TILE_ORDER, null) == null) {
            createDefaultTileList();
        } else {
            updateDefaultTileList();
        }
    }

    private void createDefaultTileList() {
        String[] tileKeys = mResources.getStringArray(R.array.qs_tile_values);
        String newList = "";

        for (String key : tileKeys) {
            if (supportedTile(key)) {
                if (!newList.isEmpty()) newList += ",";
                newList += key;
            }
        }

        mPrefs.edit().putString(PREF_KEY_TILE_ORDER, newList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_ENABLED,Utils.join(mResources.getStringArray(
                R.array.qs_tile_default_values), ",")).commit();
    }

    private void updateDefaultTileList() {
        String list = mPrefs.getString(PREF_KEY_TILE_ORDER, "");
        String enabledList = mPrefs.getString(PREF_KEY_TILE_ENABLED, "");
        boolean listChanged = false;
        boolean enabledListChanged = false;
        String[] tileKeys = mResources.getStringArray(R.array.qs_tile_values);
        for (String key : tileKeys) {
            if (supportedTile(key)) {
                if (!list.contains(key)) {
                    if (!list.isEmpty()) list += ",";
                    list += key;
                    listChanged = true;
                }
            } else {
                if (list.contains(key)) {
                    list = list.replace("," + key, "");
                    list = list.replace(key + ",", "");
                    listChanged = true;
                }
                if (enabledList.contains(key)) {
                    enabledList = enabledList.replace("," + key, "");
                    enabledList = enabledList.replace(key + ",", "");
                    enabledListChanged = true;
                }
            }
        }
        // forcibly remove old cell2 tile
        if (list.contains("aosp_tile_cell2")) {
            list = list.replace(",aosp_tile_cell2", "");
            list = list.replace("aosp_tile_cell2,", "");
            listChanged = true;
        }
        if (enabledList.contains("aosp_tile_cell2")) {
            enabledList = enabledList.replace(",aosp_tile_cell2", "");
            enabledList = enabledList.replace("aosp_tile_cell2,", "");
            enabledListChanged = true;
        }
        if (listChanged) {
            mPrefs.edit().putString(PREF_KEY_TILE_ORDER, list).commit();
        }
        if (enabledListChanged) {
            mPrefs.edit().putString(PREF_KEY_TILE_ENABLED, enabledList).commit();
        }
    }

    private boolean supportedTile(String key) {
        if (key.equals("gb_tile_torch") && !Utils.hasFlash(mContext))
            return false;
        if (key.equals("gb_tile_gps_alt") && !Utils.hasGPS(mContext))
            return false;
        if ((key.equals("aosp_tile_cell") || key.equals("aosp_tile_2cell") ||
                key.equals("gb_tile_network_mode") || key.equals("gb_tile_smart_radio") ||
                key.equals("mtk_tile_mobile_data")) && Utils.isWifiOnly(mContext))
            return false;
        if (key.equals("gb_tile_nfc") && !Utils.hasNfc(mContext))
            return false;
        if (key.equals("gb_tile_quiet_hours") && LedSettings.isUncLocked(mContext))
            return false;
        if (key.equals("gb_tile_compass") && !Utils.hasCompass(mContext))
            return false;
        if (key.equals("aosp_tile_2cell") && GravityBoxSettings.sSystemProperties != null &&
                !GravityBoxSettings.sSystemProperties.hasMsimSupport)
            return false;
        if ((key.equals("mtk_tile_mobile_data") || key.equals("mtk_tile_audio_profile")) &&
                !Utils.isMtkDevice())
            return false;
        if (key.equals("gb_tile_smart_radio") && !mPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false))
            return false;

        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        mOrderedTileList = getOrderedTileList();
        setListAdapter(mTileAdapter);
    }

    @Override
    public void onStop() {
        super.onStop();
        setListAdapter(null);
        mOrderedTileList = null;
    }

    @Override
    public void onDestroy() {
        ((TouchInterceptor) mTileList).setDropListener(null);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        // reload our tiles and invalidate the views for redraw
        mTileList.invalidateViews();
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnSave) {
            saveOrderedTileList();
            finish();
        } else if (v == mBtnCancel) {
            finish();
        }
    }

    private TouchInterceptor.DropListener mDropListener = new TouchInterceptor.DropListener() {
        public void drop(int from, int to) {
            // move the tile
            if (from < mOrderedTileList.size()) {
                TileInfo tile = mOrderedTileList.remove(from);
                if (to <= mOrderedTileList.size()) {
                    mOrderedTileList.add(to, tile);
                    mTileList.invalidateViews();
                }
            }
        }
    };

    private List<TileInfo> getOrderedTileList() {
        String[] orderedTiles = mPrefs.getString(PREF_KEY_TILE_ORDER, "").split(",");
        String enabledTiles = mPrefs.getString(PREF_KEY_TILE_ENABLED, "");
        String securedTiles = mPrefs.getString(PREF_KEY_TILE_SECURED, "");

        List<TileInfo> tiles = new ArrayList<TileInfo>();
        for (int i = 0; i < orderedTiles.length; i++) {
            TileInfo ti = new TileInfo();
            ti.key = orderedTiles[i];
            ti.name = mTileTexts.get(ti.key);
            ti.enabled = enabledTiles.contains(ti.key);
            ti.secured = securedTiles.contains(ti.key);
            tiles.add(ti);
        }

        return tiles;
    }

    private void saveOrderedTileList() {
        String newOrderedList = "";
        String newEnabledList = "";
        String newSecuredList = "";

        for (TileInfo ti : mOrderedTileList) {
            if (!newOrderedList.isEmpty()) newOrderedList += ",";
            newOrderedList += ti.key;

            if (ti.enabled) {
                if (!newEnabledList.isEmpty()) newEnabledList += ",";
                newEnabledList += ti.key;
            }

            if (ti.secured) {
                if (!newSecuredList.isEmpty()) newSecuredList += ",";
                newSecuredList += ti.key;
            }
        }

        mPrefs.edit().putString(PREF_KEY_TILE_ORDER, newOrderedList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_ENABLED, newEnabledList).commit();
        mPrefs.edit().putString(PREF_KEY_TILE_SECURED, newSecuredList).commit();
        Intent intent = new Intent(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED);
        intent.putExtra(EXTRA_QS_ORDER_CHANGED, true);
        mContext.sendBroadcast(intent);
    }

    private class TileAdapter extends BaseAdapter {
        private Context mContext;
        private LayoutInflater mInflater;

        public TileAdapter(Context c) {
            mContext = c;
            mInflater = LayoutInflater.from(mContext);
        }

        public int getCount() {
            return mOrderedTileList.size();
        }

        public Object getItem(int position) {
            return mOrderedTileList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final View itemView;
            final TileInfo tileInfo = mOrderedTileList.get(position);

            if (convertView == null) {
                itemView = mInflater.inflate(R.layout.order_tile_list_item, null);
                final CheckBox enabled = (CheckBox) itemView.findViewById(R.id.chkEnable);
                final CheckBox enabledLocked = (CheckBox) itemView.findViewById(R.id.chkEnableLocked);
                enabled.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TileInfo ti = (TileInfo) itemView.getTag();
                        ti.enabled = ((CheckBox)v).isChecked();
                        enabledLocked.setEnabled(ti.enabled);
                    }
                });
                enabledLocked.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TileInfo ti = (TileInfo) itemView.getTag();
                        ti.secured = !((CheckBox)v).isChecked();
                    }
                });
            } else {
                itemView = convertView;
            }

            itemView.setTag(tileInfo);
            final TextView name = (TextView) itemView.findViewById(R.id.name);
            final CheckBox enabled = (CheckBox) itemView.findViewById(R.id.chkEnable);
            final CheckBox enabledLocked = (CheckBox) itemView.findViewById(R.id.chkEnableLocked);
            name.setText(tileInfo.name);
            enabled.setChecked(tileInfo.enabled);
            enabledLocked.setChecked(!tileInfo.secured);
            enabledLocked.setEnabled(tileInfo.enabled);
            enabledLocked.setVisibility(tileInfo.key.equals("gb_tile_lock_screen") ?
                    View.INVISIBLE : View.VISIBLE);

            return itemView;
        }
    }
}
