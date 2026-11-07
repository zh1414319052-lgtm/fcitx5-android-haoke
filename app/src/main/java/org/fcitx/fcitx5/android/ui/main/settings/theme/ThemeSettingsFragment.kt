/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.os.Bundle
import androidx.annotation.Keep
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.utils.isDarkMode

class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs) {

    private val followSystem = ThemeManager.prefs.followSystemDayNightTheme
    private lateinit var colorMode: ListPreference

    @Keep private val followListener = ManagedPreference.OnChangeListener<Boolean> { _, _ ->
        lifecycleScope.launch { syncMode() }
    }
    @Keep private val themeListener = ThemeManager.OnThemeChangeListener {
        lifecycleScope.launch { syncMode() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followSystem.registerOnChangeListener(followListener)
        ThemeManager.addOnChangedListener(themeListener)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        findPreference<Preference>(followSystem.key)?.let { it.parent?.removePreference(it) }
        colorMode = ListPreference(requireContext()).apply {
            key = COLOR_MODE_KEY
            title = getString(R.string.haoke_keyboard_color_mode)
            dialogTitle = title
            isPersistent = false // The existing Boolean and actual theme are the durable state.
            isIconSpaceReserved = false
            // Preference compares orders by subtraction; MIN_VALUE overflows against later rows.
            order = -1
            entries = arrayOf(getString(R.string.haoke_mode_system), getString(R.string.haoke_mode_light), getString(R.string.haoke_mode_dark))
            entryValues = arrayOf("system", "light", "dark")
            setOnPreferenceChangeListener { _, value ->
                when (value) {
                    "system" -> followSystem.setValue(true)
                    "light", "dark" -> {
                        val prefs = ThemeManager.prefs
                        val dark = value == "dark"
                        val configured = if (dark) prefs.darkModeTheme.getValue() else prefs.lightModeTheme.getValue()
                        // Older settings allow any palette in either slot. Honor the explicit mode
                        // without rewriting the user's system-following palette assignments.
                        val theme = configured.takeIf { it.isDark == dark }
                            ?: if (dark) ThemePreset.HaokeDark else ThemePreset.HaokeLight
                        ThemeManager.setNormalModeTheme(theme)
                        followSystem.setValue(false)
                    }
                    else -> return@setOnPreferenceChangeListener false
                }
                syncMode()
                true
            }
        }
        preferenceScreen.addPreference(colorMode)
        syncMode()
    }

    override fun onResume() {
        super.onResume()
        syncMode()
    }

    private fun syncMode() {
        if (!::colorMode.isInitialized || !isAdded) return
        val system = followSystem.getValue()
        colorMode.value = if (system) "system" else if (ThemeManager.prefs.normalModeTheme.getValue().isDark) "dark" else "light"
        colorMode.summary = if (system) getString(R.string.haoke_mode_system_summary,
            getString(if (resources.configuration.isDarkMode()) R.string.haoke_mode_dark_state else R.string.haoke_mode_light_state))
        else getString(R.string.haoke_mode_manual_summary, colorMode.entry)
    }

    override fun onDestroy() {
        followSystem.unregisterOnChangeListener(followListener)
        ThemeManager.removeOnChangedListener(themeListener)
        super.onDestroy()
    }

    companion object { const val COLOR_MODE_KEY = "haoke_keyboard_color_mode" }
}
