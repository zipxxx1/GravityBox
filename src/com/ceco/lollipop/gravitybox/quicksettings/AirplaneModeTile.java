package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XSharedPreferences;

public class AirplaneModeTile extends AospTile {
    public static final String AOSP_KEY = "airplane";

    protected AirplaneModeTile(Object host, Object tile, XSharedPreferences prefs, 
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_airplane_mode", tile, prefs, eventDistributor);
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.AirplaneModeTile";
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }
}
