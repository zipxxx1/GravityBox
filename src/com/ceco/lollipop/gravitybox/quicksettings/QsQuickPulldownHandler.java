package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.BroadcastSubReceiver;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.MotionEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class QsQuickPulldownHandler implements BroadcastSubReceiver {
    private static final String TAG = "GB:QsQuickPulldownHandler";
    private static final boolean DEBUG = false;

    private static final int MODE_OFF = 0;
    private static final int MODE_RIGHT = 1;
    private static final int MODE_LEFT = 2;
    
    private static final String CLASS_NOTIF_PANEL = 
            "com.android.systemui.statusbar.phone.NotificationPanelView";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Context mContext;
    private XSharedPreferences mPrefs;
    private int mMode;
    private int mSizePercent;

    public QsQuickPulldownHandler(Context context, XSharedPreferences prefs, 
            QsTileEventDistributor eventDistributor) {
        mContext = context;
        mPrefs = prefs;
        eventDistributor.registerBroadcastSubReceiver(this);

        initPreferences();
        createHooks();
        if (DEBUG) log("Quick pulldown handler created");
    }

    private void initPreferences() {
        mMode = Integer.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_QUICK_PULLDOWN, "0"));
        mSizePercent = mPrefs.getInt(GravityBoxSettings.PREF_KEY_QUICK_PULLDOWN_SIZE, 15);
        if (DEBUG) log("initPreferences: mode=" + mMode + "; size%=" + mSizePercent);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICK_PULLDOWN)) {
                mMode = intent.getIntExtra(GravityBoxSettings.EXTRA_QUICK_PULLDOWN, MODE_OFF);
                if (DEBUG) log("onBroadcastReceived: mode=" + mMode);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QUICK_PULLDOWN_SIZE)) {
                mSizePercent = intent.getIntExtra(GravityBoxSettings.EXTRA_QUICK_PULLDOWN_SIZE, 15);
                if (DEBUG) log("onBroadcastReceived: size%=" + mSizePercent);
            }
        }
    }

    private String getQsExpandFieldName() {
        switch (Build.VERSION.SDK_INT) {
            case 21: return "mTwoFingerQsExpand";
            default: return "mQsExpandImmediate";
        }
    }

    private void createHooks() {
        try {
            ClassLoader cl = mContext.getClassLoader();

            final String qsExpandFieldName = getQsExpandFieldName();

            XposedHelpers.findAndHookMethod(CLASS_NOTIF_PANEL, cl,
                    "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final Object o = param.thisObject;
                    if (mMode == MODE_OFF ||
                        XposedHelpers.getBooleanField(o, "mBlockTouches") ||
                        XposedHelpers.getBooleanField(o, "mOnlyAffordanceInThisMotion") ||
                        XposedHelpers.getBooleanField(o, qsExpandFieldName) ||
                        (!XposedHelpers.getBooleanField(o, qsExpandFieldName) && 
                                XposedHelpers.getBooleanField(o, "mQsTracking") &&
                                !XposedHelpers.getBooleanField(o, "mConflictingQsExpansionGesture"))) {
                        return;
                    }

                    final MotionEvent event = (MotionEvent) param.args[0];
                    boolean oneFingerQsOverride = event.getActionMasked() == MotionEvent.ACTION_DOWN
                            && shouldQuickSettingsIntercept(o, event.getX(), event.getY(), -1)
                            && event.getY(event.getActionIndex()) < 
                                XposedHelpers.getIntField(o, "mStatusBarMinHeight");
                    if (oneFingerQsOverride) {
                        XposedHelpers.setBooleanField(o, qsExpandFieldName, true);
                        XposedHelpers.callMethod(o, "requestPanelHeightUpdate");
                        XposedHelpers.callMethod(o, "setListening", true);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean shouldQuickSettingsIntercept(Object o, float x, float y, float yDiff) {
        if (!XposedHelpers.getBooleanField(o, "mQsExpansionEnabled")) {
            return false;
        }

        final int w = (int) XposedHelpers.callMethod(o, "getMeasuredWidth");
        float region = (w * (mSizePercent/100f));
        final boolean showQsOverride = mMode == MODE_RIGHT ? 
                (x > w - region) : (x < region);

        if (XposedHelpers.getBooleanField(o, "mQsExpanded")) {
            Object scv = XposedHelpers.getObjectField(o, "mScrollView");
            return ((boolean)XposedHelpers.callMethod(scv, "isScrolledToBottom") && yDiff < 0) && 
                    (boolean)XposedHelpers.callMethod(o, "isInQsArea", x, y);
        } else {
            return showQsOverride;
        }
    }
}
