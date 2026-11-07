/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.utils.PrimaryLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chineseNamesAndEnglishFallbacksInHostResources() {
        listOf("zh-TW,en-US", "zh-HK,en", "zh-Hant,en", "zh-SG,en", "zh,en").forEach {
            val config = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(it))
            }
            assertEquals("小企鹅输入法（测试版）", context.createConfigurationContext(config).getString(R.string.app_name_debug))
        }
        listOf("en-US,zh-CN", "ja-JP,zh-TW", "fr,zh", "ar,zh").forEach {
            val config = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(it))
            }
            assertEquals("Fcitx5 (QA)", context.createConfigurationContext(config).getString(R.string.app_name_debug))
        }
    }

    @Test fun isolatedLanguageChangesAndRecreatedContexts() {
        listOf("zh-Hant", "en-GB", "ja-JP", "zh-CN", "en-US").forEach { tag ->
            val config = Configuration(context.resources.configuration).apply {
                setLocale(PrimaryLanguage.select(tag))
            }
            val expected = if (tag.startsWith("zh")) "剪贴板" else "Clipboard"
            repeat(2) {
                assertEquals(expected, context.createConfigurationContext(config).getString(R.string.clipboard))
            }
        }
    }
}
