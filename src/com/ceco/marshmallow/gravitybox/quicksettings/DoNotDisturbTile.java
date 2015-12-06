package com.ceco.marshmallow.gravitybox.quicksettings;

import android.provider.Settings;
import de.robv.android.xposed.XSharedPreferences;

public class DoNotDisturbTile extends AospTile {
    public static final String AOSP_KEY = "dnd";

    protected DoNotDisturbTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_dnd", tile, prefs, eventDistributor);
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.DndTile";
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        startSettingsActivity(Settings.ACTION_SOUND_SETTINGS);
        return true;
    }
}
