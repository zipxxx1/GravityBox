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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.ceco.nougat.gravitybox.quicksettings.AospTile;
import com.ceco.nougat.gravitybox.quicksettings.BaseTile;
import com.ceco.nougat.gravitybox.quicksettings.QsPanel;
import com.ceco.nougat.gravitybox.quicksettings.QsPanelQuick;
import com.ceco.nougat.gravitybox.quicksettings.QsQuickPulldownHandler;
import com.ceco.nougat.gravitybox.quicksettings.QsTile;
import com.ceco.nougat.gravitybox.quicksettings.QsTileEventDistributor;
import com.ceco.nougat.gravitybox.quicksettings.QsTileEventDistributor.OnTileDestroyedListener;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModQsTiles {
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String TAG = "GB:ModQsTile";
    public static final boolean DEBUG = false;

    public static final String CLASS_TILE_HOST = Utils.isXperiaDevice() ?
            "com.sonymobile.systemui.qs.SomcQSTileHost" :
            "com.android.systemui.statusbar.phone.QSTileHost";
    public static final String TILES_SETTING = "sysui_qs_tiles";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static QsTileEventDistributor mEventDistributor;
    private static QsQuickPulldownHandler mQuickPulldownHandler;
    private static QsPanel mQsPanel;
    private static QsPanelQuick mQsPanelQuick;
    private static Map<String, BaseTile> mTiles = new HashMap<>();

    private static OnTileDestroyedListener mOnTileDestroyedListener = new OnTileDestroyedListener() {
        @Override
        public void onTileDestroyed(String key) {
            if (mTiles.containsKey(key)) {
                if (DEBUG) log("Removing wrapper from: " + key);
                mTiles.remove(key);
            }
        }
    };

    public static void initResources(final InitPackageResourcesParam resparam) {
        if (Utils.isXperiaDevice()) {
            resparam.res.setReplacement(PACKAGE_NAME, "integer", "config_maxToolItems", 60);
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            if (DEBUG) log("init");

            Class<?> classTileHost = XposedHelpers.findClass(CLASS_TILE_HOST, classLoader);
            mQsPanel = new QsPanel(prefs, classLoader);
            mQsPanelQuick = new QsPanelQuick(prefs, classLoader);

            XposedHelpers.findAndHookMethod(classTileHost, "onTuningChanged",
                    String.class, String.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!TILES_SETTING.equals(param.args[0])) return;

                    if (mEventDistributor == null) {
                        mEventDistributor = new QsTileEventDistributor(param.thisObject, prefs);
                        mEventDistributor.setOnTileDestroyedListener(mOnTileDestroyedListener);
                        mQsPanel.setEventDistributor(mEventDistributor);
                        if (DEBUG) log("Tile event distributor created");
                    }

                    Map<String, Object> tileMap = (Map<String, Object>)
                            XposedHelpers.getObjectField(param.thisObject, "mTiles");
                    Context context = (Context) XposedHelpers.callMethod(param.thisObject, "getContext");

                    // prepare tile wrappers
                    for (Entry<String,Object> entry : tileMap.entrySet()) {
                        if (mTiles.containsKey(entry.getKey())) {
                            mTiles.get(entry.getKey()).setTile(entry.getValue());
                            if (DEBUG) log("Updated tile reference for: " + entry.getKey());
                            continue;
                        }
                        if (entry.getKey().contains(GravityBox.PACKAGE_NAME)) {
                            if (DEBUG) log("Creating wrapper for custom tile: " + entry.getKey());
                            QsTile gbTile = QsTile.create(param.thisObject, entry.getKey(), entry.getValue(),
                                prefs, mEventDistributor);
                            if (gbTile != null) {
                                mTiles.put(entry.getKey(), gbTile);
                            }
                        } else {
                            if (DEBUG) log("Creating wrapper for AOSP tile: " + entry.getKey());
                            AospTile aospTile = AospTile.create(param.thisObject, entry.getValue(), 
                                    entry.getKey(), prefs, mEventDistributor);
                            mTiles.put(aospTile.getKey(), aospTile);
                        }
                    }
                    if (DEBUG) log("Tile wrappers created");

                    if (mQuickPulldownHandler == null) {
                        mQuickPulldownHandler = new QsQuickPulldownHandler(context, prefs, mEventDistributor);
                    }
                    mQsPanel.updateResources();
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
