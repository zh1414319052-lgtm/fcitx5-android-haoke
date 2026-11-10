/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context, theme),
            NumberKeyboard.Name to NumberKeyboard(context, theme)
        )
    }
    private var currentKeyboardName = ""
    private val languageLayouts by lazy { LanguageLayoutResolver(context) }
    private var languageLayoutJob: Job? = null
    private var languageLayoutGeneration = 0L
    private var displayedTextIme = ""
    private var punctuationMapping: Map<String, String> = emptyMap()
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, _ ->
        if (it is KeyAction.LayoutSwitchAction) {
            switchLayout(it.act)
        } else if (currentKeyboardName == TextKeyboard.Name &&
            displayedTextIme != fcitx.runImmediately { inputMethodEntryCached.uniqueName } &&
            it != KeyAction.LangSwitchAction && it != KeyAction.ShowInputMethodPickerAction
        ) {
            // A script layout may still be resolving. Never send its old keys to the new engine.
            return@KeyActionListener
        } else {
            val expectedIme = displayedTextIme.takeIf { currentKeyboardName == TextKeyboard.Name }
            commonKeyActionListener.onKeyAction(it, expectedIme)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        attachLayout(TextKeyboard.Name)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.onInputMethodUpdate(fcitx.runImmediately { inputMethodEntryCached })
            it.onPunctuationUpdate(punctuationMapping)
        }
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val target = to.ifEmpty { lastSymbolType }
        ContextCompat.getMainExecutor(service).execute {
            if (keyboards.containsKey(target)) {
                if (remember && target != TextKeyboard.Name) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(target)
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> TextKeyboard.Name
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        currentKeyboard?.onInputMethodUpdate(ime)
        refreshLanguageLayout()
    }

    // Typing-method and layout settings can change without changing the engine name.
    override fun onStatusAreaUpdate(actions: Array<Action>) {
        refreshLanguageLayout()
    }

    private fun replaceTextLayout(ime: InputMethodEntry, layout: LanguageLayout) {
        displayedTextIme = ime.uniqueName
        val old = keyboards[TextKeyboard.Name] as TextKeyboard
        if (old.languageLayout == layout) return
        popup.dismissAll()
        val visible = currentKeyboardName == TextKeyboard.Name
        if (visible) detachCurrentLayout()
        keyboards[TextKeyboard.Name] = TextKeyboard(context, theme, layout)
        if (visible) attachLayout(TextKeyboard.Name)
    }

    private fun refreshLanguageLayout() {
        val generation = ++languageLayoutGeneration
        languageLayoutJob?.cancel()
        val ime = fcitx.runImmediately { inputMethodEntryCached }
        LanguageLayoutResolver.layoutWithoutConfig(ime)?.let {
            replaceTextLayout(ime, it)
            return
        }
        languageLayoutJob = service.lifecycleScope.launch {
            try {
                val layout = fcitx.runOnReady {
                    languageLayouts.resolve(this, ime)
                }
                if (generation != languageLayoutGeneration ||
                    ime != fcitx.runImmediately { inputMethodEntryCached }) return@launch
                replaceTextLayout(ime, layout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.w(failure, "Unable to resolve input method keyboard layout")
                // Never leave the previous language's letters on a different engine.
                if (generation == languageLayoutGeneration &&
                    ime == fcitx.runImmediately { inputMethodEntryCached }) {
                    replaceTextLayout(ime, LanguageLayout.Latin)
                }
            }
        }
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        notifyBarLayoutChanged()
        refreshLanguageLayout()
    }

    override fun onDetached() {
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name)
    }
}
