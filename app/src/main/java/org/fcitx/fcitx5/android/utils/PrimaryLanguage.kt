/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.utils

import java.util.Locale

/** UI language follows only the first system language, never a later fallback. */
object PrimaryLanguage {
    fun select(primaryTag: String?): Locale =
        if (primaryTag?.replace('_', '-')?.substringBefore('-')?.lowercase(Locale.ROOT) == "zh")
            Locale.SIMPLIFIED_CHINESE
        else Locale.ENGLISH
}
