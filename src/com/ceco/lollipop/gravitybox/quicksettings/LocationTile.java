package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XSharedPreferences;

public class LocationTile extends AospTile {
    public static final String AOSP_KEY = "location";

    protected LocationTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_location", tile, prefs, eventDistributor);
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.LocationTile";
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }
}
