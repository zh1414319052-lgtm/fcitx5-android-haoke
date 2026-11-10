/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.keyboard.KeyView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.extensions.BundledExtensions
import org.fcitx.fcitx5.android.input.keyboard.LanguageLayoutResolver
import org.fcitx.fcitx5.android.input.keyboard.LangSwitchBehavior
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Physical touches on the actual IME window, with a synthetic editor in the QA app only. */
class LanguageKeyboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var connection: FcitxConnection
    private lateinit var activity: MainActivity
    private lateinit var editor: EditText

    @Before fun start() = runBlocking {
        connection = FcitxDaemon.connect(javaClass.name)
        AppPrefs.getInstance().internal.needNotifications.setValue(false)
        AppPrefs.getInstance().clipboard.clipboardListening.setValue(false)
        AppPrefs.getInstance().keyboard.showLangSwitchKey.setValue(true)
        AppPrefs.getInstance().keyboard.popupOnKeyPress.setValue(true)
        AppPrefs.getInstance().keyboard.keepLettersUppercase.setValue(false)
        AppPrefs.getInstance().keyboard.langSwitchKeyBehavior.setValue(LangSwitchBehavior.Enumerate)
        for (id in listOf("hangul", "anthy", "chewing", "sayura")) BundledExtensions.change(connection, id, true)
        connection.runOnReady {
            setEnabledIme(arrayOf("keyboard-us", "hangul", "anthy", "chewing", "sayura", "pinyin"))
        }
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_RUN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            editor = EditText(activity).apply {
                id = View.generateViewId()
                hint = "QA language keyboard"
                minLines = 3
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.noto_sans_sc)
            }
            activity.setContentView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(editor, LinearLayout.LayoutParams(-1, -2))
            })
            editor.requestFocus()
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            (activity.getSystemService(InputMethodManager::class.java)).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        delay(1500)
    }

    @After fun stop() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
        FcitxDaemon.disconnect(javaClass.name)
    }

    private fun nodeMatches(node: AccessibilityNodeInfo, text: String?, id: String?): Rect? {
        if ((text != null && (node.text?.toString() == text || node.contentDescription?.toString() == text)) ||
            (id != null && node.viewIdResourceName?.endsWith(":id/$id") == true)) {
            val rect = Rect().also(node::getBoundsInScreen)
            if (!rect.isEmpty && node.isVisibleToUser) return rect
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            nodeMatches(child, text, id)?.let { return it }
        }
        return null
    }

    @android.annotation.SuppressLint("NewApi")
    private fun findVisibleView(text: String?, id: String?): View? {
        fun visit(view: View): View? {
            if (!view.isShown) return null
            val name = if (id != null) runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() else null
            if ((text != null && (view.contentDescription?.toString() == text ||
                view is TextView && view.text.toString() == text)) || (id != null && name == id)) {
                if (view.width > 0 && view.height > 0) return view
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                visit(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        return WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::visit)
    }

    private fun find(text: String? = null, id: String? = null): Rect? {
        var result: Rect? = null
        instrumentation.runOnMainSync {
            findVisibleView(text, id)?.let { view ->
                val (x, y) = IntArray(2).also(view::getLocationOnScreen)
                result = Rect(x, y, x + view.width, y + view.height)
            }
        }
        return result
    }

    private suspend fun waitKey(text: String? = null, id: String? = null): Rect = try {
        withTimeout(12_000) {
            var rect = find(text, id)
            while (rect == null) { delay(75); rect = find(text, id) }
            rect
        }
    } catch (timeout: TimeoutCancellationException) {
        screenshot("missing-key")
        throw AssertionError("Keyboard key missing: text=$text id=$id", timeout)
    }

    private suspend fun tap(text: String? = null, id: String? = null, hold: Long = 35) {
        val rect = waitKey(text, id)
        val now = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
            rect.exactCenterX(), rect.exactCenterY(), 0))
        delay(hold)
        instrumentation.sendPointerSync(MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
            rect.exactCenterX(), rect.exactCenterY(), 0))
        delay(100)
    }

    private suspend fun activate(id: String, key: String) {
        connection.runOnReady { reset(); activateIme(id) }
        waitKey(key)
        instrumentation.runOnMainSync { editor.setText("") }
    }

    @android.annotation.SuppressLint("NewApi", "ClickableViewAccessibility")
    private suspend fun longPressSelect(key: String, alternative: String) {
        delay(500) // Let the synthetic editor's restartInput finish before holding a key.
        val rect = waitKey(key)
        var targetKey: KeyView? = null
        instrumentation.runOnMainSync {
            fun visit(view: View) {
                if (view is KeyView && view.contentDescription?.toString() == key && view.isShown)
                    targetKey = view
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            WindowInspector.getGlobalWindowViews().forEach(::visit)
        }
        val now = SystemClock.uptimeMillis()
        var x = rect.exactCenterX()
        var y = rect.exactCenterY()
        // System-injected DOWN events did not reach the key in this long-hold
        // test. Dispatch the gesture through the real attached key view instead;
        // this is an instrumented-view check, not proof of an OS-injected hold.
        fun inject(action: Int) {
            instrumentation.runOnMainSync {
                val view = checkNotNull(targetKey)
                val origin = IntArray(2).also(view::getLocationOnScreen)
                val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                    x - origin[0], y - origin[1], 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        try {
            inject(MotionEvent.ACTION_DOWN)
            delay(800)
            val target = waitKey(alternative)
            screenshot("korean-long-press")
            x = target.exactCenterX(); y = target.exactCenterY()
            inject(MotionEvent.ACTION_MOVE)
            delay(100)
        } finally {
            inject(MotionEvent.ACTION_UP)
        }
        delay(150)
    }

    private fun content(): String {
        var result = ""
        instrumentation.runOnMainSync { result = editor.text.toString() }
        return result
    }

    private suspend fun selectCandidate(text: String, expected: String) {
        clickKeyboardControl(text)
        try {
            withTimeout(3000) {
                while (content() != expected) delay(50)
            }
        } catch (timeout: TimeoutCancellationException) {
            screenshot("candidate-selection")
            throw AssertionError("Tapping $text: expected $expected, got ${content()}", timeout)
        }
    }

    private suspend fun clickKeyboardControl(text: String? = null, id: String? = null) {
        // MuMu can reposition the IME window while coordinate events are queued.
        // Exercise the actual visible control through its Android accessibility click.
        withTimeout(12_000) {
            var clicked = false
            while (!clicked) {
                instrumentation.runOnMainSync {
                    val view = findVisibleView(text, id)
                    val control = generateSequence(view) { it.parent as? View }
                        .firstOrNull { it.isClickable }
                    clicked = control?.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) == true
                }
                if (!clicked) delay(75)
            }
        }
        delay(100)
    }

    @android.annotation.SuppressLint("NewApi")
    private fun screenshot(name: String) {
        val folder = File(context.filesDir, "qa-language-keyboards").apply { mkdirs() }
        instrumentation.runOnMainSync {
            val diagnostic = StringBuilder()
            fun visit(view: View) {
                if (view is TextKeyboard) diagnostic.appendLine("Keyboard=${view.languageLayout.id} popup=${view.popupActionListener != null} key=${view.keyActionListener != null}")
                if (view is KeyView && view.isPressed) diagnostic.appendLine("Pressed=${view.contentDescription} longPress=${view.longPressEnabled}")
                if (view is TextView && view.isShown) diagnostic.appendLine("Text=${view.text} bounds=${Rect().also { view.getGlobalVisibleRect(it) }}")
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            WindowInspector.getGlobalWindowViews().forEach(::visit)
            File(folder, "$name.txt").writeText(diagnostic.toString())
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun nativeLanguageKeysAndSwitchingMatchActualInput() = runBlocking<Unit> {
        activate("hangul", "ㅂ")
        screenshot("korean")
        for (key in listOf("ㅎ", "ㅏ", "ㄴ", "ㄱ", "ㅡ", "ㄹ")) tap(key)
        tap(id = "button_space")
        assertTrue("Korean editor: ${content()}", content().contains("한글"))
        instrumentation.runOnMainSync { editor.setText("") }
        tap(id = "button_caps")
        waitKey("ㄲ")
        tap("ㄲ"); tap("ㅏ"); tap(id = "button_space")
        assertTrue("Shifted Hangul: ${content()}", content().contains("까"))

        // Actual globe gesture traverses to Japanese, rather than just injecting a fake UI state.
        tap(id = "button_lang")
        waitKey("た")
        assertEquals("anthy", connection.runOnReady { currentIme().uniqueName })
        instrumentation.runOnMainSync { editor.setText("") }
        screenshot("japanese")
        tap("に"); tap("ほ"); tap("ん")
        tap(id = "button_return")
        assertTrue("Kana editor: ${content()}", content().contains("にほん"))
        instrumentation.runOnMainSync { editor.setText("") }
        tap("か"); tap("゛"); tap(id = "button_return")
        assertTrue("Voiced Kana: ${content()}", content().contains("が"))

        tap(id = "button_lang")
        waitKey("ㄅ")
        screenshot("zhuyin")
        instrumentation.runOnMainSync { editor.setText("") }
        for (key in listOf("ㄋ", "ㄧ", "ˇ", "ㄏ", "ㄠ", "ˇ")) tap(key)
        tap(id = "button_return")
        assertTrue("Zhuyin editor: ${content()}", content().contains("你好"))

        tap(id = "button_lang")
        waitKey("ක")
        screenshot("sinhala")
        instrumentation.runOnMainSync { editor.setText("") }
        tap("ක"); tap("අ"); tap(id = "button_space")
        assertTrue("Sinhala editor: ${content()}", content().contains("ක"))

        activate("keyboard-us", "q")
        screenshot("english")
        for (key in "hello") tap(key.toString())
        tap(id = "button_return")
        assertTrue("English editor: ${content()}", content().contains("hello"))

        activate("hangul", "ㅂ")
        tap("?123")
        tap("ABC")
        waitKey("ㅂ")
        // A hide/show cycle must restore the current native alphabet.
        instrumentation.runOnMainSync {
            activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0)
        }
        delay(300)
        instrumentation.runOnMainSync {
            activity.getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        waitKey("ㅂ")
    }

    @Test fun koreanLongPressUsesTheShownHangulAlternative() = runBlocking {
        activate("hangul", "ㅂ")
        longPressSelect("ㅂ", "ㅃ")
        tap("ㅏ"); tap(id = "button_space")
        assertTrue("Long-press Hangul: ${content()}", content().contains("빠"))
    }

    @Test fun nineKeyPinyinTypesAndFollowsLanguageSwitches() = runBlocking {
        BundledExtensions.change(connection, "rime", true)
        connection.runOnReady {
            val method = availableIme().first { it.uniqueName == "haoke-pinyin-t9" }
            assertTrue(method.displayName in setOf("九宫格拼音", "9-key Pinyin"))
            setEnabledIme(arrayOf("haoke-pinyin-t9", "keyboard-us", "hangul", "anthy",
                "chewing", "sayura", "pinyin", "rime"))
        }
        activate("haoke-pinyin-t9", "6 MNO")
        assertNull(find(text = "q"))
        delay(500) // Wait for the synthetic editor's restartInput and window transition.

        // The extra digit is removed before choosing a real phrase candidate.
        for (key in listOf("6 MNO", "4 GHI", "4 GHI", "2 ABC", "6 MNO", "9 WXYZ")) clickKeyboardControl(key)
        clickKeyboardControl(id = "button_backspace")
        assertTrue("Nine-key Pinyin did not start composing", connection.runOnReady {
            inputPanelCached.preedit.isNotEmpty() || clientPreeditCached.isNotEmpty()
        })
        selectCandidate("你好", "你好")

        clickKeyboardControl("?123")
        clickKeyboardControl("1")
        assertEquals("你好1", content())
        clickKeyboardControl("ABC")
        waitKey("6 MNO")
        clickKeyboardControl("!?#")
        clickKeyboardControl("ABC")
        waitKey("6 MNO")

        for (key in listOf("9 WXYZ", "4 GHI", "6 MNO", "6 MNO", "4 GHI",
            "1 ’", "4 GHI", "8 TUV", "6 MNO")) clickKeyboardControl(key)
        selectCandidate("中国", "你好1中国")

        for (key in listOf("6 MNO", "4 GHI", "4 GHI", "2 ABC", "6 MNO")) clickKeyboardControl(key)
        clickKeyboardControl("?123")
        clickKeyboardControl("2")
        assertTrue("Numeric panel with composition: ${content()}", content().endsWith("2"))
        clickKeyboardControl("ABC")
        waitKey("6 MNO")

        for ((method, key) in listOf(
            "keyboard-us" to "q", "hangul" to "ㅂ", "anthy" to "た",
            "chewing" to "ㄅ", "sayura" to "ක", "pinyin" to "q", "rime" to "q",
            "haoke-pinyin-t9" to "6 MNO"
        )) {
            clickKeyboardControl(id = "button_lang")
            waitKey(key)
            assertEquals(method, connection.runOnReady { currentIme().uniqueName })
            if (method != "haoke-pinyin-t9") assertNull(find(text = "6 MNO"))
        }

        instrumentation.runOnMainSync {
            activity.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(editor.windowToken, 0)
        }
        delay(300)
        instrumentation.runOnMainSync {
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        waitKey("6 MNO")
        screenshot("nine-key-pinyin")
    }

    @Test fun nativeConfigurationSelectsTheMatchingKoreanAndZhuyinLayout() = runBlocking {
        val resolver = LanguageLayoutResolver(context)
        connection.runOnReady {
            activateIme("hangul")
            val cfg = getAddonConfig("hangul")["cfg"]
            assertEquals("Dubeolsik", cfg["Keyboard"].value)
            val current = resolver.resolve(this, currentIme())
            assertEquals("ㅂ", current.label("q"))
            cfg["Keyboard"].value = "Sebeolsik Final"
            setAddonConfig("hangul", cfg)
            val alternate = resolver.resolve(this, currentIme())
            assertEquals("hangul/Sebeolsik Final", alternate.id)
            assertEquals(4, alternate.rows.size)
            cfg["Keyboard"].value = "Dubeolsik"
            setAddonConfig("hangul", cfg)
            activateIme("chewing")
            assertEquals("ㄅ", resolver.resolve(this, currentIme()).label("1"))
        }
    }
}
