/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.fcitx.fcitx5.android.R
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent

inline fun Context.materialTextInput(
    initLayout: TextInputLayout.(TextInputEditText) -> Unit
): Pair<TextInputLayout, TextInputEditText> {
    val density = resources.displayMetrics.density
    val editText = view(::TextInputEditText) {
        includeFontPadding = false
        minHeight = (48 * density).toInt()
        setTextColor(ContextCompat.getColor(context, R.color.ui_text))
        // TextInputLayout owns the outline. A second EditText underline is redundant.
        background = null
        setPadding((14 * density).toInt(), (14 * density).toInt(),
            (14 * density).toInt(), (14 * density).toInt())
    }
    val inputLayout = view(::TextInputLayout) {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxStrokeColor = ContextCompat.getColor(context, R.color.ui_primary)
        defaultHintTextColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ui_muted))
        val radius = 12 * density
        setBoxCornerRadii(radius, radius, radius, radius)
        setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        add(editText, lParams(matchParent, wrapContent))
        initLayout.invoke(this, editText)
    }
    return inputLayout to editText
}

inline fun Ui.materialTextInput(
    initLayout: TextInputLayout.(TextInputEditText) -> Unit
) = ctx.materialTextInput(initLayout)
