/*
 * Copyright (C) 2014 Tungstwenty@xda
 * Copyright (C) 2014 Bruno Martins for GravityBox Project (bgcngm@xda)
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

package com.ceco.lollipop.gravitybox;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.LinkedList;

import android.app.AndroidAppHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PatchFakeId {
    private static final String TAG = "GB:PatchFakeId";

    private static final ThreadLocal<Object> insideCollectCertificates = new ThreadLocal<Object>();

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote() {
        try {
            try {
                // Check for the presence of the fixed method signature
                XposedHelpers.findMethodExact("org.apache.harmony.security.utils.JarUtils", null, "createChain",
                        X509Certificate.class, X509Certificate[].class, boolean.class);
                // If it's found, there's nothing to patch
                log("FakeID vulnerability not found");
                return;
            } catch (Throwable t) {
                log("Patching FakeID vulnerability");
            }

            findAndHookMethod("org.apache.harmony.security.utils.JarUtils", null, "createChain", X509Certificate.class,
                    X509Certificate[].class, new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (insideCollectCertificates.get() == null) {
                        // Not in a relevant place, default to previous behavior
                        return;
                    }

                    try {
                        X509Certificate signer = (X509Certificate) param.args[0];
                        X509Certificate[] candidates = (X509Certificate[]) param.args[1];
                        param.setResult(createChain_fix(signer, candidates));
                    } catch (Throwable t) {
                        // If any exception occurs, send it to the caller as the invocation result
                        // instead of having Xposed fallback to the original (unpatched) method
                        param.setThrowable(t);
                    }
                }
            });

            findAndHookMethod("android.content.pm.PackageParser", null, "collectCertificates",
                    "android.content.pm.PackageParser$Package", int.class, new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // Only apply the fixed method for system services
                    if ("android".equals(AndroidAppHelper.currentPackageName())) {
                        insideCollectCertificates.set("dummy");
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    insideCollectCertificates.set(null);
                }
            });

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static X509Certificate[] createChain_fix(X509Certificate signer, X509Certificate[] candidates) {
        LinkedList chain = new LinkedList();
        chain.add(0, signer);
        // Signer is self-signed
        if (signer.getSubjectDN().equals(signer.getIssuerDN())) {
            return (X509Certificate[]) chain.toArray(new X509Certificate[1]);
        }
        Principal issuer = signer.getIssuerDN();
        X509Certificate issuerCert;
        X509Certificate subjectCert = signer;
        int count = 1;
        while (true) {
            issuerCert = findCert_fix(issuer, candidates, subjectCert, true);
            if (issuerCert == null) {
                break;
            }
            chain.add(issuerCert);
            count++;
            if (issuerCert.getSubjectDN().equals(issuerCert.getIssuerDN())) {
                break;
            }
            issuer = issuerCert.getIssuerDN();
            subjectCert = issuerCert;
        }
        return (X509Certificate[]) chain.toArray(new X509Certificate[count]);
    }

    private static X509Certificate findCert_fix(Principal issuer, X509Certificate[] candidates,
            X509Certificate subjectCert, boolean chainCheck) {
        for (int i = 0; i < candidates.length; i++) {
            if (issuer.equals(candidates[i].getSubjectDN())) {
                if (chainCheck) {
                    try {
                        subjectCert.verify(candidates[i].getPublicKey());
                    } catch (Exception e) {
                        continue;
                    }
                }
                return candidates[i];
            }
        }
        return null;
    }
}