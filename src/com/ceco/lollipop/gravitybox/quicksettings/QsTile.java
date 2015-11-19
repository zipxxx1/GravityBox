package com.ceco.lollipop.gravitybox.quicksettings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.Utils;
import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarState;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class QsTile extends BaseTile {
    public static final String DUMMY_INTENT = "intent(dummy)";
    public static final String CLASS_INTENT_TILE = "com.android.systemui.qs.tiles.IntentTile";

    private static Class<?> sResourceIconClass;

    protected State mState;

    public static QsTile create(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {

        Context ctx = (Context) XposedHelpers.callMethod(host, "getContext");

        if (key.equals("gb_tile_gravitybox"))
            return new GravityBoxTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_network_mode") && !Utils.isWifiOnly(ctx))
            return new NetworkModeTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_expanded_desktop"))
            return new ExpandedDesktopTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_gps_alt") && Utils.hasGPS(ctx))
            return new GpsTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_gps_slimkat") && Utils.hasGPS(ctx))
            return new LocationTileSlimkat(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_lock_screen"))
            return new LockScreenTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_music"))
            return new MusicTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_nfc") && Utils.hasNfc(ctx))
            return new NfcTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_quickapp"))
            return new QuickAppTile(host, key, prefs, eventDistributor, 1);
        else if (key.equals("gb_tile_quickapp2"))
            return new QuickAppTile(host, key, prefs, eventDistributor, 2);
        else if (key.equals("gb_tile_quickrecord"))
            return new QuickRecordTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_quiet_hours") &&
                SysUiManagers.QuietHoursManager != null)
            return new QuietHoursTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_ringer_mode"))
            return new RingerModeTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_screenshot"))
            return new ScreenshotTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_sleep"))
            return new SleepTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_smart_radio") && prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false))
            return new SmartRadioTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_stay_awake"))
            return new StayAwakeTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_sync"))
            return new SyncTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_torch") && Utils.hasFlash(ctx))
            return new TorchTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_volume"))
            return new VolumeTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_compass") && Utils.hasCompass(ctx))
            return new CompassTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_usb_tether"))
            return new UsbTetherTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_battery"))
            return new BatteryTile(host, key, prefs, eventDistributor);

        return null;
    }

    protected QsTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mState = new State(mKey, mEventDistributor.isResourceIconHooked());

        mTile = XposedHelpers.callStaticMethod(XposedHelpers.findClass(
                CLASS_INTENT_TILE, mContext.getClassLoader()),
                "create", mHost, DUMMY_INTENT);
        XposedHelpers.setAdditionalInstanceField(mTile, TILE_KEY_NAME, mKey);

        if (Build.VERSION.SDK_INT >= 22 && sResourceIconClass == null) {
            sResourceIconClass = getResourceIconClass(mContext.getClassLoader());
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible &= mEnabled &&
                (!mLocked || !mEventDistributor.isKeyguardShowing()) &&
                (!mLockedOnly || mEventDistributor.isKeyguardShowing()) &&
                (!mSecured || !mEventDistributor.isKeyguardShowingAndLocked());
        mState.applyTo(state);
    }

    @Override
    public Drawable getResourceIconDrawable() {
        return mState.icon;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        mState = null;
        if (DEBUG) log(mKey + ": handleDestroy called");
    }

    private static Class<?> getResourceIconClass(ClassLoader cl) {
        try {
            return XposedHelpers.findClass(CLASS_BASE_TILE+".ResourceIcon", cl);
        } catch (Throwable t) {
            log("Error getting resource icon class:");
            XposedBridge.log(t);
            return null;
        }
    }

    public static class State {
        public boolean visible;
        public Drawable icon;
        public String label;
        public boolean autoMirrorDrawable = true;

        private String mKey;
        private boolean mAllowCustomResourceIcon;

        public State(String key, boolean resourceIconHooked) {
            mKey = key;
            mAllowCustomResourceIcon = resourceIconHooked;
        }

        public void applyTo(Object state) {
            XposedHelpers.setBooleanField(state, "visible", visible);
            XposedHelpers.setObjectField(state, "icon",
                    Build.VERSION.SDK_INT >= 22 ? getResourceIcon() : icon);
            XposedHelpers.setObjectField(state, "label", label);
            XposedHelpers.setBooleanField(state, "autoMirrorDrawable", autoMirrorDrawable);
        }

        private Object getResourceIcon() {
            if (sResourceIconClass == null || icon == null ||
                    !mAllowCustomResourceIcon) return null;

            try {
                Object resourceIcon = XposedHelpers.callStaticMethod(
                        sResourceIconClass, "get", icon.hashCode());
                XposedHelpers.setAdditionalInstanceField(resourceIcon, TILE_KEY_NAME, mKey);
                if (DEBUG) log("getting resource icon for " + mKey);
                return resourceIcon;
            } catch (Throwable t) {
                log("Error creating resource icon:");
                XposedBridge.log(t);
                return null;
            }
        }
    }
}
