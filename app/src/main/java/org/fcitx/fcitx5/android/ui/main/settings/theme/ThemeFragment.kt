/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

class ThemeFragment : Fragment() {

    private var mediator: TabLayoutMediator? = null

    private lateinit var previewUi: KeyboardPreviewUi

    private lateinit var tabLayout: TabLayout

    private lateinit var viewPager: ViewPager2
    private var selectedTab = 0
    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) { selectedTab = position }
    }

    @Keep
    private val onThemeChangeListener = ThemeManager.OnThemeChangeListener { theme ->
        viewLifecycleOwnerLiveData.value?.lifecycleScope?.launch {
            previewUi.setTheme(theme)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = with(requireContext()) {
        previewUi = KeyboardPreviewUi(this, ThemeManager.activeTheme, compact = true)
        ThemeManager.addOnChangedListener(onThemeChangeListener)
        val context = this
        tabLayout = TabLayout(context).apply {
            setBackgroundColor(org.fcitx.fcitx5.android.ui.common.SettingsUi.color(context, R.color.ui_background))
        }
        viewPager = ViewPager2(context).apply {
            id = R.id.settings_theme_pager
            adapter = object : FragmentStateAdapter(this@ThemeFragment) {
                override fun getItemCount() = 2
                override fun createFragment(position: Int): Fragment = when (position) {
                    0 -> ThemeListFragment()
                    else -> ThemeSettingsFragment()
                }
            }
            setCurrentItem(savedInstanceState?.getInt(STATE_TAB) ?: selectedTab, false)
            registerOnPageChangeCallback(pageCallback)
        }
        mediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(if (position == 0) R.string.theme else R.string.configure)
        }.also { it.attach() }
        android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(org.fcitx.fcitx5.android.ui.common.SettingsUi.color(context, R.color.ui_background))
            addView(FittedKeyboardPreviewView(context, previewUi), android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                val margin = context.dp(16)
                setMargins(margin, context.dp(12), margin, context.dp(8))
            })
            addView(tabLayout, android.widget.LinearLayout.LayoutParams(-1, -2))
            addView(viewPager, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    override fun onDestroyView() {
        selectedTab = viewPager.currentItem
        viewPager.unregisterOnPageChangeCallback(pageCallback)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        mediator?.detach()
        mediator = null
        viewPager.adapter = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, if (::viewPager.isInitialized) viewPager.currentItem else selectedTab)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ThemeManager.syncToDeviceEncryptedStorage()
        }
        super.onStop()
    }

    override fun onDestroy() {
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        super.onDestroy()
    }

    companion object { private const val STATE_TAB = "theme-selected-tab" }
}
