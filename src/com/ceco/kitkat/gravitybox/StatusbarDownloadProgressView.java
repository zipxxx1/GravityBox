/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ceco.kitkat.gravitybox.managers.StatusBarIconManager;
import com.ceco.kitkat.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.kitkat.gravitybox.managers.SysUiManagers;
import com.ceco.kitkat.gravitybox.managers.StatusBarIconManager.IconManagerListener;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
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
    private static final int INDEX_CYCLER_FREQUENCY = 5000; // ms
    private static final long MAX_IDLE_TIME = 10000; // ms

    public interface ProgressStateListener {
        void onProgressTrackingStarted(Mode mode);
        void onProgressTrackingStopped();
        void onProgressModeChanged(Mode mode);
    }

    private static class ProgressInfo {
        String id;
        boolean hasProgressBar;
        int progress;
        int max;
        long lastUpdatedMs;

        ProgressInfo(String id, boolean hasProgressBar, int progress, int max) {
            this.id = id;
            this.hasProgressBar = hasProgressBar;
            this.progress = progress;
            this.max = max;
            this.lastUpdatedMs = System.currentTimeMillis();
        }

        float getFraction() {
            return (max > 0 ? ((float)progress/(float)max) : 0f);
        }

        boolean isIdle() {
            long idleTime = (System.currentTimeMillis() - this.lastUpdatedMs);
            if (DEBUG) log("ProgressInfo: '" + this.id + 
                    "' is idle for " + idleTime + "ms");
            return (idleTime > MAX_IDLE_TIME);
        }
    }

    public enum Mode { OFF, TOP, BOTTOM };
    private Mode mMode;
    private List<ProgressStateListener> mListeners;
    private boolean mAnimated;
    private ObjectAnimator mAnimator;
    private boolean mCentered;
    private int mHeightPx;
    private int mEdgeMarginPx;
    private boolean mSoundEnabled;
    private String mSoundUri;
    private boolean mSoundWhenScreenOffOnly;
    private PowerManager mPowerManager;
    private Map<String, ProgressInfo> mProgressList = new LinkedHashMap<String, ProgressInfo>();
    private int mCurrentIndex = 0;

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
        mSoundEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_ENABLE, false);
        mSoundUri = prefs.getString(GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_SOUND,
                "content://settings/system/notification_sound");
        mSoundWhenScreenOffOnly = prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_SCREEN_OFF, false);

        mListeners = new ArrayList<ProgressStateListener>();
        mProgressList = new LinkedHashMap<String, ProgressInfo>();
        mPowerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);

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
        setPivotX(mCentered ? w/2f :
            Utils.isRTL(getContext()) ? getWidth() : 0f);
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

    private void notifyProgressTrackingStarted() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressTrackingStarted(mMode);
            }
        }
    }

    private void notifyProgressTrackingStopped() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressTrackingStopped();
            }
        }
    }

    private void notifyProgressModeChanged() {
        synchronized (mListeners) {
            for (ProgressStateListener l : mListeners) {
                l.onProgressModeChanged(mMode);
            }
        }
    }

    private void addProgress(ProgressInfo pi) {
        synchronized (mProgressList) {
            if (!mProgressList.containsKey(pi.id)) {
                mProgressList.put(pi.id, pi);
                if (DEBUG) log("addProgress: added progress for '" + pi.id + "'");
                resetIndexCycler(mProgressList.size()-1);
                updateProgressView(true);
                if (mProgressList.size() == 1) {
                    notifyProgressTrackingStarted();
                }
            } else if (DEBUG) {
                log("addProgress: progress for '" + pi.id + "' already exists");
            }
        }
    }

    private void removeProgress(String id, boolean allowSound) {
        synchronized (mProgressList) {
            if (id == null) {
                mProgressList.clear();
                if (DEBUG) log("removeProgress: all cleared");
            } else if (mProgressList.containsKey(id)) {
                mProgressList.remove(id);
                if (DEBUG) log("removeProgress: removed progress for '" + id + "'");
                if (allowSound) maybePlaySound();
            }
        }
        resetIndexCycler(0);
        updateProgressView(true);
        if (mProgressList.size() == 0) {
            notifyProgressTrackingStopped();
        }
    }

    private void updateProgress(String id, int max, int progress) {
        ProgressInfo pi = mProgressList.get(id);
        if (pi != null) {
            pi.max = max;
            pi.progress = progress;
            pi.lastUpdatedMs = System.currentTimeMillis();
            if (DEBUG) {
                log("updateProgress: updated progress for '" + id + "': " +
                        "max=" + max + "; progress=" + progress);
            }
            if (id.equals(getCurrentId())) {
                updateProgressView(false);
            }
        }
    }

    private Runnable mIndexCyclerRunnable = new Runnable() {
        @Override
        public void run() {
            boolean shouldUpdateView = false;

            // clear idle first
            synchronized (mProgressList) {
                List<String> toRemove = new ArrayList<>();
                for (ProgressInfo pi : mProgressList.values())
                    if (pi.isIdle()) toRemove.add(pi.id);
                for (String id : toRemove)
                    mProgressList.remove(id);
                shouldUpdateView |= !toRemove.isEmpty();
            }

            // cycle index
            final int oldIndex = mCurrentIndex++;
            if (mCurrentIndex >= mProgressList.size()) mCurrentIndex = 0;
            if (DEBUG) log("IndexCycler: oldIndex=" + oldIndex + "; " +
                    "mCurrentIndex=" + mCurrentIndex);
            shouldUpdateView |= (mCurrentIndex != oldIndex);

            if (shouldUpdateView) {
                updateProgressView(mCurrentIndex != oldIndex);
            }

            if (mProgressList.size() > 0) {
                StatusbarDownloadProgressView.this.postDelayed(this, INDEX_CYCLER_FREQUENCY);
            }
        }
    };

    private void resetIndexCycler(int toIndex) {
        removeCallbacks(mIndexCyclerRunnable);
        mCurrentIndex = toIndex;
        if (mProgressList.size() > 0) {
            postDelayed(mIndexCyclerRunnable, INDEX_CYCLER_FREQUENCY);
        }
    }

    private String getCurrentId() {
        return (mCurrentIndex < mProgressList.size() ? 
                ((ProgressInfo)mProgressList.values().toArray()[mCurrentIndex]).id :
                null);
    }

    public void onNotificationAdded(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        ProgressInfo pi = verifyNotification(statusBarNotif);
        if (pi == null) {
            if (DEBUG) log("onNotificationAdded: ignoring unsupported notification");
            return;
        }

        addProgress(pi);
    }

    public void onNotificationUpdated(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        ProgressInfo pi = verifyNotification(statusBarNotif);
        if (pi == null) {
            String id = getIdentifier(statusBarNotif);
            if (id != null && mProgressList.containsKey(id)) {
                removeProgress(id, true);
                if (DEBUG) log("onNotificationUpdated: removing no longer " +
                        "supported notification for '" + id + "'");
            } else if (DEBUG) {
                log("onNotificationUpdated: ignoring unsupported notification");
            }
            return;
        }

        if (!mProgressList.containsKey(pi.id)) {
            // treat it as if it was added, e.g. to show progress in case
            // feature has been enabled during already ongoing download
            addProgress(pi);
        } else {
            updateProgress(pi.id, pi.max, pi.progress);
        }
    }

    public void onNotificationRemoved(Object statusBarNotif) {
        if (mMode == Mode.OFF) return;

        String id = getIdentifier(statusBarNotif);
        if (id != null && mProgressList.containsKey(id)) {
            removeProgress(id, true);
        }
    }

    private ProgressInfo verifyNotification(Object statusBarNotif) {
        if (statusBarNotif == null) {
            return null;
        }

        String id = getIdentifier(statusBarNotif);
        if (id == null)
            return null;

        String pkgName = (String) XposedHelpers.getObjectField(statusBarNotif, "pkg");
        Notification n = (Notification) XposedHelpers.getObjectField(statusBarNotif, "notification");
        if (n != null && 
               (SUPPORTED_PACKAGES.contains(pkgName) ||
                n.extras.getBoolean(ModLedControl.NOTIF_EXTRA_PROGRESS_TRACKING))) {
            ProgressInfo pi = getProgressInfo(id, n);
            if (pi != null && pi.hasProgressBar)
                return pi;
        }
        return null;
    }

    private String getIdentifier(Object statusBarNotif) {
        if (statusBarNotif == null) return null;
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

    private void updateProgressView(boolean fadeOutAndIn) {
        clearAnimation();
        if (!mProgressList.isEmpty()) {
            if (mAnimator.isStarted()) {
                mAnimator.cancel();
            }
            ProgressInfo pi = (ProgressInfo) mProgressList.values().toArray()[mCurrentIndex];
            float newScaleX = pi.getFraction();
            if (DEBUG) log("updateProgressView: id='" + 
                    pi.id + "'; newScaleX=" + newScaleX);
            if (getVisibility() != View.VISIBLE) {
                fadeIn(newScaleX);
            } else if (fadeOutAndIn) {
                fadeOutAndIn(newScaleX);
            } else if (mAnimated) {
                animateScaleXTo(newScaleX);
            } else {
                setScaleX(newScaleX);
            }
        } else {
            removeCallbacks(mIndexCyclerRunnable);
            if (mAnimator.isStarted()) {
                mAnimator.end();
            }
            if (getVisibility() == View.VISIBLE) {
                fadeOut();
            }
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

    private void fadeOutAndIn(final float newScaleX) {
        animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION / 2)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    setScaleX(newScaleX);
                    animate()
                        .alpha(1f)
                        .setDuration(ANIM_DURATION / 2);
                }
            });
    }

    private void fadeIn(final float newScaleX) {
        setAlpha(0f);
        setScaleX(newScaleX);
        setVisibility(View.VISIBLE);
        animate()
            .alpha(1f)
            .setDuration(ANIM_DURATION);
    }

    private void fadeOut() {
        animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.GONE);
                    setScaleX(0f);
                    setAlpha(1f);
                }
            });
    }

    private ProgressInfo getProgressInfo(String id, Notification n) {
        if (id == null || n == null) return null;
        ProgressInfo pInfo = new ProgressInfo(id, false, 0, 0);

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

    private void maybePlaySound() {
        if (mSoundEnabled &&
                (!mPowerManager.isScreenOn() || !mSoundWhenScreenOffOnly)) {
            try {
                final Ringtone sfx = RingtoneManager.getRingtone(getContext(),
                        Uri.parse(mSoundUri));
                if (sfx != null) {
                    sfx.setStreamType(AudioManager.STREAM_NOTIFICATION);
                    sfx.play();
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    private void updatePosition() {
        if (mMode == Mode.OFF) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.height = mHeightPx;
        lp.gravity = mMode == Mode.TOP ? (Gravity.TOP | Gravity.START) :
            (Gravity.BOTTOM | Gravity.START);
        lp.setMargins(0, mMode == Mode.TOP ? mEdgeMarginPx : 0,
                      0, mMode == Mode.BOTTOM ? mEdgeMarginPx : 0);
        setLayoutParams(lp);
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            setBackgroundColor(colorInfo.coloringEnabled ?
                    colorInfo.iconColor[0] : Color.WHITE);
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaTextAndBattery);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_STATUSBAR_DOWNLOAD_PROGRESS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED)) {
                mMode = Mode.valueOf(intent.getStringExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_ENABLED));
                notifyProgressModeChanged();
                if (mMode == Mode.OFF) {
                    removeProgress(null, false);
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
                setPivotX(mCentered ? getWidth()/2f :
                    Utils.isRTL(getContext()) ? getWidth() : 0f);
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
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_ENABLE)) {
                mSoundEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_ENABLE, false);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND)) {
                mSoundUri = intent.getStringExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_SCREEN_OFF)) {
                mSoundWhenScreenOffOnly = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_STATUSBAR_DOWNLOAD_PROGRESS_SOUND_SCREEN_OFF, false);
            }
        }
    }
}
