package com.ceco.lollipop.gravitybox.quicksettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ModQsTiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsTileEventDistributor {
    private static final String TAG = "GB:QsTileEventDistributor";
    private static final boolean DEBUG = ModQsTiles.DEBUG;

    public interface QsEventListener {
        String getKey();
        void handleUpdateState(Object state, Object arg);
        boolean supportsDualTargets();
        void handleClick();
        void handleSecondaryClick();
        void setListening(boolean listening);
        Object createTileView() throws Throwable;
        void handleDestroy();
        void onBrodcastReceived(Context context, Intent intent);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Object mHost;
    private Context mContext;
    private Map<String,QsEventListener> mListeners;

    public QsTileEventDistributor(Object host) {
        mHost = host;
        mListeners = new LinkedHashMap<String,QsEventListener>();

        createHooks();
        prepareBroadcastReceiver();
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                for (Entry<String,QsEventListener> l : mListeners.entrySet()) {
                    l.getValue().onBrodcastReceived(context, intent);
                }
            } catch (Throwable t) {
                log("Error notifying listeners of new broadcast: ");
                XposedBridge.log(t);
            }
        }
    };

    private void prepareBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private void createHooks() {
        try {
            if (DEBUG) log("Creating hooks");
            mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
            ClassLoader cl = mContext.getClassLoader();

            XposedHelpers.findAndHookMethod(QsTile.CLASS_INTENT_TILE, cl, "handleUpdateState",
                    QsTile.CLASS_TILE_STATE, Object.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleUpdateState(param.args[0], param.args[1]);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "supportsDualTargets",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        param.setResult(l.supportsDualTargets());
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_INTENT_TILE, cl, "handleClick",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleClick();
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "handleSecondaryClick",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleSecondaryClick();
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_INTENT_TILE, cl, "setListening",
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.setListening((boolean)param.args[0]);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "createTileView",
                    Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        param.setResult(l.createTileView());
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_INTENT_TILE, cl, "handleDestroy",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, QsTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleDestroy();
                    }
                }
            });
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
}
