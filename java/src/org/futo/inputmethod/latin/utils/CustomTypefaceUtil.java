/*
 * Copyright (C) 2023 FUTO Project
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

package org.futo.inputmethod.latin.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

import androidx.core.content.res.ResourcesCompat;

import org.futo.inputmethod.latin.R;

/**
 * Utility for loading and caching custom typefaces
 */
public class CustomTypefaceUtil {
    
    private static Typeface sSpaceMonoRegular;
    private static Typeface sSpaceMonoBold;
    private static Typeface sSpaceMonoItalic;
    private static Typeface sSpaceMonoBoldItalic;
    
    /**
     * Get the Space Mono typeface with the specified style
     * 
     * @param context The context to use for loading resources
     * @param style The typeface style (Typeface.NORMAL, Typeface.BOLD, etc.)
     * @return The Space Mono typeface
     */
    public static Typeface getSpaceMonoTypeface(Context context, int style) {
        switch (style) {
            case Typeface.BOLD:
                if (sSpaceMonoBold == null) {
                    sSpaceMonoBold = ResourcesCompat.getFont(context, R.font.spacemono_bold);
                }
                return sSpaceMonoBold;
            case Typeface.ITALIC:
                if (sSpaceMonoItalic == null) {
                    sSpaceMonoItalic = ResourcesCompat.getFont(context, R.font.spacemono_italic);
                }
                return sSpaceMonoItalic;
            case Typeface.BOLD_ITALIC:
                if (sSpaceMonoBoldItalic == null) {
                    sSpaceMonoBoldItalic = ResourcesCompat.getFont(context, R.font.spacemono_bold_italic);
                }
                return sSpaceMonoBoldItalic;
            case Typeface.NORMAL:
            default:
                if (sSpaceMonoRegular == null) {
                    sSpaceMonoRegular = ResourcesCompat.getFont(context, R.font.spacemono_regular);
                }
                return sSpaceMonoRegular;
        }
    }
} 