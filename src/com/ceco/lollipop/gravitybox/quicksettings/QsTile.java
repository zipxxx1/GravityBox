package com.ceco.lollipop.gravitybox.quicksettings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

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
    public static final String CLASS_BASE_TILE = "com.android.systemui.qs.QSTile";
    public static final String CLASS_TILE_STATE = "com.android.systemui.qs.QSTile.State";
    public static final String CLASS_TILE_VIEW = "com.android.systemui.qs.QSTileView";

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

    public abstract void handleUpdateState(Object state, Object arg);
    public abstract void handleClick();
    public abstract boolean handleLongClick(View view);

    public Object getTile() {
        return mTile;
    }

    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public boolean supportsDualTargets() {
        return false;
    }

    @Override
    public void handleSecondaryClick() {
        // optional
    }

    @Override
    public void setListening(boolean listening) {
        // optional
    }

    @Override
    public Object createTileView() throws Throwable {
        View tileView = (View) XposedHelpers.findConstructorExact(CLASS_TILE_VIEW,
                mContext.getClassLoader(), Context.class).newInstance(mContext);
        XposedHelpers.setAdditionalInstanceField(tileView, TILE_KEY_NAME, mKey);

        tileView.setLongClickable(true);
        tileView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return handleLongClick(v);
            }
        });

        return tileView;
    }

    public void refreshState() {
        try {
            XposedHelpers.callMethod(mTile, "refreshState");
        } catch (Throwable t) {
            log("Error refreshing tile state: ");
            XposedBridge.log(t);
        }
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
