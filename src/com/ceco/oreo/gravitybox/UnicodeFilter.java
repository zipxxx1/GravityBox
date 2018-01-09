/*
 * Copyright (C) 2012-2013 The CyanogenMod Project
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
 *
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

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Attempts to substitute characters that cannot be encoded in the limited
 * GSM 03.38 character set. In many cases this will prevent sending a message
 * containing characters that would switch the message from 7-bit GSM
 * encoding (160 char limit) to 16-bit Unicode encoding (70 char limit).
 */
public class UnicodeFilter {
    private CharsetEncoder gsm =
            Charset.forName("gsm-03.38-2000").newEncoder();

    private Pattern diacritics =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}");

    private boolean mStripNonDecodableOnly;

    public UnicodeFilter(boolean stripNonDecodableOnly) {
        mStripNonDecodableOnly = stripNonDecodableOnly;
    }

    public CharSequence filter(CharSequence source) {
        StringBuilder output = new StringBuilder(source);
        final int sourceLength = source.length();

        for (int i = 0; i < sourceLength; i++) {
            char c = source.charAt(i);

            // Character requires Unicode, try to replace it
            if (!mStripNonDecodableOnly || !gsm.canEncode(c)) {
                String s = String.valueOf(c);

                // Try normalizing the character into Unicode NFKD form and
                // stripping out diacritic mark characters.
                s = Normalizer.normalize(s, Normalizer.Form.NFKD);
                s = diacritics.matcher(s).replaceAll("");

                // Special case characters that don't get stripped by the
                // above technique.
                s = s.replace("Œ", "OE");
                s = s.replace("œ", "oe");
                s = s.replace("�?", "L");
                s = s.replace("ł", "l");
                s = s.replace("�?", "DJ");
                s = s.replace("đ", "dj");
                s = s.replace("Α", "A");
                s = s.replace("Β", "B");
                s = s.replace("Ε", "E");
                s = s.replace("Ζ", "Z");
                s = s.replace("Η", "H");
                s = s.replace("Ι", "I");
                s = s.replace("Κ", "K");
                s = s.replace("Μ", "M");
                s = s.replace("�?", "N");
                s = s.replace("Ο", "O");
                s = s.replace("Ρ", "P");
                s = s.replace("Τ", "T");
                s = s.replace("Υ", "Y");
                s = s.replace("Χ", "X");
                s = s.replace("α", "A");
                s = s.replace("β", "B");
                s = s.replace("γ", "Γ");
                s = s.replace("δ", "Δ");
                s = s.replace("ε", "E");
                s = s.replace("ζ", "Z");
                s = s.replace("η", "H");
                s = s.replace("θ", "Θ");
                s = s.replace("ι", "I");
                s = s.replace("κ", "K");
                s = s.replace("λ", "Λ");
                s = s.replace("μ", "M");
                s = s.replace("ν", "N");
                s = s.replace("ξ", "Ξ");
                s = s.replace("ο", "O");
                s = s.replace("π", "Π");
                s = s.replace("�?", "P");
                s = s.replace("σ", "Σ");
                s = s.replace("τ", "T");
                s = s.replace("υ", "Y");
                s = s.replace("φ", "Φ");
                s = s.replace("χ", "X");
                s = s.replace("ψ", "Ψ");
                s = s.replace("ω", "Ω");
                s = s.replace("ς", "Σ");

                output.replace(i, i + 1, s);
            }
        }

        // Source is a spanned string, so copy the spans from it
        if (source instanceof Spanned) {
            SpannableString spannedoutput = new SpannableString(output);
            TextUtils.copySpansFrom(
                    (Spanned) source, 0, sourceLength, null, spannedoutput, 0);

            return spannedoutput;
        }

        // Source is a vanilla charsequence, so return output as-is
        return output.toString();
    }
}
