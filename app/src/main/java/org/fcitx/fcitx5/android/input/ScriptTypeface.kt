/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android.input

import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.fcitx.fcitx5.android.R

/** Both bundled families use SIL OFL 1.1; never rely on an OEM's missing Sinhala fallback. */
object ScriptTypeface {
    fun apply(view: TextView, text: CharSequence) {
        view.typeface = ResourcesCompat.getFont(view.context,
            if (text.any { it in '\u0d80'..'\u0dff' }) R.font.noto_sans_sinhala else R.font.noto_sans_sc)
    }
}
