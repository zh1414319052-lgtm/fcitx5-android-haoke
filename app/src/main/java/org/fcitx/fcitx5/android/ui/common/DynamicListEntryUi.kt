/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.common

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import android.content.res.ColorStateList
import org.fcitx.fcitx5.android.R
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDimenPxSize
import splitties.resources.styledDrawable
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.checkBox
import splitties.views.dsl.core.imageButton
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.setPaddingDp
import splitties.views.textAppearance

class DynamicListEntryUi(override val ctx: Context) : Ui {

    val handleImage = imageView {
        imageDrawable = drawable(R.drawable.ic_baseline_drag_handle_24)!!.apply {
            setTint(styledColor(android.R.attr.colorAccent))
        }
        setPaddingDp(3, 0, 3, 0)
        contentDescription = ctx.getText(R.string.ui_reorder_item)
    }

    val multiselectCheckBox = checkBox()

    val checkBox = checkBox()

    val nameText = textView {
        textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        textSize = 16f
        includeFontPadding = false
        setTextColor(SettingsUi.color(ctx, R.color.ui_text))
    }

    val secondaryText = SettingsUi.text(ctx, "", 12f, true).apply { visibility = View.GONE }

    val editButton = imageButton {
        contentDescription = ctx.getText(R.string.ui_edit_item)
        background = styledDrawable(android.R.attr.selectableItemBackground)
        imageDrawable = drawable(R.drawable.ic_baseline_edit_24)!!.apply {
            setTint(styledColor(android.R.attr.colorControlNormal))
        }
    }

    val settingsButton = imageButton {
        contentDescription = ctx.getText(R.string.ui_configure_item)
        background = styledDrawable(android.R.attr.selectableItemBackground)
        imageDrawable = drawable(R.drawable.ic_baseline_settings_24)!!.apply {
            setTint(styledColor(android.R.attr.colorControlNormal))
        }
    }

    override val root: View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(matchParent, wrapContent).apply { bottomMargin = dp(8) }
        background = SettingsUi.clickableSurface(ctx)
        minimumHeight = dp(72)
        setPaddingDp(8, 8, 8, 8)
        addView(handleImage, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(multiselectCheckBox, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(checkBox, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingDp(8, 4, 4, 4)
            addView(nameText, LinearLayout.LayoutParams(-1, -2))
            addView(secondaryText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(editButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
}
