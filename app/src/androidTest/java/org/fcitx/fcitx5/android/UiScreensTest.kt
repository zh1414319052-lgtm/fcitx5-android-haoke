/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.TypedValue
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import arrow.core.getOrElse
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.pinyin.CustomPhraseManager
import org.fcitx.fcitx5.android.data.pinyin.customphrase.PinyinCustomPhrase
import org.fcitx.fcitx5.android.data.quickphrase.CustomQuickPhrase
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.fcitx.fcitx5.android.ui.main.LogActivity
import org.fcitx.fcitx5.android.ui.main.CropImageActivity
import org.fcitx.fcitx5.android.ui.main.settings.ProgressFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.ui.main.settings.theme.CustomThemeActivity
import org.fcitx.fcitx5.android.ui.main.settings.theme.FittedKeyboardPreviewView
import org.fcitx.fcitx5.android.ui.main.settings.theme.ThemeSettingsFragment
import org.fcitx.fcitx5.android.ui.setup.SetupActivity
import org.fcitx.fcitx5.android.utils.config.ConfigDescriptor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Physical-device acceptance of real routes and user-visible controls; no system settings changed. */
class UiScreensTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var connection: FcitxConnection
    private lateinit var activity: MainActivity
    private val extraActivities = mutableListOf<Activity>()
    private var notificationPrompt = true
    private var hideKeys = true
    private var oldNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    private var oldFollowSystem = true
    private var oldTheme = ThemePreset.PixelDark as org.fcitx.fcitx5.android.data.theme.Theme
    private var oldPhrases = emptyArray<PinyinCustomPhrase>()
    private val screenshots get() = File(context.filesDir, "qa-ui-screens").apply { mkdirs() }
    private val image get() = File(context.filesDir, "qa-ui-landscape.png")
    private val phraseFile get() = File(context.filesDir, "qa-ui-phrases.mb")

    @Test fun largeKeyGlyphsRenderWithoutClipping() = ui {
        val density = context.resources.displayMetrics.density
        val width = (96 * density).toInt()
        val height = (60 * density).toInt()
        for (label in "0123456789".map(Char::toString) + listOf("Aa", "京")) {
            val view = AutoScaleTextView(context).apply {
                includeFontPadding = false
                typeface = ResourcesCompat.getFont(context, R.font.noto_sans_sc)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36f)
                setTextColor(Color.WHITE)
                text = label
            }
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val expected = Rect().also { view.paint.getTextBounds(label, 0, label.length, it) }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var first = height
            var last = -1
            for (y in 0 until height) for (x in 0 until width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 127) {
                    first = minOf(first, y)
                    last = maxOf(last, y)
                }
            }
            assertTrue("$label loses ink at the key edge", last - first + 1 >= expected.height() - 2)
            assertTrue("$label touches the bottom clip", last < height - 2)
            bitmap.recycle()
        }
    }

    private fun <T> ui(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) fail(message)
            SystemClock.sleep(50)
        }
        instrumentation.waitForIdleSync()
    }

    @Before fun prepare() = runBlocking {
        check(context.packageName == "org.fcitx.fcitx5.android.debug")
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        connection = FcitxDaemon.connect(javaClass.name)
        withTimeout(45_000) { connection.runOnReady { enabledIme() } }
        notificationPrompt = AppPrefs.getInstance().internal.needNotifications.getValue()
        hideKeys = AppPrefs.getInstance().advanced.hideKeyConfig.getValue()
        AppPrefs.getInstance().internal.needNotifications.setValue(false)
        AppPrefs.getInstance().advanced.hideKeyConfig.setValue(false)
        oldNightMode = AppCompatDelegate.getDefaultNightMode()
        oldFollowSystem = ThemeManager.prefs.followSystemDayNightTheme.getValue()
        oldTheme = ThemeManager.prefs.normalModeTheme.getValue()
        oldPhrases = CustomPhraseManager.load() ?: emptyArray()
        CustomPhraseManager.save(oldPhrases + arrayOf(
            PinyinCustomPhrase("qauinh", 1, "你好"), PinyinCustomPhrase("qauixx", 1, "谢谢"), PinyinCustomPhrase("qauizj", 1, "再见")))
        ui {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeManager.prefs.followSystemDayNightTheme.setValue(false)
            ThemeManager.setNormalModeTheme(ThemePreset.HaokeLight)
        }
        phraseFile.writeText("hello 你好\nthanks 谢谢\nbye 再见\n")
        Bitmap.createBitmap(1080, 600, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.rgb(172, 205, 232))
                drawRect(0f, 300f, 1080f, 600f, android.graphics.Paint().apply { color = Color.rgb(80, 139, 145) })
            }
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_RUN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ui { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        awaitPage()
    }

    @After fun restore() = runBlocking {
        ui {
            extraActivities.asReversed().forEach { if (!it.isFinishing) it.finish() }
            if (::activity.isInitialized) activity.finish()
        }
        instrumentation.waitForIdleSync()
        CustomPhraseManager.save(oldPhrases)
        ui {
            ThemeManager.setNormalModeTheme(oldTheme)
            ThemeManager.prefs.followSystemDayNightTheme.setValue(oldFollowSystem)
            AppCompatDelegate.setDefaultNightMode(oldNightMode)
        }
        AppPrefs.getInstance().advanced.hideKeyConfig.setValue(hideKeys)
        AppPrefs.getInstance().internal.needNotifications.setValue(notificationPrompt)
        FcitxDaemon.disconnect(javaClass.name)
    }

    private fun fragment(): Fragment? =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment

    private fun awaitPage() {
        waitUntil("Page did not become visible") { ui {
            val f = fragment() ?: return@ui false
            val v = f.view ?: return@ui false
            v.width > 0 && v.height > 0 && activity.hasWindowFocus() &&
                (f !is ProgressFragment || (v as? ViewGroup)?.childCount != 0) &&
                (f !is PreferenceFragmentCompat || (f.listView.adapter?.itemCount ?: 0) > 0)
        } }
        SystemClock.sleep(280) // Capture the settled native transition, not its transparent first frame.
    }

    private fun open(route: SettingsRoute) {
        val expected = when (route) {
            SettingsRoute.Index -> "MainFragment"
            SettingsRoute.VirtualKeyboard -> "KeyboardSettingsFragment"
            SettingsRoute.CandidatesWindow -> "CandidatesSettingsFragment"
            SettingsRoute.Clipboard -> "ClipboardSettingsFragment"
            SettingsRoute.Symbol -> "SymbolSettingsFragment"
            SettingsRoute.Extensions -> "BundledExtensionsFragment"
            SettingsRoute.Advanced -> "AdvancedSettingsFragment"
            SettingsRoute.License -> "LicensesFragment"
            is SettingsRoute.ListConfig -> "ListFragment"
            is SettingsRoute.PinyinDict -> "PinyinDictionaryFragment"
            is SettingsRoute.Punctuation -> "PunctuationEditorFragment"
            SettingsRoute.TableInputMethods -> "TableInputMethodFragment"
            else -> route.javaClass.simpleName + "Fragment"
        }
        ui { activity.startActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_RUN).putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, route)) }
        waitUntil("Wrong route: $expected") { ui { fragment()?.javaClass?.simpleName == expected } }
        awaitPage()
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        var observedPackage: String? = null
        waitUntil("Refuse to capture another app: $name, observed=$observedPackage") {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            observedPackage = root?.packageName?.toString()
            observedPackage == context.packageName
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(screenshots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA UI captured $name ${bitmap.width}x${bitmap.height}")
        bitmap.recycle()
    }

    @Test fun everyFunctionalPageRendersOnDevice() = runBlocking {
        val raw = connection.runOnReady { getGlobalConfig() }
        val top = ConfigDescriptor.parseTopLevel(raw["desc"]).getOrElse { throw it }
        val hotkey = top.values.filterIsInstance<ConfigDescriptor.ConfigCustom>().first { it.name == "Hotkey" }
        val list = hotkey.customTypeDef!!.values.first { it.name == "TriggerKeys" }
        val routes = listOf(
            "01-home" to SettingsRoute.Index,
            "02-global" to SettingsRoute.GlobalConfig,
            "03-input-methods" to SettingsRoute.InputMethodList,
            "04-input-config" to SettingsRoute.InputMethodConfig("拼音", "pinyin"),
            "05-addons" to SettingsRoute.AddonList,
            "06-addon-config" to SettingsRoute.AddonConfig("标点", "punctuation"),
            "07-theme" to SettingsRoute.Theme,
            "09-keyboard" to SettingsRoute.VirtualKeyboard,
            "10-candidates" to SettingsRoute.CandidatesWindow,
            "11-clipboard" to SettingsRoute.Clipboard,
            "12-symbols" to SettingsRoute.Symbol,
            "13-extensions" to SettingsRoute.Extensions,
            "14-external-plugins" to SettingsRoute.Plugin,
            "15-advanced" to SettingsRoute.Advanced,
            "16-developer" to SettingsRoute.Developer,
            "17-about" to SettingsRoute.About,
            "18-licenses" to SettingsRoute.License,
            "19-list-config" to SettingsRoute.ListConfig(raw["cfg"]["Hotkey"]["TriggerKeys"], list),
            "20-pinyin-dictionaries" to SettingsRoute.PinyinDict(),
            "21-punctuation" to SettingsRoute.Punctuation("中文标点"),
            "22-quickphrase-files" to SettingsRoute.QuickPhraseList,
            "23-quickphrase-entries" to SettingsRoute.QuickPhraseEdit(CustomQuickPhrase(phraseFile)),
            "24-tables" to SettingsRoute.TableInputMethods,
            "25-custom-phrases" to SettingsRoute.PinyinCustomPhrase
        )
        for ((name, route) in routes) {
            open(route)
            capture(name)
            if (route == SettingsRoute.Theme) {
                ui { descendants(activity.window.decorView).filterIsInstance<TabLayout>().first().getTabAt(1)!!.select() }
                awaitPage()
                capture("08-theme-config")
            }
        }
        val setup = instrumentation.startActivitySync(Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SetupActivity
        extraActivities += setup
        for (position in 0..1) {
            ui { setup.findViewById<ViewPager2>(R.id.viewpager).setCurrentItem(position, false) }
            waitUntil("Setup page not visible") { ui { setup.hasWindowFocus() } }
            SystemClock.sleep(250)
            capture(if (position == 0) "26-setup-enable" else "27-setup-select")
        }
        ui { setup.finish() }
        val custom = instrumentation.startActivitySync(Intent(context, CustomThemeActivity::class.java)
            .putExtra(CustomThemeActivity.ORIGIN_THEME, ThemePreset.TransparentDark.deriveCustomBackground("qa-ui", image.path, image.path))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CustomThemeActivity
        extraActivities += custom
        waitUntil("Custom theme not visible") { ui { custom.hasWindowFocus() } }
        SystemClock.sleep(300); capture("28-custom-theme"); ui { custom.finish() }
        val crop = instrumentation.startActivitySync(Intent(context, CropImageActivity::class.java)
            .putExtra(CropImageActivity.CROP_OPTIONS, CropImageActivity.CropOption.Edit(1080, 600, Uri.fromFile(image)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CropImageActivity
        extraActivities += crop
        waitUntil("Crop not visible") { ui { crop.hasWindowFocus() } }
        SystemClock.sleep(500); capture("29-crop"); ui { crop.finish() }
        val logs = instrumentation.startActivitySync(Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LogActivity
        extraActivities += logs
        waitUntil("Log not visible") { ui { logs.hasWindowFocus() } }
        SystemClock.sleep(300); capture("30-logs"); ui { logs.finish() }
        assertEquals("Every functional page must have a physical screenshot", 30,
            screenshots.listFiles()!!.count { it.name.matches(Regex("[0-9]{2}-.+\\.png")) })
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun clickLabel(resource: Int) = ui {
        val label = context.getString(resource)
        var view: View = descendants(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == label && it.isShown }
        while (!view.hasOnClickListeners()) view = view.parent as View
        check(view.performClick())
    }

    @Test fun settingSwitchAndNumericCancelKeepTheirRealSemantics() {
        val prefs = AppPrefs.getInstance()
        val old = prefs.advanced.disableAnimation.getValue()
        try {
            open(SettingsRoute.Advanced)
            clickLabel(R.string.disable_animation)
            waitUntil("Switch did not update its stored value") { prefs.advanced.disableAnimation.getValue() != old }
            open(SettingsRoute.Index)
            open(SettingsRoute.Advanced)
            assertEquals(!old, prefs.advanced.disableAnimation.getValue())
            val limit = prefs.clipboard.clipboardHistoryLimit.getValue()
            open(SettingsRoute.Clipboard)
            clickLabel(R.string.clipboard_limit)
            SystemClock.sleep(200); capture("dialog-numeric")
            val cancel = context.getString(android.R.string.cancel)
            val node = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(cancel).first { it.text?.toString() == cancel }
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil("Dialog did not close") { ui { activity.hasWindowFocus() } }
            assertEquals(limit, prefs.clipboard.clipboardHistoryLimit.getValue())
        } finally { prefs.advanced.disableAnimation.setValue(old) }
    }

    @Test fun themeGalleryPreviewFillsItsCard() {
        open(SettingsRoute.Theme)
        ui {
            val frame = descendants(activity.window.decorView).filterIsInstance<FittedKeyboardPreviewView>().first()
            val keyboard = frame.getChildAt(0)
            assertEquals("No left letterbox", 0, keyboard.left)
            assertEquals("No right letterbox", frame.width.toFloat(), keyboard.width * keyboard.scaleX, 1f)
            assertEquals("All rows fit vertically", frame.height.toFloat(), keyboard.height * keyboard.scaleY, 1f)
            assertTrue("Gallery leaves room for its theme controls", frame.height <= 220 * context.resources.displayMetrics.density + 1)
        }
        capture("theme-full-width")
    }

    @Test fun imageThemePreviewFillsWidthAndRetainsCropAspect() {
        val custom = instrumentation.startActivitySync(Intent(context, CustomThemeActivity::class.java)
            .putExtra(CustomThemeActivity.ORIGIN_THEME, ThemePreset.TransparentDark.deriveCustomBackground("qa-preview", image.path, image.path))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CustomThemeActivity
        extraActivities += custom
        waitUntil("Image editor did not settle") { ui { custom.hasWindowFocus() } }
        SystemClock.sleep(350)
        ui {
            val frame = descendants(custom.window.decorView).filterIsInstance<FittedKeyboardPreviewView>().first()
            val keyboard = frame.getChildAt(0)
            assertEquals(0, keyboard.left)
            assertEquals(frame.width.toFloat(), keyboard.width * keyboard.scaleX, 1f)
            assertEquals("Crop/image preview keeps the full aspect ratio", frame.height.toFloat(), keyboard.height * keyboard.scaleY, 1f)
        }
        capture("image-preview-full-width")
    }

    @Test fun keyboardColorModesPersistAndReturnToSystem() {
        open(SettingsRoute.Theme)
        ui { descendants(activity.window.decorView).filterIsInstance<TabLayout>().first().getTabAt(1)!!.select() }
        awaitPage()
        fun mode(): ListPreference =
            checkNotNull(fragment()).childFragmentManager.fragments.filterIsInstance<ThemeSettingsFragment>().first()
                .findPreference<ListPreference>(ThemeSettingsFragment.COLOR_MODE_KEY)!!
        ui {
            val settings = checkNotNull(fragment()).childFragmentManager.fragments.filterIsInstance<ThemeSettingsFragment>().first()
            assertSame("Color mode must be the first visible setting", mode(), settings.preferenceScreen.getPreference(0))
        }
        fun dialogClick(resource: Int) {
            val label = context.getString(resource)
            waitUntil("Dialog entry is missing: $label") {
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.any { it.text?.toString() == label } == true
            }
            var node = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).first { it.text?.toString() == label }
            while (!node.isClickable) node = checkNotNull(node.parent)
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil("Dialog did not close") { ui { activity.hasWindowFocus() } }
        }
        fun select(resource: Int, value: String) {
            clickLabel(R.string.haoke_keyboard_color_mode)
            dialogClick(resource)
            waitUntil("Mode did not apply: $value") { ui { mode().value == value } }
        }
        val originalDark = ThemeManager.prefs.darkModeTheme.getValue()
        try {
            // Existing versions permit a light palette in the dark slot (and vice versa).
            ThemeManager.prefs.darkModeTheme.setValue(ThemePreset.HaokeLight)
            select(R.string.haoke_mode_dark, "dark")
            assertFalse(ThemeManager.prefs.followSystemDayNightTheme.getValue())
            assertTrue(ThemeManager.activeTheme.isDark)
            assertEquals("The old system palette is not reset", ThemePreset.HaokeLight, ThemeManager.prefs.darkModeTheme.getValue())
        } finally { ThemeManager.prefs.darkModeTheme.setValue(originalDark) }
        clickLabel(R.string.haoke_keyboard_color_mode)
        SystemClock.sleep(250)
        capture("theme-mode-dialog")
        dialogClick(android.R.string.cancel)
        assertEquals("Cancel keeps the previous mode", "dark", ui { mode().value })
        val originalLight = ThemeManager.prefs.lightModeTheme.getValue()
        try {
            ThemeManager.prefs.lightModeTheme.setValue(ThemePreset.HaokeDark)
            select(R.string.haoke_mode_light, "light")
            assertFalse(ThemeManager.activeTheme.isDark)
            assertEquals(ThemePreset.HaokeDark, ThemeManager.prefs.lightModeTheme.getValue())
        } finally { ThemeManager.prefs.lightModeTheme.setValue(originalLight) }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            ui { activity.recreate() }
            activity = checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 15_000)) as MainActivity
            awaitPage()
            assertEquals("Manual choice is derived from durable existing settings", "light", ui { mode().value })
            select(R.string.haoke_mode_system, "system")
            assertTrue(ThemeManager.prefs.followSystemDayNightTheme.getValue())
            val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            assertEquals(night, ThemeManager.activeTheme.isDark)
            assertEquals("system", ui { mode().value })
            assertFalse(androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).contains(ThemeSettingsFragment.COLOR_MODE_KEY))
        } finally { instrumentation.removeMonitor(monitor) }
        capture("theme-mode-follows-system")
    }

    @Test fun themeConfigurationSurvivesActivityRecreation() {
        open(SettingsRoute.Theme)
        ui { descendants(activity.window.decorView).filterIsInstance<TabLayout>().first().getTabAt(1)!!.select() }
        awaitPage()
        val selected = ThemeManager.activeTheme.name
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            ui { activity.recreate() }
            activity = checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 15_000)) as MainActivity
            awaitPage()
            assertEquals(selected, ThemeManager.activeTheme.name)
            assertEquals(1, ui { descendants(activity.window.decorView).filterIsInstance<TabLayout>().first().selectedTabPosition })
            capture("theme-after-recreate")
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun storedThemeOptionsKeepTheirValuesWithNewDefaults() {
        val preferences = context.getSharedPreferences("qa-ui-upgrade-seed", 0)
        assertTrue(preferences.edit().putBoolean("key_border", false).putInt("key_radius", 4)
            .putString("light_mode_theme", "MaterialLight").commit())
        val values = ThemePrefs(preferences)
        assertFalse(values.keyBorder.getValue())
        assertEquals(4, values.keyRadius.getValue())
        assertEquals("MaterialLight", values.lightModeTheme.getValue().name)
    }
}
