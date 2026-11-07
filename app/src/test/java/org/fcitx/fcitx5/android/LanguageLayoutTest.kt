/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.keyboard.LanguageLayout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LanguageLayoutTest {
    private fun layout(id: String): LanguageLayout {
        val json = JSONObject(File("src/main/assets/keyboard-labels.json").readText()).getJSONObject(id)
        val labels = json.getJSONObject("labels")
        val rows = json.getJSONArray("rows")
        return LanguageLayout(id, labels.keys().asSequence().associateWith(labels::getString),
            List(rows.length()) { rows.getString(it) })
    }

    @Test fun koreanTwoSetUsesHangulIncludingShiftedConsonantsAndVowels() {
        val korean = layout("hangul/Dubeolsik")
        assertEquals("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "qwertyuiop".map { korean.label(it.toString()) }.joinToString(""))
        assertEquals("ㅃ", korean.label("q", true))
        assertEquals("ㄲ", korean.label("r", true))
        assertEquals("ㅒ", korean.label("o", true))
        assertEquals("Q", korean.key("q", true))
        assertEquals("g", korean.key("G", false))
    }

    @Test fun zhuyinIncludesTheNumberAndPunctuationKeysNeededToType() {
        val zhuyin = layout("chewing/Default Keyboard")
        assertEquals("ㄅ", zhuyin.label("1"))
        assertEquals("ˇ", zhuyin.label("3"))
        assertEquals("ㄤ", zhuyin.label(";"))
        assertEquals("ㄦ", zhuyin.label("-"))
        assertTrue(zhuyin.rows.joinToString("").contains("1"))
        assertTrue(zhuyin.rows.joinToString("").contains(";"))
        assertEquals("Q", zhuyin.label("q", true))
    }

    @Test fun kanaDisplaysActualKanaAndShiftedSmallKana() {
        val kana = layout("anthy/Kana")
        assertEquals("た", kana.label("q"))
        assertEquals("ぃ", kana.label("e", true))
        assertEquals("ぁ", kana.label("3", true))
        assertEquals("゛", kana.label("@"))
        assertEquals("゜", kana.label("["))
        assertEquals("ろ", kana.labels["_"])
    }

    @Test fun sinhalaUsesEngineMappingsAndLatinRestoresItsOwnKeys() {
        val sinhala = layout("sayura")
        assertEquals("ක", sinhala.label("k"))
        assertEquals("ඛ", sinhala.label("k", true))
        assertEquals("අ", sinhala.label("a"))
        assertEquals("k", LanguageLayout.Latin.label("k"))
        assertEquals("K", LanguageLayout.Latin.label("k", true))
    }

    @Test fun everyVisibleShiftedKeyHasAValidEngineKey() {
        val all = JSONObject(File("src/main/assets/keyboard-labels.json").readText())
        for (id in all.keys()) {
            val profile = layout(id)
            for (raw in profile.rows.joinToString("")) {
                assertEquals("$id/$raw", 1, profile.key(raw.toString(), true).length)
                assertTrue(profile.label(raw.toString()).isNotEmpty())
                assertTrue(profile.label(raw.toString(), true).isNotEmpty())
            }
        }
    }
}
