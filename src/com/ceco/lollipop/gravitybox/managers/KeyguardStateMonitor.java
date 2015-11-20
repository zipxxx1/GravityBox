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
package com.ceco.lollipop.gravitybox.managers;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;

public class KeyguardStateMonitor {
    public static final String TAG="GB:KeyguardStateMonitor";
    public static final String CLASS_KG_UPDATE_MONITOR =
            "com.android.keyguard.KeyguardUpdateMonitor";
    public static final String CLASS_LOCK_PATTERN_UTILS =
            "com.android.internal.widget.LockPatternUtils";
    private static boolean DEBUG = false;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public interface Listener {
        void onKeyguardStateChanged();
    }

    private Context mContext;
    private boolean mIsShowing;
    private boolean mIsLocked;
    private boolean mIsTrustManaged;
    private Object mUpdateMonitor;
    private Object mLockPatternUtils;
    private Object mMediator;
    private List<Listener> mListeners = new ArrayList<>();

    protected KeyguardStateMonitor(Context context) {
        mContext = context;
        createLockPatternUtils();
        createHooks();
    }

    private void createLockPatternUtils() {
        try {
            Constructor<?> c = XposedHelpers.findConstructorExact(CLASS_LOCK_PATTERN_UTILS,
                    mContext.getClassLoader(), Context.class);
            mLockPatternUtils = c.newInstance(mContext);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public void setMediator(Object mediator) {
        mMediator = mediator;
    }

    private void createHooks() {
        try {
            ClassLoader cl = mContext.getClassLoader();
            Class<?> updateMonitorClass = XposedHelpers.findClass(CLASS_KG_UPDATE_MONITOR, cl);

            XposedBridge.hookAllConstructors(updateMonitorClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mUpdateMonitor = param.thisObject;
                }
            });

            XposedHelpers.findAndHookMethod(updateMonitorClass, "handleKeyguardVisibilityChanged",
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    boolean showing = (int) param.args[0] == 1;
                    if (showing != mIsShowing) {
                        mIsShowing = showing;
                        notifyStateChanged();
                    }
                }
            });

            XposedHelpers.findAndHookMethod(updateMonitorClass, "onTrustChanged",
                boolean.class, int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    boolean locked = !(boolean) XposedHelpers.callMethod(param.thisObject,
                            "getUserHasTrust", getCurrentUserId());
                    if (locked != mIsLocked) {
                        mIsLocked = locked;
                        notifyStateChanged();
                    }
                }
            });

            XposedHelpers.findAndHookMethod(updateMonitorClass, "onTrustManagedChanged",
                boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    boolean managed = (boolean) XposedHelpers.callMethod(param.thisObject,
                            "getUserTrustIsManaged", getCurrentUserId());
                    if (managed != mIsTrustManaged) {
                        mIsTrustManaged = managed;
                        notifyStateChanged();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void notifyStateChanged() {
        if (DEBUG) log("showing:" + mIsShowing + "; secured:" + isSecured() + 
                "; locked:" + isLocked() + "; trustManaged:" + mIsTrustManaged);
        synchronized (mListeners) {
            for (Listener l : mListeners) {
                l.onKeyguardStateChanged();
            }
        }
    }

    public void registerListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    public void unregisterListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (mListeners.contains(l)) {
                mListeners.remove(l);
            }
        }
    }

    public int getCurrentUserId() {
        if (mLockPatternUtils == null) return 0;
        try {
            return (int) XposedHelpers.callMethod(mLockPatternUtils, "getCurrentUser");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return 0;
        }
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    public boolean isSecured() {
        if (mLockPatternUtils == null) return false;
        try {
            return (boolean) XposedHelpers.callMethod(mLockPatternUtils, "isSecure");
        } catch (Throwable t) {
            XposedBridge.log(t);
            return false;
        }
    }

    public boolean isLocked() {
        return (isSecured() && mIsLocked);
    }

    public boolean isTrustManaged() {
        return mIsTrustManaged;
    }

    public void dismissKeyguard() {
        if (mMediator != null) {
            try {
                XposedHelpers.callMethod(mMediator, "dismiss");
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }
}
