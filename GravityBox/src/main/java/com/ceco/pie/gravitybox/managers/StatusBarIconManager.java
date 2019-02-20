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

package com.ceco.pie.gravitybox.managers;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import android.graphics.Color;

public class StatusBarIconManager {
    private static final String TAG = "GB:StatusBarIconManager";
    private static final boolean DEBUG = false;

    public static final int FLAG_ICON_ALPHA_CHANGED = 1 << 0;
    public static final int FLAG_ICON_TINT_CHANGED = 1 << 1;
    public static final int FLAG_HEADS_UP_VISIBILITY_CHANGED = 1 << 2;
    private static final int FLAG_ALL = 0x7;

    private ColorInfo mColorInfo;
    private List<IconManagerListener> mListeners;

    public interface IconManagerListener {
        void onIconManagerStatusChanged(int flags, ColorInfo colorInfo);
    }

    public static class ColorInfo {
        public float alphaSignalCluster;
        public float alphaTextAndBattery;
        public int iconTint;
        public boolean headsUpVisible;
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected StatusBarIconManager() {
        initColorInfo();
        mListeners = new ArrayList<>();
    }

    private void initColorInfo() {
        mColorInfo = new ColorInfo();
        mColorInfo.alphaSignalCluster = 1;
        mColorInfo.alphaTextAndBattery = 1;
        mColorInfo.iconTint = Color.WHITE;
    }

    public void registerListener(IconManagerListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onIconManagerStatusChanged(FLAG_ALL, mColorInfo);
        }
    }

    public void unregisterListener(IconManagerListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners(int flags) {
        for (IconManagerListener listener : mListeners) {
            listener.onIconManagerStatusChanged(flags, mColorInfo);
        }
    }

    public void refreshState() {
        notifyListeners(FLAG_ALL);
    }

    public void setIconAlpha(float alphaSignalCluster, float alphaTextAndBattery) {
        if (mColorInfo.alphaSignalCluster != alphaSignalCluster ||
                mColorInfo.alphaTextAndBattery != alphaTextAndBattery) {
            mColorInfo.alphaSignalCluster = alphaSignalCluster;
            mColorInfo.alphaTextAndBattery = alphaTextAndBattery;
            notifyListeners(FLAG_ICON_ALPHA_CHANGED);
        }
    }

    public void setIconTint(int iconTint) {
        if (mColorInfo.iconTint != iconTint) {
            mColorInfo.iconTint = iconTint;
            notifyListeners(FLAG_ICON_TINT_CHANGED);
        }
    }

    public void setHeadsUpVisible(boolean visible) {
        if (mColorInfo.headsUpVisible != visible) {
            mColorInfo.headsUpVisible = visible;
            notifyListeners(FLAG_HEADS_UP_VISIBILITY_CHANGED);
        }
    }
}
