/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.kitkat.gravitybox;

import android.app.ListActivity;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WifiPriorityActivity extends ListActivity {

    private final TouchInterceptor.DropListener mDropListener =
            new TouchInterceptor.DropListener() {
        public void drop(int from, int to) {
            if (from == to) return;

            // Sort networks by user selection
            List<WifiConfiguration> mNetworks = mAdapter.getNetworks();
            WifiConfiguration o = mNetworks.remove(from);
            mNetworks.add(to, o);

            // Set the new priorities of the networks
            int cc = mNetworks.size();
            for (int i = 0; i < cc; i++) {
                WifiConfiguration network = mNetworks.get(i);
                network.priority = cc - i;

                // Update the priority
                mWifiManager.updateNetwork(network);
            }

            // Now, save all the Wi-Fi configuration with its new priorities
            mWifiManager.saveConfiguration();

            // Reload the networks
            mAdapter.reloadNetworks();
            mNetworksListView.invalidateViews();
        }
    };

    private WifiManager mWifiManager;
    private TouchInterceptor mNetworksListView;
    private WifiPriorityAdapter mAdapter;
    private int mTextAppearanceResId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        mTextAppearanceResId = android.R.style.TextAppearance_Holo_Medium_Inverse;
        if (file.exists()) {
            this.setTheme(android.R.style.Theme_Holo);
            mTextAppearanceResId = android.R.style.TextAppearance_Holo_Medium;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_network_priority);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        // Set the touchable listview
        mNetworksListView = (TouchInterceptor)getListView();
        mNetworksListView.setDropListener(mDropListener);
        mAdapter = new WifiPriorityAdapter(this, mWifiManager);
        setListAdapter(mAdapter);
    }

    @Override
    public void onDestroy() {
        mNetworksListView.setDropListener(null);
        setListAdapter(null);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Reload the networks
        mAdapter.reloadNetworks();
        mNetworksListView.invalidateViews();
    }

    private class WifiPriorityAdapter extends BaseAdapter {

        private final WifiManager mWifiManager;
        private final LayoutInflater mInflater;
        private List<WifiConfiguration> mNetworks;

        public WifiPriorityAdapter(Context ctx, WifiManager wifiManager) {
            mWifiManager = wifiManager;
            mInflater = LayoutInflater.from(ctx);
            reloadNetworks();
        }

        private void reloadNetworks() {
            mNetworks = mWifiManager.getConfiguredNetworks();
            if (mNetworks == null) {
                mNetworks = new ArrayList<WifiConfiguration>();
            }

            // Sort network list by priority (or by network id if the priority is the same)
            Collections.sort(mNetworks, new Comparator<WifiConfiguration>() {
                @Override
                public int compare(WifiConfiguration lhs, WifiConfiguration rhs) {
                    // > priority -- > lower position
                    if (lhs.priority < rhs.priority) return 1;
                    if (lhs.priority > rhs.priority) return -1;
                    // < network id -- > lower position
                    if (lhs.networkId < rhs.networkId) return -1;
                    if (lhs.networkId > rhs.networkId) return 1;
                    return 0;
                }
            });
        }

        List<WifiConfiguration> getNetworks() {
            return mNetworks;
        }

        @Override
        public int getCount() {
            return mNetworks.size();
        }

        @Override
        public Object getItem(int position) {
            return mNetworks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View v;
            if (convertView == null) {
                v = mInflater.inflate(R.layout.wifi_network_priority_list_item, null);
            } else {
                v = convertView;
            }

            WifiConfiguration network = (WifiConfiguration)getItem(position);

            final TextView name = (TextView) v.findViewById(R.id.name);
            name.setTextAppearance(WifiPriorityActivity.this.getApplicationContext(), mTextAppearanceResId);
            // wpa_suplicant returns the SSID between double quotes. Remove them if are present.
            name.setText(filterSSID(network.SSID));

            return v;
        }

        private String filterSSID(String ssid) {
            // Filter only if has start and end double quotes
            if (ssid == null || !ssid.startsWith("\"") || !ssid.endsWith("\"")) {
                return ssid;
            }
            return ssid.substring(1, ssid.length()-1);
        }
    }
}
