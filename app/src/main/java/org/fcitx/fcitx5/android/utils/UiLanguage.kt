/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object UiLanguage {
    fun locale(): Locale {
        val system = Resources.getSystem().configuration
        val primary = if (Build.VERSION.SDK_INT >= 24) {
            system.locales.takeUnless { it.isEmpty }?.get(0)
        } else {
            @Suppress("DEPRECATION")
            system.locale
        }
        return PrimaryLanguage.select(primary?.toLanguageTag())
    }

    fun configuration(original: Configuration): Configuration = Configuration(original).apply {
        val selected = locale()
        if (Build.VERSION.SDK_INT >= 24) setLocales(LocaleList(selected)) else setLocale(selected)
        setLayoutDirection(selected)
    }

    fun wrap(context: Context): Context {
        // Process-local formatters use the same language as Android resources.
        Locale.setDefault(locale())
        return context.createConfigurationContext(configuration(context.resources.configuration))
    }

    @Suppress("DEPRECATION")
    fun refresh(context: Context) {
        Locale.setDefault(locale())
        context.resources.updateConfiguration(
            configuration(context.resources.configuration), context.resources.displayMetrics
        )
    }
}
