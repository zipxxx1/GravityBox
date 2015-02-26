/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.ImageView;

public class CompassTile extends QsTile implements SensorEventListener {
    private final static float ALPHA = 0.97f;

    private boolean mActive = false;
    private Float mNewDegree;

    private SensorManager mSensorManager;
    private Sensor mAccelerationSensor;
    private Sensor mGeomagneticFieldSensor;

    private float[] mAcceleration;
    private float[] mGeomagnetic;

    private ImageView mImage;
    private boolean mListeningSensors;

    public CompassTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, prefs, eventDistributor);

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mAccelerationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGeomagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        setListeningSensors(false);
        mSensorManager = null;
        mImage = null;
    }

    @Override
    public void onCreateTileView(View tileView) throws Throwable {
        super.onCreateTileView(tileView);

        mImage = (ImageView) tileView.findViewById(android.R.id.icon);
    }

    @Override
    public void handleClick() {
        mActive = !mActive;
        refreshState();
        setListeningSensors(mActive);
    }

    @Override
    public boolean handleLongClick(View view) {
        return false;
    }

    private void setListeningSensors(boolean listening) {
        if (listening == mListeningSensors) return;
        mListeningSensors = listening;
        if (mListeningSensors) {
            mSensorManager.registerListener(
                    this, mAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(
                    this, mGeomagneticFieldSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        if (mActive) {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_compass_on);
            if (mNewDegree != null) {
                mState.label = formatValueWithCardinalDirection(mNewDegree);

                float target = 360 - mNewDegree;
                float relative = target - mImage.getRotation();
                if (relative > 180) relative -= 360;

                mImage.setRotation(mImage.getRotation() + relative / 2);
            } else {
                mState.label = mGbContext.getString(R.string.quick_settings_compass_init);
                mImage.setRotation(0);
            }
        } else {
            mState.icon = mGbContext.getDrawable(R.drawable.ic_qs_compass_off);
            mState.label = mGbContext.getString(R.string.quick_settings_compass_off);
            mImage.setRotation(0);
        }

        mState.applyTo(state);
    }

    @Override
    public void setListening(boolean listening) {
        if (!listening) {
            setListeningSensors(false);
            mActive = false;
        }
    }

    private String formatValueWithCardinalDirection(float degree) {
        int cardinalDirectionIndex = (int) (Math.floor(((degree - 22.5) % 360) / 45) + 1) % 8;
        String[] cardinalDirections = mGbContext.getResources().getStringArray(
                R.array.cardinal_directions);

        return mGbContext.getString(R.string.quick_settings_compass_value, degree,
                cardinalDirections[cardinalDirectionIndex]);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (mAcceleration == null) {
                mAcceleration = event.values.clone();
            }
            values = mAcceleration;
        } else {
            // Magnetic field sensor
            if (mGeomagnetic == null) {
                mGeomagnetic = event.values.clone();
            }
            values = mGeomagnetic;
        }

        for (int i = 0; i < 3; i++) {
            values[i] = ALPHA * values[i] + (1 - ALPHA) * event.values[i];
        }

        if (!mActive || !mListeningSensors || mAcceleration == null || mGeomagnetic == null) {
            // Nothing to do at this moment
            return;
        }

        float R[] = new float[9];
        float I[] = new float[9];
        if (!SensorManager.getRotationMatrix(R, I, mAcceleration, mGeomagnetic)) {
            // Rotation matrix couldn't be calculated
            return;
        }

        // Get the current orientation
        float[] orientation = new float[3];
        SensorManager.getOrientation(R, orientation);

        // Convert azimuth to degrees
        mNewDegree = Float.valueOf((float) Math.toDegrees(orientation[0]));
        mNewDegree = (mNewDegree + 360) % 360;

        refreshState();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // noop
    }
}
