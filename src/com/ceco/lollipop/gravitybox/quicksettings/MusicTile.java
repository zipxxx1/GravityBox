/*
 * Copyright (C) 2013 The SlimRoms Project
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

package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

public class MusicTile extends BasicTile {

    private boolean mActive = false;
    private boolean mClientIdLost = true;
    private int mMusicTileMode = 3;
    private Metadata mMetadata = new Metadata();

    private RemoteController mRemoteController;
    private Object mAudioService = null;

    public MusicTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mRemoteController = new RemoteController(context, mRCClientUpdateListener);
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.registerRemoteController(mRemoteController);
        mRemoteController.setArtworkConfiguration(100, 80);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_NEXT);
                return true;
            }
        };
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_music;
    }

    @Override
    protected void updateTile() {
        final ImageView background =
                (ImageView) mTile.findViewById(R.id.background);
        if (background != null) {
            if (mMetadata.bitmap != null && (mMusicTileMode == 1 || mMusicTileMode == 3)) {
                background.setImageDrawable(new BitmapDrawable(mResources, mMetadata.bitmap));
                background.setColorFilter(
                    Color.rgb(123, 123, 123), android.graphics.PorterDuff.Mode.MULTIPLY);
            } else {
                background.setImageDrawable(null);
                background.setColorFilter(null);
            }
        }
        if (mActive) {
            mDrawableId = R.drawable.ic_qs_media_pause;
            mLabel = mMetadata.trackTitle != null && mMusicTileMode > 1
                ? mMetadata.trackTitle : mGbContext.getString(R.string.quick_settings_music_pause);
        } else {
            mDrawableId = R.drawable.ic_qs_media_play;
            mLabel = mGbContext.getString(R.string.quick_settings_music_play);
        }

        super.updateTile();
    }

    private void playbackStateUpdate(int state) {
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mActive) {
            mActive = active;
            updateResources();
        }
    }

    private void sendMediaButtonClick(int keyCode) {
        if (!mClientIdLost) {
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } else {
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent key = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            dispatchMediaKeyWithWakeLockToAudioService(key);
            dispatchMediaKeyWithWakeLockToAudioService(
                KeyEvent.changeAction(key, KeyEvent.ACTION_UP));
        }
    }

    private void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        mAudioService = getAudioService();
        if (mAudioService != null) {
            try {
                XposedHelpers.callMethod(mAudioService, "dispatchMediaKeyEventUnderWakelock", event);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    private Object getAudioService() {
        if (mAudioService == null) {
            try {
                Class<?> iasStubClass = XposedHelpers.findClass("android.media.IAudioService.Stub", null);
                Class<?> smClass = XposedHelpers.findClass("android.os.ServiceManager", null);
                mAudioService = XposedHelpers.callStaticMethod(iasStubClass, "asInterface",
                        XposedHelpers.callStaticMethod(smClass, "checkService", Context.AUDIO_SERVICE));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
        return mAudioService;
    }

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {

        private String mCurrentTrack = null;
        private Bitmap mCurrentBitmap = null;

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mCurrentTrack = null;
                mCurrentBitmap = null;
                mActive = false;
                mClientIdLost = true;
                updateResources();
            }
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            mMetadata.bitmap = data.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK,
                    mMetadata.bitmap);
            mClientIdLost = false;
            if ((mMetadata.trackTitle != null
                    && !mMetadata.trackTitle.equals(mCurrentTrack))
                || (mMetadata.bitmap != null && !mMetadata.bitmap.sameAs(mCurrentBitmap))) {
                mCurrentTrack = mMetadata.trackTitle;
                mCurrentBitmap = mMetadata.bitmap;
                updateResources();
            }
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        private String trackTitle;
        private Bitmap bitmap;

        public void clear() {
            trackTitle = null;
            bitmap = null;
        }
    }
}
