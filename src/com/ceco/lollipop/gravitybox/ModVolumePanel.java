/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.lollipop.gravitybox;

import com.ceco.lollipop.gravitybox.ledcontrol.QuietHours;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHoursActivity;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageView.ScaleType;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModVolumePanel {
    private static final String TAG = "GB:ModVolumePanel";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String CLASS_VOLUME_PANEL = "com.android.systemui.volume.VolumePanel";
    private static final boolean DEBUG = false;

    private static final int MSG_TIMEOUT = 5;

    private static Object mVolumePanel;
    private static boolean mVolumeAdjustMuted;
    private static boolean mVolumeAdjustVibrateMuted;
    private static boolean mExpandable;
    private static boolean mAutoExpand;
    private static int mTimeout;
    private static XSharedPreferences mQhPrefs;
    private static QuietHours mQuietHours;
    private static boolean mVolumesLinked;
    private static boolean mPanelExpanded;
    private static LinearLayout mSliderPanel;
    private static Context mGbContext;
    private static int mIconRingerAudibleId;
    private static int mIconRingerAudibleIdOrig;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBrodcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_AUTOEXPAND)) {
                    mAutoExpand = intent.getBooleanExtra(GravityBoxSettings.EXTRA_AUTOEXPAND, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_MUTED)) {
                    mVolumeAdjustMuted = intent.getBooleanExtra(GravityBoxSettings.EXTRA_MUTED, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED)) {
                    mVolumeAdjustVibrateMuted = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_TIMEOUT)) {
                    mTimeout = intent.getIntExtra(GravityBoxSettings.EXTRA_TIMEOUT, 0);
                }
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED)) {
                mVolumesLinked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_LINKED, true);
                if (mExpandable) {
                    updateRingerIcon();
                }
            } else if (intent.getAction().equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mQhPrefs.reload();
                mQuietHours = new QuietHours(mQhPrefs);
            }
        }
        
    };

    public static void initResources(XSharedPreferences prefs, InitPackageResourcesParam resparam) {
        XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);

        mIconRingerAudibleId = 0;
        if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_EXPANDABLE, false) &&
                XposedBridge.XPOSED_BRIDGE_VERSION >= 64) {
            mIconRingerAudibleId = XResources.getFakeResId(modRes, R.drawable.ic_ringer_audible);
            resparam.res.setReplacement(mIconRingerAudibleId, modRes.fwd(R.drawable.ic_ringer_audible));
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePanel = XposedHelpers.findClass(CLASS_VOLUME_PANEL, classLoader);

            mQhPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "quiet_hours");
            mQuietHours = new QuietHours(mQhPrefs);

            mVolumeAdjustMuted = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ADJUST_MUTE, false);
            mVolumeAdjustVibrateMuted = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ADJUST_VIBRATE_MUTE, false);
            mExpandable = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_EXPANDABLE, false);
            mAutoExpand = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_AUTOEXPAND, false);
            mVolumesLinked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true);

            XposedBridge.hookAllConstructors(classVolumePanel, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mVolumePanel = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mVolumePanel, "mContext");
                    mGbContext = SysUiManagers.GbContext;
                    if (DEBUG) log("VolumePanel constructed; mVolumePanel set");

                    mTimeout = 0;
                    try {
                        mTimeout = Integer.valueOf(prefs.getString(
                                GravityBoxSettings.PREF_KEY_VOLUME_PANEL_TIMEOUT, "0"));
                    } catch (NumberFormatException nfe) {
                        log("Invalid value for PREF_KEY_VOLUME_PANEL_TIMEOUT preference");
                    }

                    if (mExpandable) {
                        Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(classVolumePanel, "STREAMS");
                        XposedHelpers.setBooleanField(streams[1], "show", true);
                        mIconRingerAudibleIdOrig = XposedHelpers.getIntField(streams[1], "iconRes");
                        XposedHelpers.setBooleanField(streams[2], "show", true);
                        XposedHelpers.setBooleanField(streams[5], "show", true);
                        replaceSliderPanel();
                        updateRingerIcon();
                    }

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED);
                    intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED);
                    context.registerReceiver(mBrodcastReceiver, intentFilter);
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "onPlaySound",
                    int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mVolumeAdjustMuted || 
                            mQuietHours.isSystemSoundMuted(QuietHours.SystemSound.VOLUME_ADJUST)) {
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "resetTimeout", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mTimeout != 0) {
                        Handler h = (Handler) param.thisObject;
                        h.removeMessages(MSG_TIMEOUT);
                        h.sendMessageDelayed(h.obtainMessage(MSG_TIMEOUT), mTimeout);
                        param.setResult(null);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "onVibrate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mVolumeAdjustVibrateMuted) {
                        param.setResult(null);
                    }
                }
            });

            if (mExpandable) {
                XposedHelpers.findAndHookMethod(classVolumePanel, "createSliders", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        createSliders();
                    }
                });
    
                XposedHelpers.findAndHookMethod(classVolumePanel, "reorderSliders", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mPanelExpanded) {
                            param.setResult(null);
                        }
                    }
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mAutoExpand && !mPanelExpanded) {
                            expandVolumePanel();
                        }
                    }
                });
    
                XposedHelpers.findAndHookMethod(classVolumePanel, "handleMessage", Message.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        Message msg = (Message) param.args[0];
                        if (msg.what == MSG_TIMEOUT && mPanelExpanded) {
                            collapseVolumePanel();
                        }
                    }
                });
    
                XposedHelpers.findAndHookMethod(classVolumePanel, "isNotificationOrRing", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        if (XposedHelpers.getBooleanField(mVolumePanel, "mVoiceCapable")) {
                            int streamType = (int) param.args[0];
                            boolean result = streamType == AudioManager.STREAM_RING ||
                                    (mVolumesLinked && streamType == AudioManager.STREAM_NOTIFICATION);
                            param.setResult(result);
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(classVolumePanel, "setZenPanelVisible", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mPanelExpanded && XposedHelpers.getBooleanField(param.thisObject, "mZenModeAvailable")) {
                            param.args[0] = true;
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void replaceSliderPanel() {
        try {
            ViewGroup originalPanel = (ViewGroup) XposedHelpers.getObjectField(mVolumePanel, "mSliderPanel");
            mSliderPanel = new LinearLayout(originalPanel.getContext());
            mSliderPanel.setId(originalPanel.getId());
            mSliderPanel.setOrientation(LinearLayout.VERTICAL);
            mSliderPanel.setBackground(originalPanel.getBackground());
            mSliderPanel.setPadding(originalPanel.getPaddingLeft(), originalPanel.getPaddingTop(),
                    originalPanel.getPaddingRight(), originalPanel.getPaddingBottom());
            mSliderPanel.setClipChildren(false);
            mSliderPanel.setLayoutParams(originalPanel.getLayoutParams());
            ViewGroup parent = (ViewGroup) originalPanel.getParent();
            int position = parent.indexOfChild(originalPanel);
            parent.removeView(originalPanel);
            parent.addView(mSliderPanel, position);
            XposedHelpers.setObjectField(mVolumePanel, "mSliderPanel", mSliderPanel);
            if (DEBUG) log("Slider panel replaced with linear layout");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void createSliders() {
        try {
            SparseArray<?> streamControls = (SparseArray<?>) XposedHelpers.getObjectField(
                    mVolumePanel, "mStreamControls");
            final int count = streamControls.size();
            for (int i = 0; i < count; i++) {
                Object sc = streamControls.valueAt(i);
                ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(sc, "group");
                group.addView(createButton(group.getContext()));
                // disable secondary icon on SDK 22+
                if (Build.VERSION.SDK_INT >= 22) {
                    ((View) XposedHelpers.getObjectField(sc, "divider"))
                        .setVisibility(View.GONE);
                    ((View) XposedHelpers.getObjectField(sc, "secondaryIcon"))
                        .setVisibility(View.GONE);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static ImageView createButton(Context context) {
        Resources res = context.getResources();
        int sizePx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48, res.getDisplayMetrics()));
        int bgResId = res.getIdentifier("btn_borderless_rect", "drawable", PACKAGE_NAME);
        ImageView v = new ImageView(context);
        v.setScaleType(ScaleType.CENTER);
        v.setImageDrawable(mGbContext.getDrawable(R.drawable.ic_expand_slider_panel));
        if (bgResId != 0) {
            v.setBackgroundResource(bgResId);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        v.setLayoutParams(lp);
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expandVolumePanel();
            }
        });
        return v;
    }

    private static boolean isValidExpandedPanelControl(int streamType) {
        try {
            boolean voiceCapable = XposedHelpers.getBooleanField(mVolumePanel, "mVoiceCapable");
            int activeStreamType = XposedHelpers.getIntField(mVolumePanel, "mActiveStreamType");
            switch (streamType) {
                case AudioManager.STREAM_NOTIFICATION:
                    if (voiceCapable && mVolumesLinked) {
                        return false;
                    }
                case AudioManager.STREAM_RING:
                    if (!voiceCapable) {
                        return false;
                    }
                case AudioManager.STREAM_MUSIC:
                case AudioManager.STREAM_ALARM:
                    if (streamType != activeStreamType) {
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }
        catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    private static void expandVolumePanel() {
        try {
            mPanelExpanded = true;
            Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(
                    mVolumePanel.getClass(), "STREAMS");
            SparseArray<?> streamControls = (SparseArray<?>) XposedHelpers.getObjectField(
                    mVolumePanel, "mStreamControls");
            int activeStreamType = XposedHelpers.getIntField(mVolumePanel, "mActiveStreamType");
    
            for (int i = 0; i < streams.length; i++) {
                final int streamType = XposedHelpers.getIntField(streams[i], "streamType");
                if (isValidExpandedPanelControl(streamType)) {
                    Object control = streamControls.get(streamType);
                    if (control != null && streamType != activeStreamType) {
                        ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(control, "group");
                        mSliderPanel.addView(group);
                        group.setVisibility(View.VISIBLE);
                        group.getChildAt(group.getChildCount()-1).setVisibility(View.GONE);
                        if (Build.VERSION.SDK_INT < 22) {
                            XposedHelpers.callMethod(mVolumePanel, "updateSlider", control);
                        } else {
                            XposedHelpers.callMethod(mVolumePanel, "updateSlider", control, false);
                        }
                        if (DEBUG) log("showing slider for stream type " + streamType);
                    }
                }
            }

            Object activeSc = streamControls.get(activeStreamType);
            if (activeSc != null) {
                ViewGroup activeGroup = (ViewGroup) XposedHelpers.getObjectField(
                    activeSc, "group");
                activeGroup.getChildAt(activeGroup.getChildCount()-1).setVisibility(View.GONE);
            }

            XposedHelpers.callMethod(mVolumePanel, "resetTimeout");
            XposedHelpers.callMethod(mVolumePanel, "updateZenPanelVisible");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void collapseVolumePanel() {
        try {
            mPanelExpanded = false;
            Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(
                    mVolumePanel.getClass(), "STREAMS");
            SparseArray<?> streamControls = (SparseArray<?>) XposedHelpers.getObjectField(
                    mVolumePanel, "mStreamControls");
            int activeStreamType = XposedHelpers.getIntField(mVolumePanel, "mActiveStreamType");

            for (int i = 0; i < streams.length; i++) {
                final int streamType = XposedHelpers.getIntField(streams[i], "streamType");
                if (isValidExpandedPanelControl(streamType)) {
                    Object control = streamControls.get(streamType);
                    if (control != null && streamType != activeStreamType) {
                        ViewGroup group = (ViewGroup) XposedHelpers.getObjectField(control, "group");
                        group.setVisibility(View.GONE);
                        group.getChildAt(group.getChildCount()-1).setVisibility(View.VISIBLE);
                        if (Build.VERSION.SDK_INT < 22) {
                            XposedHelpers.callMethod(mVolumePanel, "updateSlider", control);
                        } else {
                            XposedHelpers.callMethod(mVolumePanel, "updateSlider", control, false);
                        }
                        if (DEBUG) log("hiding slider for stream type " + streamType);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void updateRingerIcon() {
        if (mIconRingerAudibleId == 0) return;
        try {
            int iconResId = mVolumesLinked ? mIconRingerAudibleIdOrig : mIconRingerAudibleId;
            Object[] streams = (Object[]) XposedHelpers.getStaticObjectField(
                    mVolumePanel.getClass(), "STREAMS");
            XposedHelpers.setIntField(streams[1], "iconRes", iconResId);
            SparseArray<?> streamControls = (SparseArray<?>) XposedHelpers.getObjectField(
                    mVolumePanel, "mStreamControls");
            if (streamControls != null) {
                Object sc = streamControls.get(AudioManager.STREAM_RING);
                if (sc != null) {
                    XposedHelpers.setIntField(sc, "iconRes", iconResId);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
