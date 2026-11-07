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
    private fun find(text: String? = null, id: String? = null): Rect? {
        var result: Rect? = null
        instrumentation.runOnMainSync {
            fun visit(view: View) {
                if (result != null || !view.isShown) return
                val name = if (id != null) runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() else null
                if ((text != null && (view.contentDescription?.toString() == text ||
                    view is TextView && view.text.toString() == text)) || (id != null && name == id)) {
                    val location = IntArray(2).also(view::getLocationOnScreen)
                    if (view.width > 0 && view.height > 0)
                        result = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            WindowInspector.getGlobalWindowViews().forEach(::visit)
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
