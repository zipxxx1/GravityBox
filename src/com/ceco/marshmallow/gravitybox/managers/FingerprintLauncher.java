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
package com.ceco.marshmallow.gravitybox.managers;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.ceco.marshmallow.gravitybox.Utils;
import com.ceco.marshmallow.gravitybox.managers.AppLauncher.AppInfo;
import com.ceco.marshmallow.gravitybox.BroadcastSubReceiver;
import com.ceco.marshmallow.gravitybox.GravityBoxSettings;
import com.ceco.marshmallow.gravitybox.R;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.hardware.fingerprint.FingerprintManager.CryptoObject;
import android.os.CancellationSignal;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Toast;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class FingerprintLauncher implements BroadcastSubReceiver {
    private static final String TAG = "GB:FingerprintLauncher";
    private static final boolean DEBUG = false;
    private static final String KEY_NAME = "gravitybox.fingeprint.launcher";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private Context mContext;
    private Context mGbContext;
    private XSharedPreferences mPrefs;
    private FingerprintManager mFpManager;
    private FingerprintHandler mFpHandler;
    private boolean mIgnoreAuth;
    private String mApp;
    private AppInfo mAppInfo;

    public FingerprintLauncher(Context ctx, XSharedPreferences prefs) throws Throwable {
        if (ctx == null)
            throw new IllegalArgumentException("Context cannot be null");

        mContext = ctx;
        mGbContext = Utils.getGbContext(mContext);
        mPrefs = prefs;
        mAppInfo = SysUiManagers.AppLauncher.createAppInfo();

        mIgnoreAuth = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_IGNORE_AUTH, false);
        mApp = mPrefs.getString(GravityBoxSettings.PREF_KEY_FINGERPRINT_LAUNCHER_APP, null);

        initFingerprintManager();
    }

    private void initFingerprintManager() throws Throwable {
        mFpManager = (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);
        if (!mFpManager.isHardwareDetected())
            throw new IllegalStateException("Fingerprint hardware not present");

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyStore.load(null);
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_NAME, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build());
        keyGenerator.generateKey();

        Cipher cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/" +
                KeyProperties.BLOCK_MODE_CBC + "/" +
                KeyProperties.ENCRYPTION_PADDING_PKCS7);
        SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        mFpHandler = new FingerprintHandler(cipher);

        if (DEBUG) log("Fingeprint manager initialized");
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            onUserPresentChanged(false);
        } else if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
            onUserPresentChanged(true);
        } else if (intent.getAction().equals(GravityBoxSettings.ACTION_FPL_SETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_FPL_APP)) {
                mApp = intent.getStringExtra(GravityBoxSettings.EXTRA_FPL_APP);
                mAppInfo.initAppInfo(mApp);
            }
            if (intent.hasExtra(GravityBoxSettings.EXTRA_FPL_IGNORE_AUTH)) {
                mIgnoreAuth = intent.getBooleanExtra(GravityBoxSettings.EXTRA_FPL_IGNORE_AUTH, false);
            }
        }
    }

    private void onUserPresentChanged(boolean present) {
        if (DEBUG) log("onUserPresentChanged: present=" + present);
        if (present) {
            mFpHandler.startListening();
        } else {
            mFpHandler.stopListening();
        }
    }

    private void startActivity() {
        if (DEBUG) log("starting activity");
        try {
            if (mAppInfo.getValue() == null && mApp != null) {
                mAppInfo.initAppInfo(mApp);
            }
            if (mAppInfo.getIntent() != null) {
                SysUiManagers.AppLauncher.startActivity(mContext, mAppInfo.getIntent());
            } else {
                Toast.makeText(mContext, String.format("%s\n%s",
                        TAG, mGbContext.getString(R.string.fingerprint_no_app)),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            log("Error starting activity: " + t.getMessage());
        }
    }

    private class FingerprintHandler extends FingerprintManager.AuthenticationCallback {
        private CryptoObject mCryptoObject;
        private CancellationSignal mCancellationSignal;
        private Handler mHandler;

        private FingerprintHandler(Cipher cipher) {
            mCryptoObject = new CryptoObject(cipher);
            mHandler = new Handler();
        }

        private void startListening() {
            mCancellationSignal = new CancellationSignal();
            mFpManager.authenticate(mCryptoObject, mCancellationSignal, 0, this, null);
            if (DEBUG) log("FingerprintHandler: listening started");
        }

        private void stopListening() {
            mHandler.removeCallbacks(mRestartListeningRunnable);
            if (mCancellationSignal != null && !mCancellationSignal.isCanceled()) {
                mCancellationSignal.cancel();
                if (DEBUG) log("FingerprintHandler: listening stopped");
            }
            mCancellationSignal = null;
        }

        private void restartListeningDelayed(long delayMs) {
            if (DEBUG) log("Restarting listening in " + delayMs + "ms");

            stopListening();
            mHandler.postDelayed(mRestartListeningRunnable, delayMs);
        }

        private Runnable mRestartListeningRunnable = new Runnable() {
            @Override
            public void run() {
                startListening();
            }
        };

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            if (DEBUG) log("onAuthenticationError: " + errMsgId + " - " + errString);

            switch (errMsgId) {
                case FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE:
                case FingerprintManager.FINGERPRINT_ERROR_CANCELED:
                    Toast.makeText(mContext, String.format("%s\n%s",
                            TAG, mGbContext.getString(R.string.fingerprint_sensor_unavail)),
                            Toast.LENGTH_SHORT).show();
                    restartListeningDelayed(10000);
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_UNABLE_TO_PROCESS:
                    restartListeningDelayed(3000);
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_TIMEOUT:
                    restartListeningDelayed(2000);
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_LOCKOUT:
                    restartListeningDelayed(35000);
                    Toast.makeText(mContext, String.format("%s\n%s",
                            TAG, mGbContext.getString(R.string.fingerprint_sensor_locked)),
                            Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            if (DEBUG) log("onAuthenticationHelp: " + helpMsgId + " - " + helpString);

            if (mIgnoreAuth) {
                startActivity();
            } else {
                Toast.makeText(mContext, TAG + "\n" + helpString,Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onAuthenticationFailed() {
            if (DEBUG) log("onAuthenticationFailed");

            if (mIgnoreAuth) {
                startActivity();
            } else {
                Toast.makeText(mContext, String.format("%s\n%s",
                        TAG, mGbContext.getString(R.string.fingerprint_auth_failed)),
                            Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            startActivity();
            restartListeningDelayed(1000);
        }
    }
}
