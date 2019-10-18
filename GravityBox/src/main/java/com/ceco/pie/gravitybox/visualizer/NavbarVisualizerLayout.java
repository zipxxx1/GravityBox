/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.pie.gravitybox.visualizer;

import com.ceco.pie.gravitybox.ColorUtils;
import com.ceco.pie.gravitybox.GravityBoxSettings;
import com.ceco.pie.gravitybox.R;
import com.ceco.pie.gravitybox.Utils;
import com.ceco.pie.gravitybox.ModStatusBar.StatusBarState;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class NavbarVisualizerLayout extends AVisualizerLayout {

    private static final String TAG = "GB:NavbarVisualizerLayout";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private int mLightColor;
    private int mDarkColor;
    private int mOpacity;
    private boolean mLightNavbar;
    private boolean mEnabled;

    public NavbarVisualizerLayout(Context context) {
        super(context);
    }

    @Override
    public void initPreferences(XSharedPreferences prefs) {
        super.initPreferences(prefs);
        mOpacity = Math.round(255f * ((float)prefs.getInt(GravityBoxSettings.PREF_KEY_VISUALIZER_OPACITY, 50)/100f));
        mEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VISUALIZER_NAVBAR, false);
    }

    @Override
    public void onPreferenceChanged(Intent intent) {
        super.onPreferenceChanged(intent);
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_OPACITY)) {
            mOpacity = Math.round(255f * ((float)intent.getIntExtra(
                    GravityBoxSettings.EXTRA_VISUALIZER_OPACITY, 50)/100f));
        }
        if (intent.hasExtra(GravityBoxSettings.EXTRA_VISUALIZER_NAVBAR)) {
            mEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VISUALIZER_NAVBAR, false);
        }
    }


    @Override
    public void onCreateView(ViewGroup parent) throws Throwable {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        setLayoutParams(lp);
        parent.addView(this, 0);

        LayoutInflater inflater = LayoutInflater.from(Utils.getGbContext(getContext(),
                getContext().getResources().getConfiguration()));
        inflater.inflate(R.layout.navbarvisualizer, this);
        mVisualizerView = new VisualizerView(getContext());
        mVisualizerView.setDbCapValue(4f);
        mVisualizerView.setSupportsVerticalPosition(true);
        int idx = indexOfChild(findViewById(R.id.visualizer));
        removeViewAt(idx);
        addView(mVisualizerView, idx);
    }

    @Override
    boolean supportsCurrentStatusBarState() {
        return mStatusBarState == StatusBarState.SHADE;
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && mEnabled;
    }

    @Override
    public void onColorUpdated(int color) {
        color = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        mDarkColor = ColorUtils.findContrastColor(color, Color.WHITE, true, 2);
        mLightColor = ColorUtils.findContrastColorAgainstDark(color, Color.BLACK, true, 2);
        color = mLightNavbar ? mDarkColor : mLightColor;
        color = Color.argb(mOpacity, Color.red(color), Color.green(color), Color.blue(color));
        setColor(color);
    }

    @Override
    public void setLight(boolean light) {
        super.setLight(light);
        if (mLightNavbar != light) {
            mLightNavbar = light;
            int color = light ? mDarkColor : mLightColor;
            mVisualizerView.setColor(Color.argb(
                    mOpacity, Color.red(color),
                    Color.green(color), Color.blue(color)));
        }
    }
}
