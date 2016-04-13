/*
 * Copyright (C) 2016 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.lollipop.gravitybox.shortcuts;

import java.util.List;

import com.ceco.lollipop.gravitybox.adapters.IIconListAdapterItem;
import com.ceco.lollipop.gravitybox.adapters.IconListAdapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.graphics.drawable.Drawable;

public abstract class AMultiShortcut extends AShortcut {

    protected interface ExtraDelegate {
        void addExtraTo(Intent intent);
    }

    public AMultiShortcut(Context context) {
        super(context);
    }

    @Override
    protected ShortcutIconResource getIconResource() {
        return null;
    }

    protected abstract List<IIconListAdapterItem> getShortcutList();

    @Override
    protected void createShortcut(final CreateShortcutListener listener) {
        final List<IIconListAdapterItem> list = getShortcutList();
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
            .setTitle(getText())
            .setAdapter(new IconListAdapter(mContext, list), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ShortcutItem item = (ShortcutItem) list.get(which);

                    Intent launchIntent = new Intent(mContext, LaunchActivity.class);
                    launchIntent.setAction(ShortcutActivity.ACTION_LAUNCH_ACTION);
                    launchIntent.putExtra(ShortcutActivity.EXTRA_ACTION, getAction());
                    launchIntent.putExtra(ShortcutActivity.EXTRA_ACTION_TYPE, getActionType());
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    item.addExtraTo(launchIntent);

                    Intent intent = new Intent();
                    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
                    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, item.getText());
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, item.getIconResource());

                    AMultiShortcut.this.onShortcutCreated(intent, listener);
                    dialog.dismiss();
                }
            })
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    static class ShortcutItem implements IIconListAdapterItem {
        private Context mContext;
        private int mLabelResId;
        private int mIconResId;
        private ExtraDelegate mExtraDelegate;

        public ShortcutItem(Context context, int labelResId, int iconResId, ExtraDelegate extraDelegate) {
            mContext = context;
            mLabelResId = labelResId;;
            mIconResId = iconResId;
            mExtraDelegate = extraDelegate;
        }

        @Override
        public String getText() {
            return mContext.getString(mLabelResId);
        }

        @Override
        public String getSubText() {
            return null;
        }

        @Override
        public Drawable getIconLeft() {
            return mContext.getResources().getDrawable(mIconResId, null);
        }

        @Override
        public Drawable getIconRight() {
            return null;
        }

        public ShortcutIconResource getIconResource() {
            return ShortcutIconResource.fromContext(mContext, mIconResId);
        }

        public void addExtraTo(Intent intent) {
            if (mExtraDelegate != null) {
                mExtraDelegate.addExtraTo(intent);
            }
        }
    }
}
