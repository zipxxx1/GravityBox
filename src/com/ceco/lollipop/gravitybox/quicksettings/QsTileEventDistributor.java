package com.ceco.lollipop.gravitybox.quicksettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ceco.lollipop.gravitybox.BroadcastSubReceiver;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ModQsTiles;

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

public class QsTileEventDistributor {
    private static final String TAG = "GB:QsTileEventDistributor";
    private static final boolean DEBUG = ModQsTiles.DEBUG;

    private static final String CLASS_KG_TOUCH_DELEGATE = 
            "com.android.systemui.statusbar.phone.KeyguardTouchDelegate";
    private static final String CLASS_STATUSBAR_WM = 
            "com.android.systemui.statusbar.phone.StatusBarWindowManager";
    private static final String CLASS_TILE_VIEW = 
            "com.android.systemui.qs.QSTileView";

    public interface QsEventListener {
        String getKey();
        Object getTile();
        void handleDestroy();
        void onCreateTileView(View tileView) throws Throwable;
        void onBroadcastReceived(Context context, Intent intent);
        void onStatusBarStateChanged(int state);
        boolean supportsHideOnChange();
        void onViewConfigurationChanged(View tileView, Configuration config);
        void onRecreateLabel(View tileView);
        void handleClick();
        void handleUpdateState(Object state, Object arg);
        void setListening(boolean listening);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Object mHost;
    private Context mContext;
    private XSharedPreferences mPrefs;
    private Map<String,QsEventListener> mListeners;
    private List<BroadcastSubReceiver> mBroadcastSubReceivers;
    private Object mKeyguardDelegate;

    public QsTileEventDistributor(Object host, XSharedPreferences prefs) {
        mHost = host;
        mPrefs = prefs;
        mListeners = new LinkedHashMap<String,QsEventListener>();
        mBroadcastSubReceivers = new ArrayList<BroadcastSubReceiver>();

        createHooks();
        prepareBroadcastReceiver();
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
                if (intent.hasExtra(TileOrderActivity.EXTRA_QS_ORDER_CHANGED) ||
                        intent.hasExtra(GravityBoxSettings.EXTRA_QS_NORMALIZED) ||
                        intent.hasExtra(GravityBoxSettings.EXTRA_QS_COLS)) {
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
            XposedHelpers.callMethod(mHost, "recreateTiles");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
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
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleUpdateState(param.args[0], param.args[1]);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_INTENT_TILE, cl, "handleClick",
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleClick();
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_INTENT_TILE, cl, "setListening",
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.setListening((boolean)param.args[0]);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "createTileView",
                    Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.onCreateTileView((View)param.getResult());
                    }
                }
            });

            XposedHelpers.findAndHookMethod(QsTile.CLASS_BASE_TILE, cl, "handleDestroy",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final QsEventListener l = mListeners.get(XposedHelpers
                            .getAdditionalInstanceField(param.thisObject, BaseTile.TILE_KEY_NAME));
                    if (l != null) {
                        l.handleDestroy();
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_STATUSBAR_WM, cl, "setStatusBarState",
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int newState = (int) param.args[0];
                    for (Entry<String,QsEventListener> entry : mListeners.entrySet()) {
                        entry.getValue().onStatusBarStateChanged(newState);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_TILE_VIEW, cl, "onConfigurationChanged",
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

            XposedHelpers.findAndHookMethod(CLASS_TILE_VIEW, cl, "recreateLabel",
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

    private Object getKeyguardDelegate() {
        if (mKeyguardDelegate != null) return mKeyguardDelegate;
        try {
            mKeyguardDelegate = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass(CLASS_KG_TOUCH_DELEGATE, mContext.getClassLoader()),
                    "getInstance", mContext);
            return mKeyguardDelegate;
        } catch (Throwable t) {
            log("Error getting Keyguard delegate: " + t.getMessage());
            return null;
        }
    }

    protected final boolean isKeyguardShowing() {
        try {
            return (boolean) XposedHelpers.callMethod(getKeyguardDelegate(), "isShowingAndNotOccluded");
        } catch (Throwable t) {
            log("Error in isKeyguardShowing: " + t.getMessage());
            return false;
        }
    }

    protected final boolean isKeyguardSecured() {
        try {
            return (boolean) XposedHelpers.callMethod(getKeyguardDelegate(), "isSecure");
        } catch (Throwable t) {
            log("Error in isKeyguardSecured: " + t.getMessage());
            return false;
        }
    }

    protected final boolean isKeyguardShowingAndSecured() {
        return (isKeyguardShowing() && isKeyguardSecured());
    }
}
