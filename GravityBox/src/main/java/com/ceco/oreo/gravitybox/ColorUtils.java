/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.oreo.gravitybox;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.TypedValue;

public class ColorUtils {

    public static int alphaPercentToInt(int percentAlpha) {
        percentAlpha = Math.min(Math.max(percentAlpha, 0), 100);
        float alpha = (float)percentAlpha / 100f;
        return (alpha == 0 ? 255 : (int)(1-alpha * 255));
    }

    public static int getColorFromStyleAttr(Context ctx, int attrId) {
        if (attrId == 0)
            return 0;

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = ctx.getTheme();
        theme.resolveAttribute(attrId, typedValue, true);
        TypedArray arr = ctx.obtainStyledAttributes(
                typedValue.data, new int[] { attrId });
        int color = arr.getColor(0, -1);
        arr.recycle();
        return color;
    }
}
