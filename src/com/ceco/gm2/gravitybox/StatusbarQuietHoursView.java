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
package com.ceco.gm2.gravitybox;

import com.ceco.gm2.gravitybox.managers.StatusBarIconManager;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.gm2.gravitybox.managers.StatusBarIconManager.IconManagerListener;
import com.ceco.gm2.gravitybox.managers.SysUiManagers;
import com.ceco.gm2.gravitybox.managers.StatusbarQuietHoursManager.QuietHoursListener;
import com.ceco.gm2.gravitybox.ledcontrol.QuietHours;

import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarQuietHoursView extends ImageView implements IconManagerListener, QuietHoursListener {

    private QuietHours mQuietHours;
    private Drawable[] mDrawables;

    public StatusbarQuietHoursView(Context context) {
        super(context);

        Resources res = context.getResources();
        int iconSizeResId = res.getIdentifier("status_bar_icon_size", "dimen", "android");
        int iconSize = iconSizeResId != 0 ? res.getDimensionPixelSize(iconSizeResId) :
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, res.getDisplayMetrics());

        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        setLayoutParams(lParams);
        setScaleType(ImageView.ScaleType.CENTER);

        mQuietHours = SysUiManagers.QuietHoursManager.getQuietHours();

        try {
            Context gbContext = context.createPackageContext(GravityBox.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
            mDrawables = new Drawable[3];
            mDrawables[0] = gbContext.getResources().getDrawable(R.drawable.stat_sys_quiet_hours_jb);
            mDrawables[1] = gbContext.getResources().getDrawable(R.drawable.stat_sys_quiet_hours_kk);
            mDrawables[2] = gbContext.getResources().getDrawable(R.drawable.stat_sys_quiet_hours);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

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
        if (mDrawables == null) return;

        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0 ||
                (flags & StatusBarIconManager.FLAG_ICON_STYLE_CHANGED) != 0) {
            int index = colorInfo.coloringEnabled ? colorInfo.iconStyle : 2;
            setImageDrawable(mDrawables[index]);
            if (colorInfo.coloringEnabled) {
                setColorFilter(colorInfo.iconColor[0], PorterDuff.Mode.SRC_IN);
            } else {
                clearColorFilter();
            }
        }
    }

    private void updateVisibility() {
        if (mQuietHours != null) {
            setVisibility(mQuietHours.showStatusbarIcon && mQuietHours.quietHoursActive() ?
                    View.VISIBLE : View.GONE);
        } else {
            setVisibility(View.GONE);
        }
    }
}
