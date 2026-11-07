/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.formatDateTime
import org.fcitx.fcitx5.android.utils.navigateWithAnim

class AboutFragment : PaddingPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(androidx.preference.Preference(context).apply {
                layoutResource = R.layout.ui_about_header
                isSelectable = false
                isIconSpaceReserved = false
            })
            addPreference(R.string.privacy_policy) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.privacyPolicyUrl)))
            }
            addPreference(
                R.string.open_source_licenses,
                R.string.licenses_of_third_party_libraries
            ) {
                navigateWithAnim(SettingsRoute.License)
            }
            addPreference(R.string.haoke_upstream_source, R.string.github_repo) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.githubRepo)))
            }
            addPreference(R.string.haoke_upstream_license, Const.licenseSpdxId) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.licenseUrl)))
            }
            addCategory(R.string.version) {
                isIconSpaceReserved = false
                addPreference(R.string.current_version, Const.versionName)
                addPreference(R.string.haoke_upstream_commit, BuildConfig.BUILD_GIT_HASH) {
                    val commit = BuildConfig.BUILD_GIT_HASH.substringBefore('-')
                    val uri = Uri.parse("${Const.githubRepo}/commit/${commit}")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                addPreference(R.string.build_time, formatDateTime(BuildConfig.BUILD_TIME))
            }
            addCategory(R.string.haoke_edition) {
                isIconSpaceReserved = false
                addPreference(R.string.haoke_tools) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://haoke.org/software/d000c19470bc74b6")))
                }
                addPreference(R.string.haoke_distribution_license, "GPL-3.0-only") {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.haoke_distribution_license)
                        .setMessage(R.string.haoke_license_explanation)
                        .setPositiveButton(android.R.string.ok, null).show()
                }
                if (!BuildConfig.DEBUG && BuildConfig.HAOKE_UPDATES_ENABLED) {
                    addPreference(R.string.haoke_check_updates, R.string.haoke_update_privacy) {
                        (requireActivity() as MainActivity).checkHaokeUpdates()
                    }
                }
            }
        }
    }
}
