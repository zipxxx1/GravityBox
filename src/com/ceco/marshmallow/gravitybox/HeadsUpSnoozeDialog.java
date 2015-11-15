/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.marshmallow.gravitybox;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TimePicker;
import com.ceco.marshmallow.gravitybox.R;

public class HeadsUpSnoozeDialog extends Dialog implements View.OnClickListener {

    private Context mGbContext;
    private View mView;
    private TimePicker mTimePicker;
    private TextView mMessage;
    private CheckBox mAllApps;
    private Button mBtnCancel;
    private Button mBtnOk;
    private String mPkgName;
    private String mAppName;
    HeadsUpSnoozeTimerSetListener mListener;

    public interface HeadsUpSnoozeTimerSetListener {
        void onHeadsUpSnoozeTimerSet(String pkgName, long millis); 
    }

    @SuppressLint("InflateParams")
    public HeadsUpSnoozeDialog(Context context, Context gbContext, HeadsUpSnoozeTimerSetListener listener) {
        super(context);

        mGbContext = gbContext;
        mListener = listener;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        setCanceledOnTouchOutside(true);

        LayoutInflater inflater = LayoutInflater.from(gbContext);
        mView = inflater.inflate(R.layout.dialog_headsup_snooze, null);
        mTimePicker = (TimePicker) mView.findViewById(R.id.timePicker);
        mTimePicker.setIs24HourView(true);
        mMessage = (TextView) mView.findViewById(R.id.message);
        mAllApps = (CheckBox) mView.findViewById(R.id.chkAllApps);
        mBtnCancel = (Button) mView.findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(this);
        mBtnOk = (Button) mView.findViewById(R.id.btnOk);
        mBtnOk.setOnClickListener(this);

        setContentView(mView);
    }

    public void setPackageName(String pkgName) {
        mPkgName = pkgName;
        mAppName = mPkgName;
        try {
            PackageManager pm = getContext().getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0);
            mAppName = (String) appInfo.loadLabel(pm);
        } catch (NameNotFoundException e) { }
        mMessage.setText(String.format(mGbContext.getString(R.string.headsup_snooze_app), mAppName));
    }

    public String getPackageName() {
        return mPkgName;
    }

    public String getAppName() {
        return mAppName;
    }

    @Override
    public void show() {
        mTimePicker.setCurrentHour(1);
        mTimePicker.setCurrentMinute(0);
        mAllApps.setChecked(false);
        super.show();
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnOk && mListener != null) {
            long millis = (mTimePicker.getCurrentHour()*60+mTimePicker.getCurrentMinute())*60000;
            mListener.onHeadsUpSnoozeTimerSet(mAllApps.isChecked() ? null : mPkgName, millis);
        }
        dismiss();
    }
}
