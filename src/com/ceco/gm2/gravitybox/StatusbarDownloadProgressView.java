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

import com.ceco.gm2.gravitybox.managers.StatusBarIconManager;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.IconManagerListener;
import com.ceco.gm2.gravitybox.managers.SysUiManagers;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
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
import android.view.animation.DecelerateInterpolator;
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

    private static final int ANIM_DURATION = 400;

    public interface ProgressStateListener {
        void onProgressTrackingStarted(boolean isBluetooth, Mode mode);
        void onProgressTrackingStopped();
    }

    class ProgressInfo {
        boolean hasProgressBar;
        int progress;
        int max;

        public ProgressInfo(boolean hasProgressBar, int progress, int max) {
            this.hasProgressBar = hasProgressBar;
            this.progress = progress;
            this.max = max;
        }

        public float getFraction() {
            return (max > 0 ? ((float)progress/(float)max) : 0f);
        }
    }

    public enum Mode { OFF, TOP, BOTTOM };
    private Mode mMode;
    private String mId;
    private List<ProgressStateListener> mListeners;
    private boolean mAnimated;
    private ObjectAnimator mAnimator;
    private boolean mCentered;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StatusbarDownloadProgressView(Context context, XSharedPreferences prefs) {
        super(context);

        mMode = Mode.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS, "OFF"));
        mAnimated = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED, true);
        mCentered = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED, false);

        mListeners = new ArrayList<ProgressStateListener>();

        int heightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
                context.getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, heightPx);
        setLayoutParams(lp);
        setScaleX(0f);
        setBackgroundColor(Color.WHITE);
        setVisibility(View.GONE);
        updatePosition();

        mAnimator = new ObjectAnimator();
        mAnimator.setTarget(this);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.setDuration(ANIM_DURATION);
        mAnimator.setRepeatCount(0);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) log("w=" + w + "; h=" + h);
        setPivotX(mCentered ? w/2f : 0f);
    }

    public void registerListener(ProgressStateListener listener) {
        if (listener == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void unregisterListener(ProgressStateListener listener) {
        if (listener == null) return;
        synchronized (mListeners) {
            if (mListeners.contains(listener)) {
                mListeners.remove(listener);
            }
        }
    }

    private void notifyProgressStarted(boolean isBluetooth) {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressTrackingStarted(isBluetooth, mMode);
            }
        }
    }

    private void notifyProgressStopped() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressTrackingStopped();
            }
        }
    }

    private void stopTracking() {
        mId = null;
        updateProgress(null);
        notifyProgressStopped();
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
            notifyProgressStarted(mId.startsWith(SUPPORTED_PACKAGES.get(1)) ||
                    mId.startsWith(SUPPORTED_PACKAGES.get(2)));
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
            // if notification became clearable, stop tracking immediately
            if ((Boolean) XposedHelpers.callMethod(statusBarNotif, "isClearable")) {
                if (DEBUG) log("onNotificationUpdated: notification became clearable - stopping tracking");
                stopTracking();
            } else {
                if (DEBUG) log("updating progress for " + mId);
                updateProgress(statusBarNotif);
            }
        }
    }

    public void onNotificationRemoved(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        if (mId == null) {
            if (DEBUG) log("onNotificationRemoved: no download registered");
            return;
        } else if (mId.equals(getIdentifier(statusBarNotif))) {
            if (DEBUG) log("finishing progress for " + mId);
            stopTracking();
        }
    }

    private boolean verifyNotification(Object statusBarNotif) {
        if (statusBarNotif == null || (Boolean) XposedHelpers.callMethod(statusBarNotif, "isClearable")) {
            return false;
        }

        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        Notification n = (Notification) XposedHelpers.getObjectField(statusBarNotif, "notification");
        return (n != null && 
               (SUPPORTED_PACKAGES.contains(pkgName) || hasUncProgressTracking(n)) &&
                getProgressInfo(n).hasProgressBar);
    }

    private boolean hasUncProgressTracking(Notification n) {
        if (n == null) return false;
        Bundle extras = (Bundle) XposedHelpers.getAdditionalInstanceField(n, ModLedControl.NOTIF_EXTRAS);
        return (extras != null && extras.getBoolean(ModLedControl.NOTIF_EXTRA_PROGRESS_TRACKING));
    }

    private String getIdentifier(Object statusBarNotif) {
        if (statusBarNotif == null) return null;
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
        if (statusBarNotif != null) {
            Notification n = (Notification) XposedHelpers.getObjectField(statusBarNotif, "notification");
            float newScaleX = getProgressInfo(n).getFraction();
            if (DEBUG) log("updateProgress: newScaleX=" + newScaleX);
            setVisibility(View.VISIBLE);
            if (mAnimated) {
                animateScaleXTo(newScaleX);
            } else {
                setScaleX(newScaleX);
            }
        } else {
            if (mAnimator.isStarted()) {
                mAnimator.end();
            }
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    setScaleX(0f);
                    setVisibility(View.GONE);
                }
            }, 500);
        }
    }

    private void animateScaleXTo(float newScaleX) {
        if (mAnimator.isStarted()) {
            mAnimator.cancel();
        }
        mAnimator.setValues(PropertyValuesHolder.ofFloat("scaleX", getScaleX(), newScaleX));
        mAnimator.start();
        if (DEBUG) log("Animating to new scaleX: " + newScaleX);
    }

    private ProgressInfo getProgressInfo(Notification n) {
        ProgressInfo pInfo = new ProgressInfo(false, 0, 0);
        if (n == null) return pInfo;

        // We have to extract the information from the content view
        RemoteViews views = n.bigContentView;
        if (views == null) views = n.contentView;
        if (views == null) return pInfo;

        try {
            @SuppressWarnings("unchecked")
            ArrayList<Parcelable> actions = (ArrayList<Parcelable>) 
                XposedHelpers.getObjectField(views, "mActions");
            if (actions == null) return pInfo;

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
                    pInfo.max = parcel.readInt();
                    if (DEBUG) log("getProgressInfo: total=" + pInfo.max);
                } else if ("setProgress".equals(methodName)) {
                    parcel.readInt(); // skip type value
                    pInfo.progress = parcel.readInt();
                    pInfo.hasProgressBar = true;
                    if (DEBUG) log("getProgressInfo: current=" + pInfo.progress);
                }

                parcel.recycle();
            }
        } catch (Throwable  t) {
            XposedBridge.log(t);
        }

        return pInfo;
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
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED)) {
                mMode = Mode.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED));
                if (mMode == Mode.OFF) {
                    stopTracking();
                } else {
                    updatePosition();
                }
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED)) {
                mAnimated = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED, true);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED)) {
                mCentered = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED, false);
                setPivotX(mCentered ? getWidth()/2f : 0f);
            }
        }
    }
}
