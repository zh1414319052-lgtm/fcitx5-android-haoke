/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.ui.common.SettingsUi
import splitties.views.dsl.core.Ui

class ThemeThumbnailUi(override val ctx: Context) : Ui {
    enum class State { Normal, Selected, LightMode, DarkMode }
    private fun dp(n: Int) = SettingsUi.dp(ctx,n)
    private var currentTheme: Theme? = null
    private val preview = MiniKeyboard(ctx)
    private val label = SettingsUi.text(ctx,"",12f).apply {
        maxLines=1; ellipsize=TextUtils.TruncateAt.END
        setPadding(dp(10),0,dp(10),0)
        gravity=Gravity.CENTER_VERTICAL
    }
    val editButton = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_baseline_edit_24)
        setPadding(dp(14),dp(14),dp(14),dp(14))
        contentDescription=ctx.getText(R.string.edit)
        imageTintList=ColorStateList.valueOf(SettingsUi.color(ctx,R.color.ui_primary))
        background=SettingsUi.clickableSurface(ctx)
    }
    private val checkMark=ImageView(ctx).apply {
        setPadding(dp(6),dp(6),dp(6),dp(6))
        imageTintList=ColorStateList.valueOf(SettingsUi.color(ctx,R.color.ui_primary))
        background=SettingsUi.surface(ctx)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    override val root=FrameLayout(ctx).apply {
        background=SettingsUi.clickableSurface(ctx)
        clipToOutline=true
        minimumHeight=dp(132)
        addView(LinearLayout(ctx).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(8),dp(8),dp(4))
            addView(preview,LinearLayout.LayoutParams(-1,0,1f))
            addView(label,LinearLayout.LayoutParams(-1,dp(36)))
        },FrameLayout.LayoutParams(-1,-1))
        addView(editButton,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END))
        addView(checkMark,FrameLayout.LayoutParams(dp(30),dp(30),Gravity.BOTTOM or Gravity.END).apply {marginEnd=dp(10);bottomMargin=dp(8)})
    }
    fun setTheme(theme: Theme) {
        currentTheme=theme
        preview.theme=theme
        val name=when(theme.name) {
            "HaokeLight" -> ctx.getString(R.string.ui_blue_light)
            "HaokeDark" -> ctx.getString(R.string.ui_blue_dark)
            else -> theme.name
        }
        label.text=name
        root.contentDescription=name
        editButton.isVisible=theme is Theme.Custom
        preview.invalidate()
    }
    fun setChecked(checked:Boolean) = setChecked(if(checked) State.Selected else State.Normal)
    fun setChecked(state:State) {
        checkMark.isVisible=state!=State.Normal
        checkMark.setImageResource(when(state) {
            State.Normal -> 0
            State.Selected -> R.drawable.ic_baseline_check_24
            State.LightMode -> R.drawable.ic_baseline_light_mode_24
            State.DarkMode -> R.drawable.ic_baseline_dark_mode_24
        })
        root.isSelected=state!=State.Normal
        root.foreground=GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT)
            cornerRadius=dp(16).toFloat()
            setStroke(dp(if(state==State.Normal) 1 else 2),SettingsUi.color(ctx,if(state==State.Normal) R.color.ui_line else R.color.ui_primary))
        }
        label.setPadding(dp(2),0,dp(if(state==State.Normal) 2 else 32),0)
    }
    private class MiniKeyboard(context:Context): View(context) {
        var theme:Theme?=null
            set(value) {field=value; background=value?.backgroundDrawable(true); invalidate()}
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=ResourcesCompat.getFont(context,R.font.noto_sans_sc)}
        private val rect=RectF()
        override fun onDraw(c:Canvas) {
            super.onDraw(c)
            val t=theme?:return
            val gap=width/100f;val kw=(width-gap*11)/10f;val kh=(height-gap*5)/4f
            paint.textSize=kh*0.65f;paint.textAlign=Paint.Align.CENTER
            for((r,s) in listOf("qwertyuiop","asdfghjkl","zxcvbnm").withIndex()) {
                val offset=(width-(kw+gap)*s.length+gap)/2
                for((k,ch) in s.withIndex()) {
                    val x=offset+k*(kw+gap);val y=gap+r*(kh+gap)
                    paint.color=t.keyBackgroundColor;rect.set(x,y,x+kw,y+kh);c.drawRoundRect(rect,gap*2,gap*2,paint)
                    paint.color=t.keyTextColor;c.drawText(ch.toString(),x+kw/2,y+(kh-paint.ascent()-paint.descent())/2,paint)
                }
            }
            val y=gap+3*(kh+gap)
            paint.color=t.keyBackgroundColor;rect.set(width*.22f,y,width*.73f,y+kh);c.drawRoundRect(rect,gap*2,gap*2,paint)
            paint.color=t.accentKeyBackgroundColor;rect.set(width*.80f,y,width*.98f,y+kh);c.drawRoundRect(rect,gap*2,gap*2,paint)
        }
    }
}
