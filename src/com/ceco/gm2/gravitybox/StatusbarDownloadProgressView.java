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

package com.ceco.gm2.gravitybox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ceco.gm2.gravitybox.StatusBarIconManager.ColorInfo;
import com.ceco.gm2.gravitybox.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

public class StatusbarDownloadProgressView extends View implements IconManagerListener, BroadcastSubReceiver {
    private static final String TAG = "GB:StatusbarDownloadProgressView";
    private static final boolean DEBUG = false;

    public static final List<String> SUPPORTED_PACKAGES = new ArrayList<String>(Arrays.asList(
            "com.android.providers.downloads",
            "com.android.bluetooth",
            "com.mediatek.bluetooth"
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

        if (mId.equals(getIdentifier(statusBarNotif))) {
            if (DEBUG) log("updating progress for " + mId);
            updateProgress(statusBarNotif);
        }
    }

    public void onNotificationRemoved(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

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
        if (statusBarNotif == null || !(Boolean) XposedHelpers.callMethod(statusBarNotif, "isOngoing")) {
            return false;
        }

        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        if (SUPPORTED_PACKAGES.contains(pkgName)) {
            return true;
        } else {
            Notification n = (Notification) XposedHelpers.getObjectField(statusBarNotif, "notification");
            if (n != null) {
                Bundle extras = (Bundle) XposedHelpers.getAdditionalInstanceField(n, ModLedControl.NOTIF_EXTRAS);
                return (extras != null && extras.getBoolean(ModLedControl.NOTIF_EXTRA_PROGRESS_TRACKING));
            } else {
                return false;
            }
        }
    }

    protected String getIdentifier(Object statusBarNotif) {
        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        if (Build.VERSION.SDK_INT > 17 && SUPPORTED_PACKAGES.get(0).equals(pkgName)) {
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
            newWidth = (int) ((float)maxWidth * getProgress(n));
        }
        if (DEBUG) log("updateProgress: maxWidth=" + maxWidth + "; newWidth=" + newWidth);
        ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) getLayoutParams();
        lp.width = newWidth;
        setLayoutParams(lp);
        setVisibility(newWidth > 0 ? View.VISIBLE : View.GONE);
    }

    private float getProgress(Notification n) {
        int total = 0;
        int current = 0;

        // We have to extract the information from the content view
        RemoteViews views = n.bigContentView;
        if (views == null) views = n.contentView;
        if (views == null) return 0f;

        try {
            @SuppressWarnings("unchecked")
            ArrayList<Parcelable> actions = (ArrayList<Parcelable>) 
                XposedHelpers.getObjectField(views, "mActions");
            if (actions == null) return 0f;

            for (Parcelable p : actions) {
                Parcel parcel = Parcel.obtain();
                p.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);

                // The tag tells which type of action it is (2 is ReflectionAction)
                int tag = parcel.readInt();
                if (tag != 2)  {
                    parcel.recycle();
                    continue;
                }

                parcel.readInt(); // skip View ID
                String methodName = parcel.readString();
                if ("setMax".equals(methodName)) {
                    parcel.readInt(); // skip type value
                    total = parcel.readInt();
                    if (DEBUG) log("getProgress: total=" + total);
                } else if ("setProgress".equals(methodName)) {
                    parcel.readInt(); // skip type value
                    current = parcel.readInt();
                    if (DEBUG) log("getProgress: current=" + current);
                }

                parcel.recycle();
            }
        } catch (Throwable  t) {
            XposedBridge.log(t);
        }

        return (total > 0 ? ((float)current/(float)total) : 0f);
    }

    private void updatePosition() {
        if (mMode == Mode.OFF) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.gravity = mMode == Mode.TOP ? (Gravity.TOP | Gravity.START) :
            (Gravity.BOTTOM | Gravity.START);
        setLayoutParams(lp);
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            setBackgroundColor(colorInfo.coloringEnabled ?
                    colorInfo.iconColor[0] : colorInfo.defaultIconColor);
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
