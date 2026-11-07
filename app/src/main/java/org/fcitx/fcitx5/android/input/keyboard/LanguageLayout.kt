/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.RawConfig
import org.json.JSONObject

/** Legends and physical keys are separate: engines must still receive their original key codes. */
data class LanguageLayout(
    val id: String,
    val labels: Map<String, String> = emptyMap(),
    val rows: List<String> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
    val symbols: Map<String, Int> = emptyMap()
) {
    val isScript get() = labels.isNotEmpty()
    fun key(raw: String, shifted: Boolean): String {
        if (!shifted) return raw.lowercase()
        if (!isScript || raw.length != 1 || raw[0].isLetter()) return raw.uppercase()
        if (rows.none { raw in it }) return raw
        val from = if (id.startsWith("anthy/")) "1234567890-^@[;:]\\" else "1234567890-=[];',./\\"
        val to = if (id.startsWith("anthy/")) "!\"#$%&'()~=~`{+*}|" else "!@#$%^&*()_+{}\":<>?|"
        val index = from.indexOf(raw)
        return if (index >= 0) to[index].toString() else raw
    }
    fun label(raw: String, shifted: Boolean = false): String {
        val key = key(raw, shifted)
        return labels[key] ?: key
    }

    companion object { val Latin = LanguageLayout("latin") }
}

class LanguageLayoutResolver(private val context: Context) {
    private var profiles: Map<String, LanguageLayout>? = null

    private suspend fun profiles(): Map<String, LanguageLayout> = profiles ?: withContext(Dispatchers.IO) {
        val json = context.assets.open("keyboard-labels.json").bufferedReader().use { JSONObject(it.readText()) }
        json.keys().asSequence().associateWith { id ->
            val item = json.getJSONObject(id)
            val labels = item.getJSONObject("labels")
            val rows = item.getJSONArray("rows")
            val symbols = item.optJSONObject("symbols")
            LanguageLayout(id, labels.keys().asSequence().associateWith(labels::getString),
                List(rows.length()) { rows.getString(it) },
                symbols?.keys()?.asSequence()?.associateWith(symbols::getInt) ?: emptyMap())
        }.also { profiles = it }
    }

    suspend fun resolve(api: FcitxAPI, ime: InputMethodEntry): LanguageLayout {
        if (ime.addon !in setOf("hangul", "chewing", "anthy", "sayura")) return LanguageLayout.Latin
        val all = profiles()
        if (ime.addon == "sayura") return all.getValue("sayura")
        if (ime.addon == "anthy") return when (ime.subMode.label) {
            "A", "Ａ" -> LanguageLayout.Latin
            "ア" -> all.getValue("anthy/Katakana")
            "ｱ" -> all.getValue("anthy/HalfKatakana")
            else -> all.getValue("anthy/Kana")
        }
        val cfg = api.getAddonConfig(ime.addon).findByName("cfg") ?: return LanguageLayout.Latin
        fun RawConfig.valueAt(vararg path: String): String? {
            var node: RawConfig? = this
            path.forEach { node = node?.findByName(it) }
            return node?.value
        }
        val id = when (ime.addon) {
            "hangul" -> "hangul/${cfg.valueAt("Keyboard") ?: "Dubeolsik"}"
            "chewing" -> "chewing/${cfg.valueAt("Layout") ?: "Default Keyboard"}"
            else -> return LanguageLayout.Latin
        }
        val layout = all[id] ?: return LanguageLayout.Latin
        return layout
    }
}
