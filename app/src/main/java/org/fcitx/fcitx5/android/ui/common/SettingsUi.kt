/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Shared View styling. Noto Sans SC / CJK SC: SIL OFL 1.1.
 */
package org.fcitx.fcitx5.android.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R

object SettingsUi {
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()
    fun color(context: Context, resource: Int) = ContextCompat.getColor(context, resource)

    fun surface(context: Context, resource: Int = R.color.ui_surface, radius: Int = 16) =
        GradientDrawable().apply {
            setColor(color(context, resource))
            cornerRadius = dp(context, radius).toFloat()
        }

    fun clickableSurface(context: Context) = RippleDrawable(
        ColorStateList.valueOf(color(context, R.color.ui_primary) and 0x00ffffff or 0x16000000),
        surface(context), surface(context)
    )

    fun text(context: Context, value: CharSequence, size: Float = 16f, muted: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color(context, if (muted) R.color.ui_muted else R.color.ui_text))
            includeFontPadding = false
        }

    fun section(context: Context, @StringRes title: Int) = text(context, context.getText(title), 13f).apply {
        setTextColor(color(context, R.color.ui_primary))
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(context, 4), dp(context, 22), dp(context, 4), dp(context, 10))
    }

    fun destination(
        context: Context, @StringRes title: Int, @DrawableRes icon: Int,
        @StringRes summary: Int? = null, action: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, if (summary == null) 64 else 80)
        setPadding(dp(context, 16), dp(context, 12), dp(context, 12), dp(context, 12))
        background = clickableSurface(context)
        isFocusable = true
        contentDescription = listOfNotNull(context.getString(title), summary?.let(context::getString)).joinToString(". ")
        setOnClickListener { action() }
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(color(context, R.color.ui_primary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)).apply { marginEnd = dp(context, 14) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(text(context, context.getText(title)), LinearLayout.LayoutParams(-1, -2))
            summary?.let {
                addView(text(context, context.getText(it), 13f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 4) })
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_keyboard_arrow_right_24)
            imageTintList = ColorStateList.valueOf(color(context, R.color.ui_muted))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)))
    }

    fun applyBottomInset(view: View, bottom: Int = 16) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            view.setPadding(left, top, right, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom + dp(view.context, bottom))
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** Draw a single connected surface per preference section, including recycled rows. */
    class PreferenceSurfaces(context: Context) : RecyclerView.ItemDecoration() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(context, R.color.ui_surface) }
        private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(context, R.color.ui_line); strokeWidth = dp(context, 1).toFloat() / 2 }
        private val radius = dp(context, 16).toFloat()
        private val inset = dp(context, 16).toFloat()
        private val bounds = RectF()

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val count = parent.adapter?.itemCount ?: return
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                // Our category layout is a TextView; rows are LinearLayouts. Only
                // inspect attached neighbours, using RecyclerView's public API.
                if (position == RecyclerView.NO_POSITION || child is TextView) continue
                val first = position == 0 || parent.findViewHolderForAdapterPosition(position - 1)?.itemView is TextView
                val last = position == count - 1 || parent.findViewHolderForAdapterPosition(position + 1)?.itemView is TextView
                bounds.set(child.left.toFloat(), child.top + child.translationY, child.right.toFloat(), child.bottom + child.translationY)
                canvas.drawRoundRect(bounds, radius, radius, fill)
                if (!first) canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.centerY(), fill)
                if (!last) {
                    canvas.drawRect(bounds.left, bounds.centerY(), bounds.right, bounds.bottom, fill)
                    canvas.drawLine(bounds.left + inset, bounds.bottom, bounds.right - inset, bounds.bottom, divider)
                }
            }
        }
    }
}
