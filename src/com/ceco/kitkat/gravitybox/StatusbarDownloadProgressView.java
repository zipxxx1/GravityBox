/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.kitkat.gravitybox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ceco.kitkat.gravitybox.StatusBarIconManager.ColorInfo;
import com.ceco.kitkat.gravitybox.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class StatusbarDownloadProgressView extends View implements IconManagerListener, BroadcastSubReceiver {
    private static final String TAG = "GB:StatusbarDownloadProgressView";
    private static final boolean DEBUG = false;

    public static final List<String> SUPPORTED_PACKAGES = new ArrayList<String>(Arrays.asList(
            "com.android.providers.downloads",
            "com.android.bluetooth",
            "com.mediatek.bluetooth",
            "com.android.chrome",
            "org.mozilla.firefox"
    ));

    private enum Mode { OFF, TOP, BOTTOM };
    private Mode mMode;
    private String mId;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StatusbarDownloadProgressView(Context context, XSharedPreferences prefs) {
        super(context);

        mMode = Mode.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS, "OFF"));

        int heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                context.getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, heightPx);
        setLayoutParams(lp);
        setBackgroundColor(Color.WHITE);
        setVisibility(View.GONE);
        updatePosition();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ModStatusbarColor.registerIconManagerListener(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ModStatusbarColor.unregisterIconManagerListener(this);
    }

    public void onNotificationAdded(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        if (!verifyNotification(statusBarNotif)) {
            if (DEBUG) log("onNotificationAdded: ignoring unsupported notification");
            return;
        }

        if (mId != null) {
            if (DEBUG) log("onNotificationAdded: another download already registered");
            return;
        }

        mId = getIdentifier(statusBarNotif);
        if (mId != null) {
            if (DEBUG) log("starting progress for " + mId);
            updateProgress(statusBarNotif);
        }
    }

    public void onNotificationUpdated(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        if (mId == null) {
            // treat it as if it was added, e.g. to show progress in case
            // feature has been enabled during already ongoing download
            onNotificationAdded(statusBarNotif);
            return;
        }

        if (!verifyNotification(statusBarNotif)) {
            if (DEBUG) log("onNotificationUpdated: ignoring unsupported notification");
            return;
        }

        if (mId.equals(getIdentifier(statusBarNotif))) {
            if (DEBUG) log("updating progress for " + mId);
            updateProgress(statusBarNotif);
        }
    }

    public void onNotificationRemoved(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        if (!verifyNotification(statusBarNotif)) {
            if (DEBUG) log("onNotificationRemoved: ignoring unsupported notification");
            return;
        }

        if (mId == null) {
            if (DEBUG) log("onNotificationRemoved: no download registered");
            return;
        } else if (mId.equals(getIdentifier(statusBarNotif))) {
            if (DEBUG) log("finishing progress for " + mId);
            mId = null;
            updateProgress(null);
        }
    }

    private boolean verifyNotification(Object statusBarNotif) {
        if (statusBarNotif == null) return false;
        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        if (SUPPORTED_PACKAGES.contains(pkgName)) {
            return (Boolean) XposedHelpers.callMethod(statusBarNotif, "isOngoing");
        }
        return false;
    }

    private String getIdentifier(Object statusBarNotif) {
        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        if (SUPPORTED_PACKAGES.get(0).equals(pkgName)) {
            String tag = (String) XposedHelpers.getObjectField(statusBarNotif, "tag");
            if (tag != null && tag.contains(":")) {
                return pkgName + ":" + tag.substring(tag.indexOf(":")+1);
            }
            if (DEBUG) log("getIdentifier: Unexpected notification tag: " + tag);
        } else {
            return (pkgName + ":" + String.valueOf(XposedHelpers.getIntField(statusBarNotif, "id")));
        }
        return null;
    }

    private void updateProgress(Object statusBarNotif) {
        int maxWidth = ((View) getParent()).getWidth();
        int newWidth = 0;
        if (statusBarNotif != null) {
            Notification n = (Notification) XposedHelpers.getObjectField(statusBarNotif, "notification");
            if (n != null && n.extras.containsKey(Notification.EXTRA_PROGRESS)) {
                final int progress = n.extras.getInt(Notification.EXTRA_PROGRESS);
                final int progressMax = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
                if (progressMax > 0) {
                    newWidth = (int) ((float)maxWidth * (float)progress/(float)progressMax);
                }
            }
        }
        if (DEBUG) log("updateProgress: maxWidth=" + maxWidth + "; newWidth=" + newWidth);
        ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
        lp.width = newWidth;
        setLayoutParams(lp);
        setVisibility(newWidth > 0 ? View.VISIBLE : View.GONE);
    }

    private void updatePosition() {
        if (mMode == Mode.OFF) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.gravity = mMode == Mode.TOP ? (Gravity.TOP | Gravity.START) :
            (Gravity.BOTTOM | Gravity.START);
        if (Utils.isMtkDevice()) {
            lp.setMargins(0, mMode == Mode.TOP ? 
                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, 
                            getResources().getDisplayMetrics()) : 0, 0, 0);
        }
        setLayoutParams(lp);
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            setBackgroundColor(colorInfo.coloringEnabled ?
                    colorInfo.iconColor[0] : Color.WHITE);
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaSignalCluster);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_DOWNLOAD_PROGRESS_CHANGED)) {
            mMode = Mode.valueOf(intent.getStringExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED));
            if (mMode == Mode.OFF) {
                mId = null;
                updateProgress(null);
            } else {
                updatePosition();
            }
        }
    }
}
