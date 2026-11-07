/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main.settings.theme
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.SettingsUi
import splitties.views.dsl.core.Ui
class NewThemeEntryUi(override val ctx:Context):Ui {
    val text=SettingsUi.text(ctx,ctx.getText(R.string.new_theme),13f).apply {gravity=Gravity.CENTER;setTextColor(SettingsUi.color(ctx,R.color.ui_primary))}
    val icon=ImageView(ctx).apply {setImageResource(R.drawable.ic_baseline_plus_24);imageTintList=ColorStateList.valueOf(SettingsUi.color(ctx,R.color.ui_primary));setPadding(0,SettingsUi.dp(ctx,20),0,SettingsUi.dp(ctx,12))}
    override val root=LinearLayout(ctx).apply {
        orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
        background=SettingsUi.clickableSurface(ctx)
        addView(icon,LinearLayout.LayoutParams(SettingsUi.dp(ctx,48),0,1f))
        addView(text,LinearLayout.LayoutParams(-1,SettingsUi.dp(ctx,40)))
    }
}
