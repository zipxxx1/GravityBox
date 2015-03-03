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

package com.ceco.lollipop.gravitybox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarState;
import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarStateChangedListener;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

public class StatusbarDownloadProgressView extends View implements 
                                            IconManagerListener, 
                                            BroadcastSubReceiver,
                                            StatusBarStateChangedListener {
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
    private int mHeightPx;
    private int mEdgeMarginPx;
    private int mStatusBarState;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StatusbarDownloadProgressView(Context context, XSharedPreferences prefs) {
        super(context);

        mMode = Mode.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS, "OFF"));
        mAnimated = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_ANIMATED, true);
        mCentered = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_CENTERED, false);
        mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_THICKNESS, 1),
                context.getResources().getDisplayMetrics());
        mEdgeMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                prefs.getInt(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_MARGIN, 0),
                context.getResources().getDisplayMetrics());

        mListeners = new ArrayList<ProgressStateListener>();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, mHeightPx);
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
    }

    public void onNotificationAdded(StatusBarNotification statusBarNotif) {
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

    public void onNotificationUpdated(StatusBarNotification statusBarNotif) {
        if (mMode == Mode.OFF) return;

        if (mId == null) {
            // treat it as if it was added, e.g. to show progress in case
            // feature has been enabled during already ongoing download
            onNotificationAdded(statusBarNotif);
            return;
        }

        if (mId.equals(getIdentifier(statusBarNotif))) {
            // if notification became clearable, stop tracking immediately
            if (statusBarNotif.isClearable()) {
                if (DEBUG) log("onNotificationUpdated: notification became clearable - stopping tracking");
                stopTracking();
            } else {
                if (DEBUG) log("updating progress for " + mId);
                updateProgress(statusBarNotif);
            }
        }
    }

    public void onNotificationRemoved(StatusBarNotification statusBarNotif) {
        if (mMode == Mode.OFF) return;

        if (mId == null) {
            if (DEBUG) log("onNotificationRemoved: no download registered");
            return;
        } else if (mId.equals(getIdentifier(statusBarNotif))) {
            if (DEBUG) log("finishing progress for " + mId);
            stopTracking();
        }
    }

    private boolean verifyNotification(StatusBarNotification statusBarNotif) {
        if (statusBarNotif == null || statusBarNotif.isClearable()) {
            return false;
        }

        Notification n = statusBarNotif.getNotification();
        return (n != null && 
               (SUPPORTED_PACKAGES.contains(statusBarNotif.getPackageName()) ||
                n.extras.getBoolean(ModLedControl.NOTIF_EXTRA_PROGRESS_TRACKING)) &&
                getProgressInfo(n).hasProgressBar);
    }

    private String getIdentifier(StatusBarNotification statusBarNotif) {
        if (statusBarNotif == null) return null;
        String pkgName = statusBarNotif.getPackageName();
        if (SUPPORTED_PACKAGES.get(0).equals(pkgName)) {
            String tag = statusBarNotif.getTag();
            if (tag != null && tag.contains(":")) {
                return pkgName + ":" + tag.substring(tag.indexOf(":")+1);
            }
            if (DEBUG) log("getIdentifier: Unexpected notification tag: " + tag);
        } else {
            return (pkgName + ":" + String.valueOf(statusBarNotif.getId()));
        }
        return null;
    }

    private void updateProgress(StatusBarNotification statusBarNotif) {
        if (statusBarNotif != null) {
            float newScaleX = getProgressInfo(statusBarNotif.getNotification()).getFraction();
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
                    notifyProgressStopped();
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
        lp.height = mHeightPx;
        lp.gravity = (mStatusBarState != StatusBarState.SHADE ||  mMode == Mode.TOP) ?
                (Gravity.TOP | Gravity.START) : (Gravity.BOTTOM | Gravity.START);
        lp.setMargins(0, lp.gravity == Gravity.TOP ? mEdgeMarginPx : 0,
                      0, lp.gravity == Gravity.BOTTOM ? mEdgeMarginPx : 0);
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
    public void onStatusBarStateChanged(int oldState, int newState) {
        if (mStatusBarState != newState) {
            mStatusBarState = newState;
            updatePosition();
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
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_THICKNESS)) {
                mHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_THICKNESS, 1),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_MARGIN)) {
                mEdgeMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        intent.getIntExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_MARGIN, 0),
                        getResources().getDisplayMetrics());
                updatePosition();
            }
        }
    }
}
