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

package com.ceco.nougat.gravitybox.quicksettings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ceco.nougat.gravitybox.GravityBoxSettings;
import com.ceco.nougat.gravitybox.ModQsTiles;
import com.ceco.nougat.gravitybox.Utils;
import com.ceco.nougat.gravitybox.managers.KeyguardStateMonitor;
import com.ceco.nougat.gravitybox.managers.SysUiManagers;
import com.ceco.nougat.gravitybox.quicksettings.QsTileEventDistributor.QsEventListener;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class BaseTile implements QsEventListener {
    protected static String TAG = "GB:BaseTile";
    protected static final boolean DEBUG = ModQsTiles.DEBUG;

    public static final String TILE_KEY_NAME = "gbTileKey";
    public static final String CLASS_BASE_TILE = "com.android.systemui.qs.QSTile";
    public static final String CLASS_TILE_STATE = "com.android.systemui.qs.QSTile.State";
    public static final String CLASS_TILE_VIEW = "com.android.systemui.qs.QSTileView";
    public static final String CLASS_TILE_VIEW_BASE = "com.android.systemui.qs.QSTileBaseView";
    public static final String CLASS_SIGNAL_TILE_VIEW = "com.android.systemui.qs.SignalTileView";
    public static final String CLASS_ICON_VIEW = "com.android.systemui.qs.QSIconView";

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    static class StockLayout {
        int tileSpacingPx;
        int tilePaddingTopPx;
        int iconSizePx;
        int tilePaddingBelowIconPx;
        float labelTextSizePx;
    }

    protected String mKey;
    protected Object mHost;
    protected Object mTile;
    protected QsTileEventDistributor mEventDistributor;
    protected XSharedPreferences mPrefs;
    protected Context mContext;
    protected Context mGbContext;
    protected boolean mProtected;
    protected boolean mHideOnChange;
    protected KeyguardStateMonitor mKgMonitor;
    private StockLayout mStockLayout;
    private View mTileView;

    public BaseTile(Object host, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        mHost = host;
        mKey = key;
        mPrefs = prefs;
        mEventDistributor = eventDistributor;
        mKgMonitor = SysUiManagers.KeyguardMonitor;

        mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
        mGbContext = Utils.getGbContext(mContext);

        mEventDistributor.registerListener(this);
        initPreferences();
        setTile(tile);
    }

    protected void initPreferences() {
        List<String> securedTiles = new ArrayList<String>(Arrays.asList(
                mPrefs.getString(TileOrderActivity.PREF_KEY_TILE_SECURED, "").split(",")));
        mProtected = securedTiles.contains(getSettingsKey());

        mHideOnChange = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_HIDE_ON_CHANGE, false);
    }

    protected final QsPanel getQsPanel() {
        return mEventDistributor.getQsPanel();
    }

    @Override
    public void handleClick() {
        if (mHideOnChange && supportsHideOnChange()) {
            collapsePanels();
        }
    }

    @Override
    public boolean handleLongClick() {
        return false;
    }

    public abstract String getSettingsKey();
    public abstract void handleUpdateState(Object state, Object arg);

    @Override
    public void setListening(boolean listening) { }

    @Override
    public String getKey() {
        return mKey;
    }

    public final void setTile(Object tile) {
        if (mTile != null) {
            XposedHelpers.removeAdditionalInstanceField(mTile, BaseTile.TILE_KEY_NAME);
        }
        mTile = tile;
        if (mTile != null) {
            XposedHelpers.setAdditionalInstanceField(mTile, BaseTile.TILE_KEY_NAME, mKey);
        }
    }

    @Override
    public Object getTile() {
        return mTile;
    }

    @Override
    public boolean handleSecondaryClick() {
        return false;
    }

    @Override
    public Object getDetailAdapter() {
        return null;
    }

    public void handleDestroy() {
        setListening(false);
        XposedHelpers.removeAdditionalInstanceField(mTile, BaseTile.TILE_KEY_NAME);
        mEventDistributor.unregisterListener(this);
        mEventDistributor = null;
        mKey = null;
        mTile = null;
        mHost = null;
        mPrefs = null;
        mContext = null;
        mGbContext = null;
        mKgMonitor = null;
        mStockLayout = null;
        mTileView = null;
    }

    @Override
    public void onCreateTileView(View tileView) {
        try {
            mTileView = tileView;
            XposedHelpers.setAdditionalInstanceField(tileView, TILE_KEY_NAME, mKey);

            // backup original dimensions
            int tilePaddingTopPx = XposedHelpers.getIntField(tileView, "mTilePaddingTopPx");
            int tileSpacingPx = XposedHelpers.getIntField(tileView, "mTileSpacingPx");
            TextView label = (TextView) XposedHelpers.getObjectField(mTileView, "mLabel");
            float labelTextSizePx = label.getTextSize();
            Field iconField = XposedHelpers.findClass(CLASS_TILE_VIEW_BASE,
                    tileView.getContext().getClassLoader()).getDeclaredField("mIcon");
            iconField.setAccessible(true);
            Object iconView = iconField.get(tileView);
            int iconSizePx = XposedHelpers.getIntField(iconView, "mIconSizePx");
            int tilePaddingBelowIconPx = XposedHelpers.getIntField(iconView, "mTilePaddingBelowIconPx");
            mStockLayout = new StockLayout();
            mStockLayout.tilePaddingTopPx = tilePaddingTopPx;
            mStockLayout.tileSpacingPx = tileSpacingPx;
            mStockLayout.labelTextSizePx = labelTextSizePx;
            mStockLayout.iconSizePx = iconSizePx;
            mStockLayout.tilePaddingBelowIconPx = tilePaddingBelowIconPx;

            updateTileViewLayout();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public View onCreateIcon() {
        return null;
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) { 
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_HIDE_ON_CHANGE)) {
                mHideOnChange = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_QS_HIDE_ON_CHANGE, false);
            }
            if (intent.hasExtra(TileOrderActivity.EXTRA_TILE_SECURED_LIST)) {
                List<String> securedTiles = new ArrayList<String>(Arrays.asList(
                        intent.getStringExtra(TileOrderActivity.EXTRA_TILE_SECURED_LIST).split(",")));
                mProtected = securedTiles.contains(getSettingsKey());
            }
        }
    }

    @Override
    public void onKeyguardStateChanged() {
        if (mProtected) {
            refreshState();
        }
    }

    @Override
    public boolean supportsHideOnChange() {
        return true;
    }

    @Override
    public void onViewConfigurationChanged(View tileView, Configuration config) {
        if (mStockLayout != null) {
            mStockLayout.tilePaddingTopPx = XposedHelpers.getIntField(tileView, "mTilePaddingTopPx");
            TextView label = (TextView) XposedHelpers.getObjectField(tileView, "mLabel");
            mStockLayout.labelTextSizePx = label.getTextSize();
            updateTileViewLayout();
        }
    }

    protected void updateTileViewLayout() {
        if (mStockLayout == null || mTileView == null) return;
        try {
            float scalingFactor = getQsPanel().getScalingFactor();
            // base
            XposedHelpers.setIntField(mTileView, "mTileSpacingPx",
                    Math.round(mStockLayout.tileSpacingPx*scalingFactor));
            XposedHelpers.setIntField(mTileView, "mTilePaddingTopPx",
                    Math.round(mStockLayout.tilePaddingTopPx*scalingFactor));

            // icon
            Field iconField = XposedHelpers.findClass(CLASS_TILE_VIEW_BASE,
                    mTileView.getContext().getClassLoader()).getDeclaredField("mIcon");
            iconField.setAccessible(true);
            Object iconView = iconField.get(mTileView);
            XposedHelpers.setIntField(iconView, "mIconSizePx",
                    Math.round(mStockLayout.iconSizePx*scalingFactor));
            XposedHelpers.setIntField(iconView, "mTilePaddingBelowIconPx",
                    Math.round(mStockLayout.tilePaddingBelowIconPx*scalingFactor));

            // label
            TextView label = (TextView) XposedHelpers.getObjectField(mTileView, "mLabel");
            if (label != null) {
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        mStockLayout.labelTextSizePx*scalingFactor);
            }

            mTileView.requestLayout();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public void refreshState() {
        try {
            XposedHelpers.callMethod(mTile, "refreshState");
            if (DEBUG) log(mKey + ": refreshState called");
        } catch (Throwable t) {
            log("Error refreshing tile state: ");
            XposedBridge.log(t);
        }
    }

    public void startSettingsActivity(Intent intent) {
        try {
            XposedHelpers.callMethod(mHost, "startActivityDismissingKeyguard", intent);
        } catch (Throwable t) {
            log("Error in startSettingsActivity: ");
            XposedBridge.log(t);
        }
    }

    public void startSettingsActivity(String action) {
        startSettingsActivity(new Intent(action));
    }

    public void collapsePanels() {
        try {
            XposedHelpers.callMethod(mHost, "collapsePanels");
        } catch (Throwable t) {
            log("Error in collapsePanels: ");
            XposedBridge.log(t);
        }
    }

    public void showDetail(boolean show) {
        try {
            XposedHelpers.callMethod(mTile, "showDetail", show);
        } catch (Throwable t) {
            log("Error in showDetail: ");
            XposedBridge.log(t);
        }
    }

    public void fireToggleStateChanged(boolean state) {
        try {
            XposedHelpers.callMethod(mTile, "fireToggleStateChanged", state);
        } catch (Throwable t) {
            log("Error in fireToggleStateChanged: ");
            XposedBridge.log(t);
        }
    }
}
