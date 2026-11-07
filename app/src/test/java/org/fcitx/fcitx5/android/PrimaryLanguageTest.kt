/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.utils.PrimaryLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryLanguageTest {
    @Test fun chineseVariantsUseSimplifiedChinese() {
        listOf("zh", "zh-CN", "zh-SG", "zh-Hans", "zh-Hant", "zh-TW", "zh-HK", "ZH_tw")
            .forEach { assertEquals("zh-CN", PrimaryLanguage.select(it).toLanguageTag()) }
    }

    @Test fun onlyThePrimaryLanguageMatters() {
        listOf(listOf("en-US", "zh-CN"), listOf("ja-JP", "zh-TW"), listOf("fr", "zh"))
            .forEach { assertEquals("en", PrimaryLanguage.select(it.first()).toLanguageTag()) }
        assertEquals("zh-CN", PrimaryLanguage.select(listOf("zh-TW", "en").first()).toLanguageTag())
        listOf(null, "", "und", "de", "ar", "en", "en-GB")
            .forEach { assertEquals("en", PrimaryLanguage.select(it).toLanguageTag()) }
    }
}
