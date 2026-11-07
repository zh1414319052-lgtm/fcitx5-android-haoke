/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.CallSuper
import org.fcitx.fcitx5.android.ui.main.modified.MyPreferenceFragment
import org.fcitx.fcitx5.android.utils.applyNavBarInsetsBottomPadding
import org.fcitx.fcitx5.android.R

abstract class PaddingPreferenceFragment : MyPreferenceFragment() {

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = super.onCreateView(inflater, container, savedInstanceState).apply {
        listView.apply {
            val margin = SettingsUi.dp(context, 16)
            setPadding(margin, SettingsUi.dp(context, 8), margin, margin)
            clipToPadding = false
            setBackgroundColor(SettingsUi.color(context, R.color.ui_background))
            addItemDecoration(SettingsUi.PreferenceSurfaces(context))
            SettingsUi.applyBottomInset(this)
        }
    }
}
