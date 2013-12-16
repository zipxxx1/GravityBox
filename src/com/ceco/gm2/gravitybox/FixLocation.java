/*
 * Copyright (C) 2013 Bruno Martins for GravityBox Project (bgcngm@XDA)
 * Copyright (C) 2011-2013 c:geo contributors
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

package com.ceco.gm2.gravitybox;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FixLocation {
    private static final String TAG = "GB:FixLocationIssue";
    private static final String CLASS_LOCATION = "android.location.Location";
    private static final boolean DEBUG = false;

    private static final double DEG_TO_RAD = Math.PI / 180;
    private static final double RAD_TO_DEG = 180 / Math.PI;
    private static final float EARTH_RADIUS = 6371.0f;

    public static void initZygote() {
        if (DEBUG) XposedBridge.log(TAG + ": init");

        try {
            final Class<?> locationClass = XposedHelpers.findClass(CLASS_LOCATION, null);

            XposedHelpers.findAndHookMethod(locationClass, "computeDistanceAndBearing",
                    double.class, double.class, double.class, double.class, float[].class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    double lat1 = (Double) param.args[0];
                    double lon1 = (Double) param.args[1];
                    double lat2 = (Double) param.args[2];
                    double lon2 = (Double) param.args[3];
                    float distance = (float) (getDistance(lat1, lon1, lat2, lon2)); 

                    ((float[]) param.args[4])[0] = distance;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // Calculate distance in meters (workaround for Android 4.2.1 JIT bug)
    private static double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6372.8; // for haversine use R = 6372.8 km instead of 6371 km
        double dLat = toRadians(lat2 - lat1);
        double dLon = toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        //double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        //return R * c * 1000;
        // simplify haversine:
        return (2 * earthRadius * 1000 * Math.asin(Math.sqrt(a)));
    }

    private static double toRadians(double angdeg) {
        return angdeg * DEG_TO_RAD;
    }
}