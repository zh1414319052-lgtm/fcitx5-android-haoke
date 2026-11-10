/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.ScriptTypeface
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class TextKeyboard(
    context: Context,
    theme: Theme,
    val languageLayout: LanguageLayout = LanguageLayout.Latin
) : BaseKeyboard(context, theme, layoutFor(languageLayout)) {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Text"

        private fun layoutFor(profile: LanguageLayout): List<List<KeyDef>> {
            if (profile == LanguageLayout.NineKeyPinyin) return NineKeyLayout
            if (!profile.isScript || profile.rows.size == 3) return Layout
            val rows = profile.rows.map { row ->
                row.map { c ->
                    val raw = c.toString()
                    KeyDef(KeyDef.Appearance.AltText(raw, "", 21f, percentWidth = 1f / row.length),
                        setOf(KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(raw))),
                        arrayOf(KeyDef.Popup.Preview(raw), KeyDef.Popup.Keyboard.Preset(raw)))
                }
            }
            return rows + listOf(listOf(CapsKey(), LayoutSwitchKey("?123", percentWidth = 0.15f),
                LanguageKey(), SpaceKey(), BackspaceKey(), ReturnKey()))
        }

        private fun nineKey(digit: Char, letters: String): KeyDef {
            val label = "$digit $letters"
            return KeyDef(
                KeyDef.Appearance.Text(label, 20f, Typeface.BOLD, percentWidth = 0.25f),
                setOf(
                    KeyDef.Behavior.Press(KeyAction.FcitxKeyAction(
                        if (digit == '1') "'" else digit.toString()
                    )),
                    KeyDef.Behavior.LongPress(KeyAction.CommitAction(digit.toString()))
                ),
                arrayOf(KeyDef.Popup.Preview(label))
            )
        }

        private val NineKeyLayout = listOf(
            listOf(nineKey('1', "’"), nineKey('2', "ABC"), nineKey('3', "DEF"), BackspaceKey(0.25f)),
            listOf(nineKey('4', "GHI"), nineKey('5', "JKL"), nineKey('6', "MNO"),
                CommaKey(0.25f, KeyDef.Appearance.Variant.Alternative)),
            listOf(nineKey('7', "PQRS"), nineKey('8', "TUV"), nineKey('9', "WXYZ"),
                SymbolKey(".", 0.25f, KeyDef.Appearance.Variant.Alternative)),
            listOf(LayoutSwitchKey("?123", NumberKeyboard.Name),
                LayoutSwitchKey("!?#", PickerWindow.Key.Symbol.name),
                LanguageKey(), SpaceKey(), ReturnKey())
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                AlphabetKey("Q", "1"),
                AlphabetKey("W", "2"),
                AlphabetKey("E", "3"),
                AlphabetKey("R", "4"),
                AlphabetKey("T", "5"),
                AlphabetKey("Y", "6"),
                AlphabetKey("U", "7"),
                AlphabetKey("I", "8"),
                AlphabetKey("O", "9"),
                AlphabetKey("P", "0")
            ),
            listOf(
                AlphabetKey("A", "@"),
                AlphabetKey("S", "*"),
                AlphabetKey("D", "+"),
                AlphabetKey("F", "-"),
                AlphabetKey("G", "="),
                AlphabetKey("H", "/"),
                AlphabetKey("J", "#"),
                AlphabetKey("K", "("),
                AlphabetKey("L", ")")
            ),
            listOf(
                CapsKey(),
                AlphabetKey("Z", "'"),
                AlphabetKey("X", ":"),
                AlphabetKey("C", "\""),
                AlphabetKey("V", "?"),
                AlphabetKey("B", "!"),
                AlphabetKey("N", "~"),
                AlphabetKey("M", "\\"),
                BackspaceKey()
            ),
            listOf(
                LayoutSwitchKey("?123", ""),
                CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                LanguageKey(),
                SpaceKey(),
                SymbolKey(".", 0.1f, KeyDef.Appearance.Variant.Alternative),
                ReturnKey()
            )
        )
    }

    val caps: ImageKeyView? by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val quickphrase: ImageKeyView by lazy { findViewById(R.id.button_quickphrase) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    private val keepLettersUppercase by AppPrefs.getInstance().keyboard.keepLettersUppercase

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    private val textKeys: List<TextKeyView> by lazy {
        allViews.filterIsInstance(TextKeyView::class.java).toList()
    }

    private var capsState: CapsState = CapsState.None

    private fun transformAlphabet(c: String): String {
        return when (capsState) {
            CapsState.None -> c.lowercase()
            else -> c.uppercase()
        }
    }

    private var punctuationMapping: Map<String, String> = mapOf()
    private fun transformPunctuation(p: String) = punctuationMapping.getOrDefault(p, p)

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        if (languageLayout.isScript && action is KeyAction.FcitxKeyAction) {
            val shifted = capsState != CapsState.None
            val raw = if (source == KeyActionListener.Source.Keyboard)
                languageLayout.key(action.act, shifted) else action.act
            val needsShift = if (source == KeyActionListener.Source.Keyboard) shifted
                else raw.length == 1 && raw[0].isUpperCase()
            val mapped = action.copy(act = raw,
                states = if (needsShift) KeyStates(KeyState.Virtual, KeyState.Shift) else KeyStates.Virtual)
            if (capsState == CapsState.Once) switchCapsState()
            val symbol = languageLayout.symbols[raw]
            super.onAction(if (symbol != null) KeyAction.SymAction(KeySym(symbol), KeyStates.Virtual) else mapped, source)
            return
        }
        var transformed = action
        when (action) {
            is KeyAction.FcitxKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    when (capsState) {
                        CapsState.None -> {
                            transformed = action.copy(act = action.act.lowercase())
                        }
                        CapsState.Once -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.Shift)
                            )
                            switchCapsState()
                        }
                        CapsState.Lock -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                            )
                        }
                    }
                }
                KeyActionListener.Source.Popup -> {
                    if (capsState == CapsState.Once) {
                        switchCapsState()
                    }
                }
            }
            is KeyAction.CapsAction -> switchCapsState(action.lock)
            else -> {}
        }
        super.onAction(transformed, source)
    }

    override fun onAttach() {
        capsState = CapsState.None
        updateCapsButtonIcon()
        updateAlphabetKeys()
        if (languageLayout == LanguageLayout.NineKeyPinyin) {
            textKeys.forEach { ScriptTypeface.apply(it.mainText, it.mainText.text) }
        }
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        updatePunctuationKeys()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(ime.displayName)
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
        if (capsState != CapsState.None) {
            switchCapsState()
        }
    }

    private fun transformPopupPreview(c: String): String {
        if (languageLayout.isScript) return languageLayout.label(c, capsState != CapsState.None)
        if (c.length != 1) return c
        if (c[0].isLetter()) return transformAlphabet(c)
        return transformPunctuation(c)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.PreviewAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.PreviewUpdateAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.ShowKeyboardAction -> {
                when (action.keyboard) {
                    is KeyDef.Popup.Keyboard.Preset -> {
                        val label = action.keyboard.label
                        if (languageLayout.isScript && languageLayout.rows.any { label.lowercase() in it }) {
                            val keys = listOf(languageLayout.key(label, false), languageLayout.key(label, true)).distinct()
                            action.copy(keyboard = KeyDef.Popup.Keyboard.Labeled(keys.toTypedArray(),
                                keys.map { languageLayout.labels[it] ?: it }.toTypedArray()))
                        } else if (label.length == 1 && label[0].isLetter())
                            action.copy(
                                keyboard = action.keyboard.copy(label = transformAlphabet(label))
                            )
                        else action
                    }
                    is KeyDef.Popup.Keyboard.Explicit -> action
                    is KeyDef.Popup.Keyboard.Labeled -> action
                }
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

    private fun switchCapsState(lock: Boolean = false) {
        capsState =
            if (lock) {
                when (capsState) {
                    CapsState.Lock -> CapsState.None
                    else -> CapsState.Lock
                }
            } else {
                when (capsState) {
                    CapsState.None -> CapsState.Once
                    else -> CapsState.None
                }
            }
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    private fun updateCapsButtonIcon() {
        caps?.img?.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateAlphabetKeys() {
        textKeys.forEach {
            if (it.def !is KeyDef.Appearance.AltText) return
            if (languageLayout.isScript) {
                it.mainText.text = languageLayout.label(it.def.displayText, capsState != CapsState.None)
                ScriptTypeface.apply(it.mainText, it.mainText.text)
                it.contentDescription = it.mainText.text
                return@forEach
            }
            it.mainText.text = it.def.displayText.let { str ->
                if (str.length != 1 || !str[0].isLetter()) return@forEach
                if (keepLettersUppercase) str.uppercase() else transformAlphabet(str)
            }
        }
    }

    private fun updatePunctuationKeys() {
        textKeys.forEach {
            if (it is AltTextKeyView) {
                it.def as KeyDef.Appearance.AltText
                it.altText.text = transformPunctuation(it.def.altText)
            } else {
                it.def as KeyDef.Appearance.Text
                it.mainText.text = it.def.displayText.let { str ->
                    if (str[0].run { isLetter() || isWhitespace() }) return@forEach
                    transformPunctuation(str)
                }
            }
        }
    }

}
