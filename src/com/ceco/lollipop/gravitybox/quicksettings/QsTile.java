package com.ceco.lollipop.gravitybox.quicksettings;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.ceco.lollipop.gravitybox.GravityBox;
import com.ceco.lollipop.gravitybox.ModQsTiles;
import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor.QsEventListener;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class QsTile implements QsEventListener {
    protected static String TAG = "GB:QsTile";

    protected static final boolean DEBUG = ModQsTiles.DEBUG;
    public static final String TILE_KEY_NAME = "gbTileKey";
    public static final String DUMMY_INTENT = "intent(dummy)";
    public static final String CLASS_INTENT_TILE = "com.android.systemui.qs.tiles.IntentTile";
    public static final String CLASS_TILE_STATE = "com.android.systemui.qs.QSTile.State";

    protected Object mHost;
    protected Object mTile;
    protected String mKey;
    protected State mState;
    protected Context mContext;
    protected Context mGbContext;

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public QsTile(Object host, String key) throws Throwable {
        mHost = host;
        mKey = key;
        mState = new State();

        mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
        mGbContext = mContext.createPackageContext(GravityBox.PACKAGE_NAME,
                Context.CONTEXT_IGNORE_SECURITY);

        mTile = XposedHelpers.callStaticMethod(XposedHelpers.findClass(
                CLASS_INTENT_TILE, mContext.getClassLoader()),
                "create", mHost, DUMMY_INTENT);
        XposedHelpers.setAdditionalInstanceField(mTile, TILE_KEY_NAME, key);
    }

    public Object getTile() {
        return mTile;
    }

    @Override
    public String getKey() {
        return mKey;
    }

    public static class State {
        public boolean visible;
        public Drawable icon;
        public String label;
        public boolean autoMirrorDrawable = true;

        public void applyTo(Object state) {
            XposedHelpers.setBooleanField(state, "visible", visible);
            XposedHelpers.setObjectField(state, "icon", icon);
            XposedHelpers.setObjectField(state, "label", label);
            XposedHelpers.setBooleanField(state, "autoMirrorDrawable", autoMirrorDrawable);
        }
    }
}
