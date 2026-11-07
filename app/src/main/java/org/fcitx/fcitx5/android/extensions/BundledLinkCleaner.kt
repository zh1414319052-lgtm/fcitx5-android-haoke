/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.extensions

import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.extensions.clearurls.ClearURLs
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber

object BundledLinkCleaner {
    private val initialized by lazy {
        appContext.assets.open("bundled/clearurls.json").bufferedReader().use { ClearURLs.initCatalog(it.readText()) }
        true
    }

    fun transform(source: String): String {
        if (!AppPrefs.getInstance().internal.bundledClearUrls.getValue()) return source
        return try {
            if (initialized) ClearURLs.transform(source) else source
        } catch (_: Exception) {
            // Preserve the original clip if a rule cannot be applied. Never log URLs.
            Timber.w("Bundled link cleaner could not apply a rule; original text preserved")
            source
        }
    }
}
