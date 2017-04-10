/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.gm2.gravitybox.quicksettings;

import com.ceco.gm2.gravitybox.ModHwKeys;
import com.ceco.gm2.gravitybox.R;

import android.content.Context;
import android.content.Intent;
import android.view.View;

public class ScreenshotTile extends BasicTile {

    public ScreenshotTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                Intent intent = new Intent(ModHwKeys.ACTION_SCREENSHOT);
                intent.putExtra(ModHwKeys.EXTRA_SCREENSHOT_DELAY_MS, 1000L);
                mContext.sendBroadcast(intent);
            }
        };

        mDrawableId = R.drawable.ic_qs_screenshot;
        mLabel = mGbContext.getString(R.string.qs_tile_screenshot);
        mTileColor = KK_COLOR_ON;
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_screenshot;
    }
}
