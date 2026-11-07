/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.extensions.BundledExtensions
import org.fcitx.fcitx5.android.extensions.BundledLinkCleaner
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

class BundledExtensionsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var connection: FcitxConnection
    private var originalIme = emptyList<String>()
    private var originalAddons = emptyMap<String, Boolean>()
    private var originalCleaner = false
    private var originalNotificationPrompt = false
    private lateinit var activity: MainActivity

    @Before fun connect() = runBlocking {
        connection = FcitxDaemon.connect(javaClass.name)
        withTimeout(30_000) {
            connection.runOnReady {
                originalIme = enabledIme().map { it.uniqueName }
                originalAddons = addons().filter { it.uniqueName in BundledExtensions.ids }.associate { it.uniqueName to it.enabled }
            }
        }
        originalCleaner = AppPrefs.getInstance().internal.bundledClearUrls.getValue()
        originalNotificationPrompt = AppPrefs.getInstance().internal.needNotifications.getValue()
        AppPrefs.getInstance().internal.needNotifications.setValue(false)
        // Keep this headless native test's host visible on physical Android 16;
        // no global screen/battery setting is changed.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_RUN).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @After fun restore() = runBlocking {
        try {
            connection.runOnReady { reset(); setAddonState(originalAddons.keys.toTypedArray(), originalAddons.values.toBooleanArray()) }
            FcitxDaemon.restartFcitx()
            withTimeout(30_000) { connection.runOnReady { setEnabledIme(originalIme.toTypedArray()); save() } }
            AppPrefs.getInstance().internal.bundledClearUrls.setValue(originalCleaner)
            AppPrefs.getInstance().internal.needNotifications.setValue(originalNotificationPrompt)
        } finally {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
            FcitxDaemon.disconnect(javaClass.name)
        }
    }

    private fun input(id: String, keys: String, script: Regex, convert: Boolean = false) = runBlocking {
        BundledExtensions.change(connection, id, true)
        val output = StringBuffer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val events = connection.runOnReady { eventFlow }
        val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event -> when (event) {
                is FcitxEvent.CommitStringEvent -> output.append(event.data.text)
                is FcitxEvent.ClientPreeditEvent -> output.append(event.data.strings.joinToString(""))
                is FcitxEvent.CandidateListEvent -> output.append(event.data.candidates.joinToString { it.text })
                is FcitxEvent.InputPanelEvent -> output.append(event.data.preedit.strings.joinToString(""))
                else -> Unit
            } }
        }
        try {
            connection.runOnReady {
                activate(android.os.Process.myUid(), context.packageName)
                focus(true); reset(); activateIme(id)
                assertEquals(id, currentIme().uniqueName)
            }
            // First activation may deploy RIME's bundled schemas asynchronously.
            withTimeout(40_000) {
                while (true) {
                    connection.runOnReady {
                        reset()
                        for (key in keys) { sendKey(key); delay(25) }
                        if (convert) sendKey(' ')
                    }
                    delay(100)
                    if (script.containsMatchIn(output.toString())) break
                    delay(250)
                }
            }
            assertTrue("$id must produce its script from real key events: $output", script.containsMatchIn(output.toString()))
            println("Bundled engine $id processed '$keys': ${output.toString().take(100)}")
        } finally { collector.cancel(); scope.cancel(); connection.runOnReady { reset() } }
    }

    @Test fun rimeUsesBundledSchemas() = input("rime", "nihao", Regex("[\u3400-\u9fff]"))
    @Test fun cantoneseUsesBundledDictionary() = input("jyutping", "neihou", Regex("[\u3400-\u9fff]"))
    @Test fun chewingProcessesZhuyinKeys() = input("chewing", "su3cl3", Regex("[\u3100-\u312f\u3400-\u9fff]"))
    @Test fun japaneseProcessesKanaAndConversion() = input("anthy", "nihon", Regex("[\u3040-\u30ff\u3400-\u9fff]"), true)
    @Test fun koreanProcessesHangulKeys() = input("hangul", "gksrmf", Regex("[\u1100-\u11ff\uac00-\ud7af]"))
    @Test fun vietnameseProcessesTelexKeys() = input("unikey", "tieengs", Regex("[\u00c0-\u024f\u1e00-\u1eff]"))
    @Test fun sinhalaProcessesSayuraKeys() = input("sayura", "ka", Regex("[\u0d80-\u0dff]"))

    @Test fun disablingTheLastLanguageFallsBackAndPreservesUserFiles() = runBlocking {
        BundledExtensions.change(connection, "rime", true)
        val marker = File(context.getExternalFilesDir(null) ?: context.filesDir, "data/rime/qa-preserve.custom.yaml")
        marker.parentFile!!.mkdirs()
        val data = "# Synthetic QA configuration\npatch:\n  menu/page_size: 7\n"
        marker.writeText(data)
        connection.runOnReady { setEnabledIme(arrayOf("rime")); activate(android.os.Process.myUid(), context.packageName); focus(true); activateIme("rime") }
        BundledExtensions.change(connection, "rime", false)
        assertFalse(BundledExtensions.states(connection).getValue("rime"))
        assertTrue(connection.runOnReady { enabledIme().isNotEmpty() && enabledIme().none { it.addon == "rime" } })
        assertEquals(data, marker.readText())
        BundledExtensions.change(connection, "rime", true)
        assertTrue(BundledExtensions.states(connection).getValue("rime"))
        assertEquals(data, marker.readText())
    }

    @Test fun engineSwitchPersistsAcrossNativeRestartAndPreservesOtherLanguages() = runBlocking {
        val before = connection.runOnReady { enabledIme().map { it.uniqueName } }
        BundledExtensions.change(connection, "jyutping", true)
        FcitxDaemon.restartFcitx()
        assertTrue(withTimeout(30_000) { BundledExtensions.states(connection).getValue("jyutping") })
        assertTrue(connection.runOnReady { enabledIme().map { it.uniqueName }.containsAll(before) })
        BundledExtensions.change(connection, "jyutping", false)
        assertTrue(connection.runOnReady { enabledIme().map { it.uniqueName }.containsAll(before.filterNot { it == "jyutping" }) })
    }

    @Test fun advancedAddonDisableUsesTheSameSafeFallback() = runBlocking {
        BundledExtensions.change(connection, "jyutping", true)
        connection.runOnReady {
            setEnabledIme(arrayOf("jyutping"))
            setAddonState(arrayOf("jyutping"), booleanArrayOf(false))
            assertTrue(enabledIme().isNotEmpty())
            assertTrue(enabledIme().none { it.addon == "jyutping" })
        }
        assertFalse(BundledExtensions.states(connection).getValue("jyutping"))
    }

    @Test fun enablingCantoneseAlsoEnablesItsRequiredPunctuationModule() = runBlocking {
        val punctuation = connection.runOnReady { addons().first { it.uniqueName == "punctuation" }.enabled }
        try {
            connection.runOnReady { setAddonState(arrayOf("jyutping", "punctuation"), booleanArrayOf(false, false)) }
            FcitxDaemon.restartFcitx()
            BundledExtensions.change(connection, "jyutping", true)
            assertTrue(connection.runOnReady { addons().first { it.uniqueName == "punctuation" }.enabled })
            assertTrue(BundledExtensions.states(connection).getValue("jyutping"))
        } finally {
            connection.runOnReady { setAddonState(arrayOf("punctuation"), booleanArrayOf(punctuation)) }
        }
    }

    @Test fun clearUrlsCanBeEnabledAndDisabledWithoutTouchingTheSystemClipboard() = runBlocking {
        val source = "https://example.com/article?id=7&utm_source=qa&utm_medium=test"
        BundledExtensions.change(connection, "clipboard_filter", false)
        assertEquals(source, BundledLinkCleaner.transform(source))
        BundledExtensions.change(connection, "clipboard_filter", true)
        val cleaned = BundledLinkCleaner.transform(source)
        assertTrue(cleaned.contains("id=7"))
        assertFalse(cleaned.contains("utm_source"))
        assertFalse(cleaned.contains("utm_medium"))
        assertEquals("ordinary synthetic text", BundledLinkCleaner.transform("ordinary synthetic text"))
        BundledExtensions.change(connection, "clipboard_filter", false)
        assertEquals(source, BundledLinkCleaner.transform(source))
    }

    @Test fun newSettingsHaveChineseAndEnglishResources() {
        for ((locale, expected) in listOf(Locale.SIMPLIFIED_CHINESE to "语言与扩展", Locale.ENGLISH to "Languages & extensions")) {
            val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) }
            assertEquals(expected, context.createConfigurationContext(config).getString(R.string.language_extensions))
        }
    }
}
