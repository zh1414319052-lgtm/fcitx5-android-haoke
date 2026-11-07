/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main.settings.theme
import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.ui.common.SettingsUi
import kotlin.math.max
class ResponsiveThemeListView(context:Context):RecyclerView(context) {
    private val gap=SettingsUi.dp(context,8)
    private val grid=object:GridLayoutManager(context,2) {
        override fun generateDefaultLayoutParams()=LayoutParams(LayoutParams.MATCH_PARENT,SettingsUi.dp(context,132))
    }
    init {
        setPadding(gap,gap,gap,gap)
        clipToPadding=false
        layoutManager=grid
        addItemDecoration(object:ItemDecoration() {
            override fun getItemOffsets(outRect:Rect,view:View,parent:RecyclerView,state:State) {outRect.set(gap,gap,gap,gap)}
        })
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0) grid.spanCount=max(1,(w-gap*2)/(SettingsUi.dp(context,136)+gap*2))
    }
}
