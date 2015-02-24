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

    @Override
    public boolean supportsDualTargets() {
        if (DEBUG) log("Tile " + mKey + ": supportsDualTargets called");
        return true;
    }

    @Override
    public void handleClick() {
        if (DEBUG) log("Tile " + mKey + ": handleClick called");
    }

    @Override
    public void handleSecondaryClick() {
        if (DEBUG) log("Tile " + mKey + ": handleSecondaryClick called");
    }

    @Override
    public void setListening(boolean listening) {
        if (DEBUG) log("Tile " + mKey + ": setListening(" + listening + ") called");
    }
}
