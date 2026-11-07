/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.common.SettingsUi
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class MainFragment : Fragment() {

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        // AboutMenuProvider is tied to viewLifecycleOwner, so the about menu items
        // are automatically shown when this Fragment is visible and removed when it's not
        requireActivity().addMenuProvider(
            AboutMenuProvider(), viewLifecycleOwner, Lifecycle.State.STARTED
        )
    }

    private inner class AboutMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.item(R.string.faq) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.faqUrl)))
            }
            menu.item(R.string.developer) {
                navigateWithAnim(SettingsRoute.Developer)
            }
            menu.item(R.string.about) {
                navigateWithAnim(SettingsRoute.About)
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        fun dp(value: Int) = SettingsUi.dp(context, value)
        val content = LinearLayout(context).apply {
            id = R.id.settings_home_content
            orientation = LinearLayout.VERTICAL
        }
        fun destination(@StringRes title: Int, @DrawableRes icon: Int, route: SettingsRoute, @StringRes summary: Int? = null) =
            SettingsUi.destination(context, title, icon, summary) { navigateWithAnim(route) }
        fun group(vararg rows: View) {
            content.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = SettingsUi.surface(context)
                rows.forEachIndexed { index, row ->
                    addView(row, LinearLayout.LayoutParams(-1, -2))
                    if (index != rows.lastIndex) addView(View(context).apply {
                        setBackgroundColor(SettingsUi.color(context, R.color.ui_line))
                    }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(16); marginEnd = dp(16) })
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = SettingsUi.surface(context, R.color.ui_soft)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            addView(SettingsUi.text(context, getText(R.string.ui_home_heading), 20f).apply { setTypeface(typeface, Typeface.BOLD) })
            addView(SettingsUi.text(context, getText(R.string.ui_home_subtitle), 13f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        group(
            destination(R.string.input_methods, R.drawable.ic_baseline_language_24, SettingsRoute.InputMethodList, R.string.ui_home_im_summary),
            destination(R.string.language_extensions, R.drawable.ic_baseline_extension_24, SettingsRoute.Extensions, R.string.ui_home_extension_summary)
        )
        content.addView(SettingsUi.section(context, R.string.ui_appearance))
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(destination(R.string.theme, R.drawable.ic_baseline_palette_24, SettingsRoute.Theme), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            addView(destination(R.string.virtual_keyboard, R.drawable.ic_baseline_keyboard_24, SettingsRoute.VirtualKeyboard), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        })
        group(
            destination(R.string.candidates_window, R.drawable.ic_baseline_list_alt_24, SettingsRoute.CandidatesWindow),
            destination(R.string.clipboard, R.drawable.ic_clipboard, SettingsRoute.Clipboard),
            destination(R.string.emoji_and_symbols, R.drawable.ic_baseline_emoji_symbols_24, SettingsRoute.Symbol)
        )
        content.addView(SettingsUi.section(context, R.string.ui_more_settings))
        group(
            destination(R.string.global_options, R.drawable.ic_baseline_tune_24, SettingsRoute.GlobalConfig),
            destination(R.string.addons, R.drawable.ic_baseline_extension_24, SettingsRoute.AddonList),
            destination(R.string.advanced, R.drawable.ic_baseline_more_horiz_24, SettingsRoute.Advanced),
            destination(R.string.about, R.drawable.ic_baseline_info_24, SettingsRoute.About)
        )
        return ScrollView(context).apply {
            id = R.id.settings_home_scroll
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setBackgroundColor(SettingsUi.color(context, R.color.ui_background))
            addView(content, ViewGroup.LayoutParams(-1, -2))
            SettingsUi.applyBottomInset(this)
        }
    }
}
