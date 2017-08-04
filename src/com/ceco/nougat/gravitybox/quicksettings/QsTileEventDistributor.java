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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ceco.nougat.gravitybox.BroadcastSubReceiver;
import com.ceco.nougat.gravitybox.GravityBoxSettings;
import com.ceco.nougat.gravitybox.ModQsTiles;
import com.ceco.nougat.gravitybox.PhoneWrapper;
import com.ceco.nougat.gravitybox.Utils;
import com.ceco.nougat.gravitybox.managers.KeyguardStateMonitor;
import com.ceco.nougat.gravitybox.managers.SysUiManagers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsTileEventDistributor implements KeyguardStateMonitor.Listener {
    private static final String TAG = "GB:QsTileEventDistributor";
    private static final boolean DEBUG = ModQsTiles.DEBUG;

    public interface QsEventListener {
        String getKey();
        Object getTile();
        void handleDestroy();
        void onCreateTileView(View tileView) throws Throwable;
        void onBroadcastReceived(Context context, Intent intent);
        void onKeyguardStateChanged();
        boolean supportsHideOnChange();
        void onViewConfigurationChanged(View tileView, Configuration config);
        void onRecreateLabel(View tileView);
        void handleClick();
        boolean handleLongClick();
        void handleUpdateState(Object state, Object arg);
        void setListening(boolean listening);
        View onCreateIcon();
        boolean handleSecondaryClick();
        Object getDetailAdapter();
    }

    public interface OnTileDestroyedListener {
        void onTileDestroyed(String key);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Object mHost;
    private Context mContext;
    private XSharedPreferences mPrefs;
    private Map<String,QsEventListener> mListeners;
    private List<BroadcastSubReceiver> mBroadcastSubReceivers;
    private String mCreateTileViewTileKey;
    private OnTileDestroyedListener mOnTileDestroyedListener;

    public QsTileEventDistributor(Object host, XSharedPreferences prefs) {
        mHost = host;
        mPrefs = prefs;
        mListeners = new LinkedHashMap<String,QsEventListener>();
        mBroadcastSubReceivers = new ArrayList<BroadcastSubReceiver>();
        SysUiManagers.KeyguardMonitor.registerListener(this);

        createHooks();
        prepareBroadcastReceiver();
    }

    public void setOnTileDestroyedListener(OnTileDestroyedListener listener) {
        mOnTileDestroyedListener = listener;
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
                if (intent.hasExtra(TileOrderActivity.EXTRA_QS_ORDER_CHANGED) ||
                        intent.hasExtra(GravityBoxSettings.EXTRA_QS_COLS) ||
                        intent.hasExtra(GravityBoxSettings.EXTRA_QS_SCALE_CORRECTION)) {
                    recreateTiles();
                } else {
                    notifyTilesOfBroadcast(context, intent);
                }
                for (BroadcastSubReceiver receiver : mBroadcastSubReceivers) {
                    receiver.onBroadcastReceived(context, intent);
                }
            } else {
                notifyTilesOfBroadcast(context, intent);
            }
        }
    };

    private void prepareBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_EXPANDED_DESKTOP_MODE_CHANGED);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QUICKAPP_CHANGED);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QUICKAPP_CHANGED_2);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QUICKAPP_CHANGED_3);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QUICKAPP_CHANGED_4);
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QS_NETWORK_MODE_SIM_SLOT_CHANGED);
        intentFilter.addAction(PhoneWrapper.ACTION_NETWORK_TYPE_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private void notifyTilesOfBroadcast(Context context, Intent intent) {
        try {
            for (Entry<String,QsEventListener> l : mListeners.entrySet()) {
                l.getValue().onBroadcastReceived(context, intent);
            }
        } catch (Throwable t) {
            log("Error notifying listeners of new broadcast: ");
            XposedBridge.log(t);
        }        
    }

    private void recreateTiles() {
        try {
            mPrefs.reload();
            if (Utils.isXperiaDevice()) {
                XposedHelpers.setObjectField(mHost, "mQuickSettingsTilesDefault", "");
                XposedHelpers.callMethod(mHost, "onConfigurationChanged");
            } else {
                XposedHelpers.callMethod(mHost, "onTuningChanged",
                        ModQsTiles.TILES_SETTING, (String)null);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void createHooks() {
        try {
            if (DEBUG) log("Creating hooks");
            mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
            final ClassLoader cl = mContext.getClassLoader();

            XposedHelpers.findAndHookMethod(QsTile.CLASS_CUSTOM_TILE, cl, "handleUpdateState",
                    BaseTile.CLASS_TILE_STATE, Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l instanceof QsTile) {
                        l.handleUpdateState(param.args[0], param.args[1]);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_CUSTOM_TILE, cl, "handleClick",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l instanceof QsTile) {
                        l.handleClick();
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_CUSTOM_TILE, cl, "setListening",
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l instanceof QsTile) {
                        l.setListening((boolean)param.args[0]);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsPanel.CLASS_QS_PANEL, cl, "createTileView",
                    BaseTile.CLASS_BASE_TILE, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    mCreateTileViewTileKey = (String) XposedHelpers
                            .getAdditionalInstanceField(param.args[0], BaseTile.TILE_KEY_NAME);
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.args[0], BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.onCreateTileView((View)param.getResult());
                    }
                    mCreateTileViewTileKey = null;
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "getDetailAdapter",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        Object detailAdapter = l.getDetailAdapter();
                        if (detailAdapter != null) {
                            param.setResult(detailAdapter);
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(BaseTile.CLASS_BASE_TILE, cl, "handleDestroy",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleDestroy();
                        if (mOnTileDestroyedListener != null) {
                            mOnTileDestroyedListener.onTileDestroyed(l.getKey());
                        }
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "handleSecondaryClick",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null && l.handleSecondaryClick()) {
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(BaseTile.CLASS_TILE_VIEW, cl, "onConfigurationChanged",
                    Configuration.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.onViewConfigurationChanged((View)param.thisObject,
                                (Configuration)param.args[0]);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(BaseTile.CLASS_TILE_VIEW, cl, "createLabel",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.onRecreateLabel((View)param.thisObject);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(BaseTile.CLASS_ICON_VIEW, cl, "createIcon",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(mCreateTileViewTileKey);
                    if (l != null) {
                        View icon = l.onCreateIcon();
                        if (icon != null) {
                            param.setResult(icon);
                        }
                    }
                }
            });

            XC_MethodHook longClickHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null && l.handleLongClick()) {
                        param.setResult(null);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(BaseTile.CLASS_BASE_TILE, cl,
                        "handleLongClick", longClickHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public synchronized void registerListener(QsEventListener listener) {
        if (listener == null) 
            throw new IllegalArgumentException("registerListener: Listener cannot be null");

        final String key = listener.getKey();
        if (!mListeners.containsKey(key)) {
            mListeners.put(key, listener);
        }
    }

    public synchronized void unregisterListener(QsEventListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("unregisterListener: Listener cannot be null");

        final String key = listener.getKey();
        if (mListeners.containsKey(key)) {
            mListeners.remove(key);
        }
    }

    public synchronized void registerBroadcastSubReceiver(BroadcastSubReceiver receiver) {
        if (receiver == null) 
            throw new IllegalArgumentException("registerBroadcastSubReceiver: receiver cannot be null");

        if (!mBroadcastSubReceivers.contains(receiver)) {
            mBroadcastSubReceivers.add(receiver);
        }
    }

    public synchronized void unregisterBroadcastSubReceiver(BroadcastSubReceiver receiver) {
        if (receiver == null)
            throw new IllegalArgumentException("unregisterBroadcastSubReceiver: receiver cannot be null");

        if (mBroadcastSubReceivers.contains(receiver)) {
            mBroadcastSubReceivers.remove(receiver);
        }
    }

    @Override
    public void onKeyguardStateChanged() {
        for (Entry<String,QsEventListener> entry : mListeners.entrySet()) {
            entry.getValue().onKeyguardStateChanged();
        }
    }
}
