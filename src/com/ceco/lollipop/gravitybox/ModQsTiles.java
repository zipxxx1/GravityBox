package com.ceco.lollipop.gravitybox;

import java.util.Map;

import com.ceco.lollipop.gravitybox.quicksettings.QsTile;
import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor;
import com.ceco.lollipop.gravitybox.quicksettings.TestTile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModQsTiles {
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String TAG = "GB:ModQsTile";
    public static final boolean DEBUG = true;

    public static final String CLASS_TILE_HOST = "com.android.systemui.statusbar.phone.QSTileHost";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static QsTileEventDistributor mEventDistributor;

    public static void init(final ClassLoader classLoader) {
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

                    QsTile tile = new TestTile(param.thisObject, "gb_test_tile");
                    tileMap.put(tile.getKey(), tile.getTile());
                    mEventDistributor.registerListener(tile);

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
