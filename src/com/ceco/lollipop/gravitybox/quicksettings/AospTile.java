package com.ceco.lollipop.gravitybox.quicksettings;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ModQsTiles;
import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarState;
import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor.QsEventListener;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class AospTile implements QsEventListener {
    protected static String TAG = "GB:AospTile";
    protected static final boolean DEBUG = ModQsTiles.DEBUG;

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected Object mHost;
    protected Object mTile;
    protected QsTileEventDistributor mEventDistributor;
    protected XSharedPreferences mPrefs;
    protected Context mContext;
    protected boolean mEnabled;
    protected Unhook mHandleUpdateStateHook;
    protected Unhook mHandleClickHook;
    protected boolean mSecured;
    protected int mStatusBarState;
    protected boolean mNormalized;
    protected boolean mHideOnChange;
    protected int mNumColumns;
    protected float mScalingFactor = 1f;

    public static AospTile create(Object host, Object tile, String aospKey, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        if (AirplaneModeTile.AOSP_KEY.equals(aospKey))
            return new AirplaneModeTile(host, tile, prefs, eventDistributor);
        else if (BluetoothTile.AOSP_KEY.equals(aospKey))
            return new BluetoothTile(host, tile, prefs, eventDistributor);
        else if (CastTile.AOSP_KEY.equals(aospKey))
            return new CastTile(host, tile, prefs, eventDistributor);
        else if (CellularTile.AOSP_KEY.equals(aospKey))
            return new CellularTile(host, tile, prefs, eventDistributor);
        else if (ColorInversionTile.AOSP_KEY.equals(aospKey))
            return new ColorInversionTile(host, tile, prefs, eventDistributor);
        else if (FlashlightTile.AOSP_KEY.equals(aospKey))
            return new FlashlightTile(host, tile, prefs, eventDistributor);
        else if (HotspotTile.AOSP_KEY.equals(aospKey))
            return new HotspotTile(host, tile, prefs, eventDistributor);
        else if (LocationTile.AOSP_KEY.equals(aospKey))
            return new LocationTile(host, tile, prefs, eventDistributor);
        else if (RotationLockTile.AOSP_KEY.equals(aospKey))
            return new RotationLockTile(host, tile, prefs, eventDistributor);
        else if (WifiTile.AOSP_KEY.equals(aospKey))
            return new WifiTile(host, tile, prefs, eventDistributor);

        return null;
    }

    protected AospTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        mHost = host;
        mTile = tile;
        mPrefs = prefs;
        mEventDistributor = eventDistributor;

        XposedHelpers.setAdditionalInstanceField(tile, QsTile.TILE_KEY_NAME, getKey());

        createHooks();
        initPreferences();
        mEventDistributor.registerListener(this);

        if (DEBUG) log(getKey() + ": aosp tile wrapper created");
    }

    @Override
    public abstract String getKey();
    protected abstract String getClassName();
    public abstract String getAospKey();
    public abstract boolean handleLongClick(View view);

    public void initPreferences() {
        String enabledTiles = mPrefs.getString(TileOrderActivity.PREF_KEY_TILE_ORDER, null);
        mEnabled = enabledTiles != null && enabledTiles.contains(getKey());

        Set<String> securedTiles = mPrefs.getStringSet(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_SECURED_TILES,
                new HashSet<String>());
        mSecured = securedTiles.contains(getKey());

        mNormalized = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_NORMALIZE, false);
        mHideOnChange = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_HIDE_ON_CHANGE, false);
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        mEnabled = enabled;
        if (DEBUG) log(getKey() + ": onEnabledChanged(" + enabled + ")");
    }

    @Override
    public void onSecuredChanged(boolean secured) {
        mSecured = secured;
        if (DEBUG) log(getKey() + ": onSecuredChanged(" + secured + ")");
    }

    @Override
    public void onStatusBarStateChanged(int state) {
        final int oldState = mStatusBarState;
        mStatusBarState = state;
        if (mSecured && mStatusBarState != oldState &&
                mStatusBarState != StatusBarState.SHADE) {
            refreshState();
        }
        if (DEBUG) log(getKey() + ": onStatusBarStateChanged(" + state + ")");
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
    }

    protected void handleUpdateState(Object state, Object arg) {
        final boolean visible = mEnabled &&
                (!mSecured || !(mStatusBarState != StatusBarState.SHADE &&
                    mEventDistributor.isKeyguardSecured()));
        XposedHelpers.setBooleanField(state, "visible", visible);
    }

    protected void onClick() {
        if (mHideOnChange && supportsHideOnChange()) {
            collapsePanels();
        }
    }

    @Override
    public boolean supportsHideOnChange() {
        return true;
    }

    @Override
    public void onHideOnChangeChanged(boolean hideOnChange) {
        mHideOnChange = hideOnChange;
    }

    @Override
    public void handleDestroy() {
        if (DEBUG) log(getKey() + ": handleDestroy called");
        destroyHooks();
        mEventDistributor.unregisterListener(this);
        mEventDistributor = null;
        mTile = null;
        mHost = null;
        mPrefs = null;
        mContext = null;
    }

    @Override
    public void onCreateTileView(View tileView) throws Throwable {
        tileView.setLongClickable(true);
        tileView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return handleLongClick(v);
            }
        });
        XposedHelpers.setAdditionalInstanceField(tileView, QsTile.TILE_KEY_NAME, getKey());

        mScalingFactor = QsPanel.getScalingFactor(Integer.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_TILES_PER_ROW, "0")));
        if (mScalingFactor != 1f) {
            int iconSizePx = XposedHelpers.getIntField(tileView, "mIconSizePx");
            XposedHelpers.setIntField(tileView, "mIconSizePx", Math.round(iconSizePx*mScalingFactor));
            int tileSpacingPx = XposedHelpers.getIntField(tileView, "mTileSpacingPx");
            XposedHelpers.setIntField(tileView, "mTileSpacingPx", Math.round(tileSpacingPx*mScalingFactor));
            int tilePaddingBelowIconPx = XposedHelpers.getIntField(tileView, "mTilePaddingBelowIconPx");
            XposedHelpers.setIntField(tileView, "mTilePaddingBelowIconPx",
                    Math.round(tilePaddingBelowIconPx*mScalingFactor));
            int dualTileVerticalPaddingPx = XposedHelpers.getIntField(tileView, "mDualTileVerticalPaddingPx");
            XposedHelpers.setIntField(tileView, "mDualTileVerticalPaddingPx", 
                    Math.round(dualTileVerticalPaddingPx*mScalingFactor));
    
            updateLabelLayout(tileView);
            updatePaddingTop(tileView);
        }
    }

    @Override
    public void onViewConfigurationChanged(View tileView, Configuration config) {
        if (mScalingFactor != 1f) {
            updateLabelLayout(tileView);
            updatePaddingTop(tileView);
            tileView.requestLayout();
        }
    }

    @Override
    public void onRecreateLabel(View tileView) {
        if (mScalingFactor != 1f) {
            updateLabelLayout(tileView);
            tileView.requestLayout();
        }
    }

    private void updatePaddingTop(View tileView) {
        int tilePaddingTopPx = XposedHelpers.getIntField(tileView, "mTilePaddingTopPx");
        XposedHelpers.setIntField(tileView, "mTilePaddingTopPx",
                Math.round(tilePaddingTopPx*mScalingFactor));
    }

    private void updateLabelLayout(View tileView) {
        TextView label = (TextView) XposedHelpers.getObjectField(tileView, "mLabel");
        if (label != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    label.getTextSize()*mScalingFactor);
        }
        Object dualLabel = XposedHelpers.getObjectField(tileView, "mDualLabel");
        if (dualLabel != null) {
            TextView first = (TextView) XposedHelpers.getObjectField(dualLabel, "mFirstLine");
            first.setTextSize(TypedValue.COMPLEX_UNIT_PX, first.getTextSize()*mScalingFactor);
            TextView second = (TextView) XposedHelpers.getObjectField(dualLabel, "mSecondLine");
            second.setTextSize(TypedValue.COMPLEX_UNIT_PX, second.getTextSize()*mScalingFactor);
        }
    }

    private void createHooks() {
        try {
            if (DEBUG) log(getKey() + ": Creating hooks");
            mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
            ClassLoader cl = mContext.getClassLoader();

            mHandleUpdateStateHook = XposedHelpers.findAndHookMethod(
                    getClassName(), cl,"handleUpdateState",
                    QsTile.CLASS_TILE_STATE, Object.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    handleUpdateState(param.args[0], param.args[1]);
                }
            });

            mHandleClickHook = XposedHelpers.findAndHookMethod(
                    getClassName(), cl, "handleClick", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    onClick();
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mHandleUpdateStateHook != null) {
            mHandleUpdateStateHook.unhook();
            mHandleUpdateStateHook = null;
        }
        if (mHandleClickHook != null) {
            mHandleClickHook.unhook();
            mHandleClickHook = null;
        }
    }

    public void refreshState() {
        try {
            XposedHelpers.callMethod(mTile, "refreshState");
            if (DEBUG) log(getKey() + ": refreshState called");
        } catch (Throwable t) {
            log("Error refreshing tile state: ");
            XposedBridge.log(t);
        }
    }

    public void startSettingsActivity(Intent intent) {
        try {
            XposedHelpers.callMethod(mHost, "startSettingsActivity", intent);
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
}
