/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.SettingsUi
import kotlin.math.min
import kotlin.math.roundToInt

/** Full-width gallery preview; image editing preserves the actual keyboard/crop proportions. */
class FittedKeyboardPreviewView(context: Context, private val preview: KeyboardPreviewUi) : FrameLayout(context) {
    private var nativeWidth = 1
    private var nativeHeight = 1
    private var fit = 1f
    init {
        background = SettingsUi.surface(context, R.color.ui_soft)
        clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(preview.root)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        if (preview.compact) {
            val verticalLimit = min(SettingsUi.dp(context, 220), resources.displayMetrics.heightPixels / 3)
            preview.setPresentationSize(width, min((width / 1.75f).roundToInt(), verticalLimit))
        }
        nativeWidth = preview.intrinsicWidth.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        nativeHeight = preview.intrinsicHeight.takeIf { it > 0 } ?: SettingsUi.dp(context, 280)
        fit = width.toFloat() / nativeWidth
        preview.root.measure(MeasureSpec.makeMeasureSpec(nativeWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(nativeHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, resolveSize((nativeHeight * fit).roundToInt(), heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        preview.root.apply {
            layout(0, 0, nativeWidth, nativeHeight)
            pivotX = 0f
            pivotY = 0f
            scaleX = fit
            scaleY = fit
        }
    }
}
