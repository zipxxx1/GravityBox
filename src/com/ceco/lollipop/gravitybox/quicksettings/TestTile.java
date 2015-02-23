package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.R;

public class TestTile extends QsTile {

    public TestTile(Object host, String key) throws Throwable {
        super(host, key);
        mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_gravitybox);
        mState.label = "GB Test Tile";
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        mState.visible = true;
        mState.applyTo(state);
        if (DEBUG) log("Tile " + mKey + " state updated: " + state);
    }
}
