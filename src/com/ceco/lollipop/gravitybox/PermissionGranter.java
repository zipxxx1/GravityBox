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

import java.util.Set;

import android.Manifest.permission;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PermissionGranter {
    public static final String TAG = "GB:PermissionGranter";
    public static final boolean DEBUG = false;

    private static final String CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService";
    private static final String CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package";

    private static final String PERM_ACCESS_SURFACE_FLINGER = "android.permission.ACCESS_SURFACE_FLINGER";
    private static final String PERM_WRITE_SETTINGS = "android.permission.WRITE_SETTINGS";
    private static final String PERM_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE";
    private static final String PERM_MODIFY_AUDIO_SETTINGS = "android.permission.MODIFY_AUDIO_SETTINGS";
    private static final String PERM_CAPTURE_AUDIO_OUTPUT = "android.permission.CAPTURE_AUDIO_OUTPUT";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initAndroid(final ClassLoader classLoader) {
        try {
            final Class<?> pmServiceClass = XposedHelpers.findClass(CLASS_PACKAGE_MANAGER_SERVICE, classLoader);

            XposedHelpers.findAndHookMethod(pmServiceClass, "grantPermissionsLPw",
                    CLASS_PACKAGE_PARSER_PACKAGE, boolean.class, String.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");

                    // GravityBox
                    if (GravityBox.PACKAGE_NAME.equals(pkgName)) {
                        final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                        final Set<String> grantedPerms =
                                (Set<String>) XposedHelpers.getObjectField(extras, "grantedPermissions");
                        final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                        final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                        // Add android.permission.ACCESS_SURFACE_FLINGER needed by screen recorder
                        if (!grantedPerms.contains(PERM_ACCESS_SURFACE_FLINGER)) {
                            final Object pAccessSurfaceFlinger = XposedHelpers.callMethod(permissions, "get",
                                    PERM_ACCESS_SURFACE_FLINGER);
                            grantedPerms.add(PERM_ACCESS_SURFACE_FLINGER);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pAccessSurfaceFlinger, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log("Permission added: " + pAccessSurfaceFlinger);
                        }

                        // Add android.permission.WRITE_SETTINGS needed by screen recorder (toggle Show Touches on/off)
                        if (!grantedPerms.contains(PERM_WRITE_SETTINGS)) {
                            final Object pWriteSettings = XposedHelpers.callMethod(permissions, "get",
                                    PERM_WRITE_SETTINGS);
                            grantedPerms.add(PERM_WRITE_SETTINGS);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pWriteSettings, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pWriteSettings);
                        }

                        // Add android.permission.MODIFY_AUDIO_SETTINGS needed by screen recorder
                        if (!grantedPerms.contains(PERM_MODIFY_AUDIO_SETTINGS)) {
                            final Object pAccessAudioSettings = XposedHelpers.callMethod(permissions, "get",
                                    PERM_MODIFY_AUDIO_SETTINGS);
                            grantedPerms.add(PERM_MODIFY_AUDIO_SETTINGS);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pAccessAudioSettings, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pAccessAudioSettings);
                        }

                        // Add android.permission.CAPTURE_AUDIO_OUTPUT needed by screen recorder
                        if (!grantedPerms.contains(PERM_CAPTURE_AUDIO_OUTPUT)) {
                            final Object pCaptureAudioOutput = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CAPTURE_AUDIO_OUTPUT);
                            grantedPerms.add(PERM_CAPTURE_AUDIO_OUTPUT);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCaptureAudioOutput, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCaptureAudioOutput);
                        }

                        if (DEBUG) {
                            log("List of permissions: ");
                            for (String perm : grantedPerms) {
                                log(pkgName + ": " + perm);
                            }
                        }
                    }

                    // SystemUI
                    if (!Utils.hasLenovoVibeUI() && pkgName.equals("com.android.systemui")) {
                        final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                        final Object sharedUser = XposedHelpers.getObjectField(extras, "sharedUser");
                        final Set<String> grantedPerms =
                                (Set<String>) XposedHelpers.getObjectField(sharedUser, "grantedPermissions");
                        final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                        final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                        // Add android.permission.CHANGE_NETWORK_STATE needed by Usb Tethering Tile
                        if (!grantedPerms.contains(PERM_CHANGE_NETWORK_STATE)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CHANGE_NETWORK_STATE);
                            grantedPerms.add(PERM_CHANGE_NETWORK_STATE);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }
                        // Add READ_CALL_LOG needed by LockscreenAppBar to show badge for missed calls
                        if (!grantedPerms.contains(permission.READ_CALL_LOG)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    permission.READ_CALL_LOG);
                            grantedPerms.add(permission.READ_CALL_LOG);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }
                        // Add ACCESS_FINE_LOCATION needed by GpsStatusMonitor
                        if (!grantedPerms.contains(permission.ACCESS_FINE_LOCATION)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    permission.ACCESS_FINE_LOCATION);
                            grantedPerms.add(permission.ACCESS_FINE_LOCATION);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }
                        // Add permissions needed by Visualizer
                        if (!grantedPerms.contains(permission.RECORD_AUDIO)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    permission.RECORD_AUDIO);
                            grantedPerms.add(permission.RECORD_AUDIO);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }
                        if (!grantedPerms.contains(permission.MODIFY_AUDIO_SETTINGS)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    permission.MODIFY_AUDIO_SETTINGS);
                            grantedPerms.add(permission.MODIFY_AUDIO_SETTINGS);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }

                        if (DEBUG) {
                            log("List of permissions: ");
                            for (String perm : grantedPerms) {
                                log(pkgName + ": " + perm);
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
