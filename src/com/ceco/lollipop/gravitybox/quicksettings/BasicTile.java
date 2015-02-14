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

package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.R;
import com.ceco.lollipop.gravitybox.ModQuickSettings.TileLayout;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

// Abstract Basic Tile definition
// Tile layout should consist of 2 View elements: ImageView and TextView
public abstract class BasicTile extends AQuickSettingsTile {

    protected ImageView mImageView;
    protected TextView mTextView;
    protected int mDrawableId;
    protected String mLabel;

    public BasicTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);
    }

    // each basic tile clone must provide its own layout identified by unique ID
    protected abstract int onGetLayoutId();

    // basic tile clone can override this to supply custom ImageView ID
    protected int onGetImageViewId() {
        return R.id.image;
    }

    // basic tile clone can override this to supply custom TextView ID
    protected int onGetTextViewId() {
        return R.id.text;
    }

    @Override
    protected void onTileCreate() {
        LayoutInflater inflater = LayoutInflater.from(mGbContext);
        inflater.inflate(onGetLayoutId(), mTile);
        mImageView = (ImageView) mTile.findViewById(onGetImageViewId());
        mTextView = (TextView) mTile.findViewById(onGetTextViewId());
    }

    @Override
    protected void updateTile() {
        mTextView.setText(mLabel);
        mImageView.setImageResource(mDrawableId);
    }

    @Override
    protected void onLayoutUpdated(TileLayout tileLayout) {
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, tileLayout.textSize);
        mTextView.setAllCaps(tileLayout.labelStyle == TileLayout.LabelStyle.ALLCAPS);
        mTextView.setVisibility(tileLayout.labelStyle == TileLayout.LabelStyle.HIDDEN ?
                View.GONE : View.VISIBLE);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mImageView.getLayoutParams();
        lp.width = lp.height = tileLayout.imageSize;
        lp.topMargin = tileLayout.imageMarginTop;
        lp.bottomMargin = tileLayout.imageMarginBottom;
        mImageView.setLayoutParams(lp);
        mImageView.requestLayout();
    }
}
