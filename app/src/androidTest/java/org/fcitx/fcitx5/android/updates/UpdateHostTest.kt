/* SPDX-License-Identifier: GPL-3.0-only */
package org.fcitx.fcitx5.android.updates

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

/** Actual Fcitx settings host, isolated QA package and synthetic offer; no production publishing. */
class UpdateHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var controller: UpdateUiController
    private val backend = Backend()
    private var originalPrompt = true
    private class Backend : UpdateUiController.Backend {
        val checks = AtomicInteger()
        val laterCalls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        @Volatile var permitted = false
        @Volatile var disposed = false
        @Volatile var kind = "available"
        override fun setAllowed(value: Boolean) { permitted = value }
        override fun check(manual: Boolean): HaokeUpdateClient.Result {
            checks.incrementAndGet(); entered.countDown()
            release.await(8, TimeUnit.SECONDS)
            if (kind != "available") return HaokeUpdateClient.Result.kind(kind)
            // Deliberately return an offer even when cancelled: the host must discard stale UI.
            return HaokeUpdateClient.Result("available", null, 0, HaokeUpdateVerifier.Release(
                11302, "synthetic-host-test", "仅本地合成验证", "Local synthetic test only",
                "https://haoke.org/software/d000c19470bc74b6"))
        }
        override fun present(show: Runnable, manual: Boolean): Boolean { show.run(); return true }
        override fun later(): Boolean { laterCalls.incrementAndGet(); return true }
        override fun ignore() = true
        override fun pageUrlFromUserGesture(): String? = null
        override fun dispose() { disposed = true; permitted = false; release.countDown() }
    }
    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        fail(message)
    }
    private fun dialog(): AlertDialog? = UpdateUiController::class.java.getDeclaredField("dialog").let {
        it.isAccessible = true; it.get(controller) as AlertDialog?
    }
    @Before fun launch() {
        assertEquals("Only the isolated QA app may run this test", "org.fcitx.fcitx5.android.debug",
            instrumentation.targetContext.packageName)
        originalPrompt = AppPrefs.getInstance().internal.needNotifications.getValue()
        AppPrefs.getInstance().internal.needNotifications.setValue(false)
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        await("Real settings window must settle") {
            var ready = false
            ui { ready = activity.hasWindowFocus() && activity.isHaokeUpdatePageSafe() }
            ready
        }
        ui {
            val field = MainActivity::class.java.getDeclaredField("haokeUpdates").apply { isAccessible = true }
            (field.get(activity) as UpdateUiController?)?.close()
            controller = UpdateUiController(activity, backend)
            field.set(activity, controller)
            controller.onResume()
        }
        assertTrue("Safe settled host must start one check", backend.entered.await(5, TimeUnit.SECONDS))
    }
    @After fun finish() {
        backend.release.countDown()
        if (::activity.isInitialized) ui { activity.finish() }
        if (::controller.isInitialized) await("Destroy must dispose the worker/backend") { backend.disposed }
        AppPrefs.getInstance().internal.needNotifications.setValue(originalPrompt)
    }
    @Test fun repeatedManualTapsShareOneRequestAndPresentThemedOffer() {
        ui { controller.checkManually(); controller.checkManually() }
        assertEquals(1, backend.checks.get())
        backend.release.countDown()
        await("An actual settings modal must present the synthetic offer") {
            var visible = false; ui { visible = dialog()?.isShowing == true }; visible
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500) // Capture the settled native window, not its transparent entry-animation frame.
        instrumentation.uiAutomation.takeScreenshot().let { screenshot ->
            File(instrumentation.targetContext.filesDir, "qa-update-host.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
        ui {
            val modal = requireNotNull(dialog())
            assertTrue(modal.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertTrue(modal.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled)
            modal.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        }
        await("The dialog button handler must deliver Later") { backend.laterCalls.get() == 1 }
    }
    @Test fun editingCancelsAndRejectsLateOffer() {
        ui {
            val editor = EditText(activity).apply { hint = "Synthetic input only" }
            (activity.findViewById<ViewGroup>(android.R.id.content)).addView(editor,
                ViewGroup.LayoutParams(240, 120))
            editor.requestFocus()
            assertFalse("Editing immediately suspends requests", backend.permitted)
        }
        backend.release.countDown()
        SystemClock.sleep(800)
        ui { assertFalse("Late offer cannot cover an editor", dialog()?.isShowing == true) }
    }
    @Test fun backgroundCancelsAndDestroyClosesWorker() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        await("Real background transition must forbid feed access") { !backend.permitted }
        backend.release.countDown()
        SystemClock.sleep(800)
        ui { assertFalse("Late offer cannot open over another app", dialog()?.isShowing == true) }
    }
    @Test fun manualNoneShowsPageStatusAndEditingRemovesIt() {
        backend.kind = "none"
        ui { controller.checkManually() }
        backend.release.countDown()
        await("Manual result must be readable inside the settings page") {
            var shown = false
            ui {
                shown = activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    ?.text?.contains("0.1.3-haoke.1") == true
            }
            shown
        }
        ui {
            val editor = EditText(activity)
            activity.findViewById<ViewGroup>(android.R.id.content).addView(editor, ViewGroup.LayoutParams(240,120))
            editor.requestFocus()
        }
        await("Status must leave the editor unobstructed") {
            var cleared = false
            ui { cleared = activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.isShown != true }
            cleared
        }
    }
}
