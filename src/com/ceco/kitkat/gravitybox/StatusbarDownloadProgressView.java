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
import android.widget.LinearLayout;

public class StatusbarDownloadProgressView extends View implements IconManagerListener, BroadcastSubReceiver {
    private static final String TAG = "GB:StatusbarDownloadProgressView";
    private static final boolean DEBUG = false;

    private boolean mEnabled;
    private String mPackageName;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StatusbarDownloadProgressView(Context context, XSharedPreferences prefs) {
        super(context);

        mEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS, false);

        int heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                context.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx);
        lp.gravity = Gravity.TOP | Gravity.START;
        setLayoutParams(lp);
        setBackgroundColor(Color.WHITE);
        setVisibility(View.GONE);
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
        if (!mEnabled) return;

        if (!verifyNotification(statusBarNotif)) {
            if (DEBUG) log("onNotificationAdded: ignoring non-download provider notification");
            return;
        }

        if (mPackageName != null) {
            if (DEBUG) log("onNotificationAdded: another download already in registered");
            return;
        }

        mPackageName = getPackageName(statusBarNotif);
        if (mPackageName != null) {
            if (DEBUG) log("starting progress for " + mPackageName);
            updateProgress(statusBarNotif);
        }
    }

    public void onNotificationUpdated(Object statusBarNotif) {
        if (!mEnabled) return;

        if (mPackageName == null) {
            // treat it as if it was added, e.g. to show progress in case
            // feature has been enabled during already ongoing download
            onNotificationAdded(statusBarNotif);
            return;
        }

        if (!verifyNotification(statusBarNotif)) {
            if (DEBUG) log("onNotificationUpdated: ignoring non-download provider notification");
            return;
        }

        if (mPackageName.equals(getPackageName(statusBarNotif))) {
            if (DEBUG) log("updating progress for " + mPackageName);
            updateProgress(statusBarNotif);
        }
    }

    public void onNotificationRemoved(Object statusBarNotif) {
        if (!mEnabled) return;

        if (!verifyNotification(statusBarNotif)) {
            if (DEBUG) log("onNotificationRemoved: ignoring non-download provider notification");
            return;
        }

        if (mPackageName == null) {
            if (DEBUG) log("onNotificationRemoved: no download registered");
            return;
        } else if (mPackageName.equals(getPackageName(statusBarNotif))) {
            if (DEBUG) log("finishing progress for " + mPackageName);
            mPackageName = null;
            updateProgress(null);
        }
    }

    private boolean verifyNotification(Object statusBarNotif) {
        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        if (ModDownloadProvider.PACKAGE_NAME.equals(pkgName)) {
            return true;
        }
        return false;
    }

    private String getPackageName(Object statusBarNotif) {
        String tag = (String) XposedHelpers.getObjectField(statusBarNotif, "tag");
        if (tag != null && tag.contains(":")) {
            return tag.substring(tag.indexOf(":")+1);
        }
        if (DEBUG) log("getPackageName: Unexpected notification tag: " + tag);
        return null;
    }

    private void updateProgress(Object statusBarNotif) {
        int maxWidth = ((View) getParent()).getWidth();
        int newWidth = 0;
        if (statusBarNotif != null) {
            Notification n = (Notification) XposedHelpers.getObjectField(statusBarNotif, "notification");
            if (n.extras.containsKey(Notification.EXTRA_PROGRESS)) {
                newWidth = (int) ((float)maxWidth * (float)(n.extras.getInt(Notification.EXTRA_PROGRESS)*0.01f));
            }
        }
        if (DEBUG) log("updateProgress: maxWidth=" + maxWidth + "; newWidth=" + newWidth);
        ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
        lp.width = newWidth;
        setLayoutParams(lp);
        setVisibility(newWidth > 0 ? View.VISIBLE : View.GONE);
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
            mEnabled = intent.getBooleanExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED, false);
            if (!mEnabled) {
                mPackageName = null;
                updateProgress(null);
            }
        }
    }
}
