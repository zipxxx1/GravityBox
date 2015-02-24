package com.ceco.lollipop.gravitybox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.ceco.lollipop.gravitybox.quicksettings.QsTile;
import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModQsTiles {
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String TAG = "GB:ModQsTile";
    public static final boolean DEBUG = true;

    public static final String CLASS_TILE_HOST = "com.android.systemui.statusbar.phone.QSTileHost";

    public static final List<String> GB_TILE_KEYS = new ArrayList<String>(Arrays.asList(
            "gb_tile_nfc",
            "gb_tile_gps_slimkat",
            "gb_tile_gps_alt",
            "gb_tile_ringer_mode",
            "gb_tile_volume",
            "gb_tile_network_mode",
            "gb_tile_smart_radio",
            "gb_tile_sync",
            "gb_tile_torch",
            "gb_tile_sleep",
            "gb_tile_stay_awake",
            "gb_tile_quickrecord",
            "gb_tile_quickapp",
            "gb_tile_quickapp2",
            "gb_tile_expanded_desktop",
            "gb_tile_screenshot",
            "gb_tile_gravitybox",
            "gb_tile_usb_tether",
            "gb_tile_music",
            "gb_tile_lock_screen",
            "gb_tile_quiet_hours",
            "gb_tile_compass"
    ));

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static QsTileEventDistributor mEventDistributor;

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            if (DEBUG) log("init");

            Class<?> classTileHost = XposedHelpers.findClass(CLASS_TILE_HOST, classLoader);

            XposedHelpers.findAndHookMethod(classTileHost, "recreateTiles", new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mEventDistributor == null) {
                        mEventDistributor = new QsTileEventDistributor(param.thisObject);
                        if (DEBUG) log("Tile event distributor created");
                    }

                    Map<String, Object> tileMap = (Map<String, Object>)
                            XposedHelpers.getObjectField(param.thisObject, "mTiles");

                    // prepare GB tiles
                    for (String key : GB_TILE_KEYS) {
                        QsTile tile = QsTile.create(param.thisObject, key, prefs,
                                mEventDistributor);
                        if (tile != null) {
                            tileMap.put(key, tile.getTile());
                        }
                    }

                    Object cb = XposedHelpers.getObjectField(param.thisObject, "mCallback");
                    if (cb != null) {
                        XposedHelpers.callMethod(cb, "onTilesChanged");
                    }
                    if (DEBUG) log("Tiles created");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
