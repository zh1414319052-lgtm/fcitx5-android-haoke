/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.core.Fcitx
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

class FcitxTest {

    private companion object {

        lateinit var fcitx: Fcitx
        // Commit events must not be overwritten by the following candidate update.
        val fcitxEventChannel = Channel<FcitxEvent<*>>(capacity = Channel.UNLIMITED)
        val scope = MainScope()

        @BeforeClass
        @JvmStatic
        fun setup() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            fcitx = Fcitx(context)

            // forward to our channel for point to point consuming
            fcitx.eventFlow
                .onEach { fcitxEventChannel.send(it) }
                .launchIn(scope)
            fcitx.start()

            // wait fcitx started
            runBlocking {
                receiveFirst<FcitxEvent.ReadyEvent>()
                // Current native frontend uses per-editor contexts. Headless tests
                // need the same activation/focus sequence as onBindInput/onStartInput.
                fcitx.activate(android.os.Process.myUid(), context.packageName)
                fcitx.focus(true)
                fcitx.setEnabledIme(arrayOf("pinyin"))
                fcitx.setGlobalConfig(
                    RawConfig(
                        arrayOf(
                            RawConfig(
                                "Behavior", arrayOf(
                                    RawConfig("ShowInputMethodInformation", false)
                                )
                            )
                        )
                    )
                )
            }
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            fcitx.stop()
        }

        private suspend fun sendString(str: String) {
            str.forEach { c ->
                fcitx.sendKey(c)
                delay(50)
            }
        }

        private suspend inline fun <reified T : FcitxEvent<*>> receiveFirst(): T? =
            withTimeout(15_000) {
                fcitxEventChannel.receiveAsFlow().mapNotNull { it as? T }.firstOrNull()
            }

        private suspend fun receiveFirstCandidateList() =
            receiveFirst<FcitxEvent.CandidateListEvent>()

        private suspend fun receiveFirstCommitString() =
            receiveFirst<FcitxEvent.CommitStringEvent>()

        private suspend fun receiveFirstPreedit() = receiveFirst<FcitxEvent.ClientPreeditEvent>()

        private suspend fun receiveFirstInputPanelAux() =
            receiveFirst<FcitxEvent.InputPanelEvent>()

    }

    private var enabledIme: List<String> = listOf()

    @Before
    fun saveEnabledIME() = runBlocking {
        enabledIme = fcitx.enabledIme().map { it.uniqueName }
    }

    @After
    fun restoreEnabledIME() = runBlocking {
        fcitx.setEnabledIme(enabledIme.toTypedArray())
    }

    @Test
    fun testWbx(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf("wbx"))
        sendString("wqvb")
        val expected = "你好"
        fcitx.select(0)
        val commitString = receiveFirstCommitString()?.data?.text
        Timber.i("commitString is $commitString")
        Assert.assertEquals(expected, commitString)
        fcitx.reset()
    }

    @Test
    fun testPinyin(): Unit = runBlocking {
        fcitx.setEnabledIme(arrayOf("pinyin"))
        sendString("nihaoshijie")
        val expected = "你好世界"
        // Dictionary revisions and learning can change rank; verify the intended
        // phrase is offered and can be committed, without assuming it ranks first.
        val candidates = fcitx.getCandidates(0, 100)
        val index = candidates.indexOfFirst { it.text == expected }
        if (index >= 0) {
            fcitx.select(index)
        } else {
            // The sentence candidate is model-dependent. Selecting its two words
            // from the same preedit must still commit the exact original sentence.
            val hello = candidates.indexOfFirst { it.text == "你好" }
            Assert.assertTrue("你好 must be offered", hello >= 0)
            fcitx.select(hello)
            val world = fcitx.getCandidates(0, 100).indexOfFirst { it.text == "世界" }
            Assert.assertTrue("世界 must be offered after 你好", world >= 0)
            fcitx.select(world)
        }
        val commitString = receiveFirstCommitString()?.data?.text
        Timber.i("commitString is $commitString")
        Assert.assertEquals(expected, commitString)
        fcitx.reset()
    }

    @Test
    fun testInputPanelStatus(): Unit = runBlocking {
        fcitx.reset()
        Timber.i("after first reset: ${fcitx.isEmpty()}")
        Assert.assertEquals(true, fcitx.isEmpty())
        fcitx.sendKey('a')
        do {
            val list = receiveFirstCandidateList()
        } while (list!!.data.candidates.isEmpty())
        Timber.i("after sending 'a': ${fcitx.isEmpty()}")
        Assert.assertEquals(false, fcitx.isEmpty())
        fcitx.reset()
        do {
            val list = receiveFirstCandidateList()
        } while (list!!.data.candidates.isNotEmpty())
        Timber.i("after second reset: ${fcitx.isEmpty()}")
        Assert.assertEquals(true, fcitx.isEmpty())
    }

}
