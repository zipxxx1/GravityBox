/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.kitkat.gravitybox;

import com.ceco.kitkat.gravitybox.ledcontrol.QuietHours;
import com.ceco.kitkat.gravitybox.managers.StatusBarIconManager;
import com.ceco.kitkat.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.kitkat.gravitybox.managers.StatusBarIconManager.IconManagerListener;
import com.ceco.kitkat.gravitybox.managers.StatusbarQuietHoursManager.QuietHoursListener;
import com.ceco.kitkat.gravitybox.managers.SysUiManagers;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarQuietHoursView extends ImageView implements  IconManagerListener, QuietHoursListener {

    private QuietHours mQuietHours;
    private Drawable mDrawable;
    private Drawable mDrawableWear;
    private int mCurrentDrawableId = -1; // -1=unset; 0=default; 1=wear
    private int mIconHeightPx;

    public StatusbarQuietHoursView(Context context) throws Throwable {
        super(context);

        Resources res = context.getResources();
        int iconSizeResId = res.getIdentifier("status_bar_icon_size", "dimen", "android");
        mIconHeightPx = iconSizeResId != 0 ? res.getDimensionPixelSize(iconSizeResId) :
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, res.getDisplayMetrics());

        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(mIconHeightPx, mIconHeightPx);
        setLayoutParams(lParams);
        setScaleType(ImageView.ScaleType.CENTER);

        mQuietHours = SysUiManagers.QuietHoursManager.getQuietHours();

        updateVisibility();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
        SysUiManagers.QuietHoursManager.registerListener(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
        SysUiManagers.QuietHoursManager.unregisterListener(this);
    }

    @Override
    public void onQuietHoursChanged() {
        mQuietHours = SysUiManagers.QuietHoursManager.getQuietHours();
        updateVisibility();
    }

    @Override
    public void onTimeTick() {
        updateVisibility();
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            if (colorInfo.coloringEnabled) {
                setColorFilter(colorInfo.iconColor[0], PorterDuff.Mode.SRC_IN);
            } else {
                clearColorFilter();
            }
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaSignalCluster);
        }
    }

    private Drawable getDefaultDrawable() {
        if (mDrawable == null) {
            try {
                mDrawable = Utils.getGbContext(getContext()).getResources()
                    .getDrawable(R.drawable.stat_sys_quiet_hours);
            } catch (Throwable e) { /* ignore */ }
        }
        return mDrawable;
    }

    private Drawable getWearDrawable() {
        if (mDrawableWear == null) {
            try {
                mDrawableWear = Utils.getGbContext(getContext()).getResources()
                    .getDrawable(R.drawable.stat_sys_quiet_hours_wear);
            } catch (Throwable e) { /* ignore */ }
        }
        return mDrawableWear;
    }

    private void setDrawableByMode() {
        final int oldDrawableId = mCurrentDrawableId;
        if (mQuietHours.mode == QuietHours.Mode.WEAR) {
            if (mCurrentDrawableId != 1) {
                setImageDrawable(getWearDrawable());
                mCurrentDrawableId = 1;
            }
        } else if (mCurrentDrawableId != 0) {
            setImageDrawable(getDefaultDrawable());
            mCurrentDrawableId = 0;
        }
        if (oldDrawableId != mCurrentDrawableId) {
            updateLayout();
        }
    }

    private void updateLayout() {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) getLayoutParams();
        lp.width = mCurrentDrawableId == 1 ?
                Math.round((float)mIconHeightPx * 0.85f) : mIconHeightPx;
        setLayoutParams(lp);
    }

    private void updateVisibility() {
        if (mQuietHours != null) {
            setDrawableByMode();
            setVisibility(mQuietHours.showStatusbarIcon && mQuietHours.quietHoursActive() ?
                    View.VISIBLE : View.GONE);
        } else {
            setVisibility(View.GONE);
        }
    }
}
