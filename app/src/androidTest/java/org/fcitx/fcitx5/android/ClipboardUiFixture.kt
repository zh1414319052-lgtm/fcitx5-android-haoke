/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardDatabase
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic history for manual UI acceptance; never reads or replaces the system clipboard. */
class ClipboardUiFixture {
    @Test fun prepareSyntheticHistory(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "org.fcitx.fcitx5.android.debug")
        val prefs = AppPrefs.getInstance().clipboard
        prefs.clipboardHistoryLimit.setValue(3)
        prefs.clipboardMaskSensitive.setValue(true)
        prefs.clipboardListening.setValue(true)
        val db = Room.databaseBuilder(context, ClipboardDatabase::class.java, "clbdb").build()
        try {
            val dao = db.clipboardDao()
            val base = System.currentTimeMillis()
            suspend fun entry(text: String, time: Long, pinned: Boolean = false, sensitive: Boolean = false): Int {
                dao.find(text, sensitive)?.let {
                    dao.updateTime(it.id, time)
                    dao.updatePinStatus(it.id, pinned)
                    return it.id
                }
                return dao.get(dao.insert(ClipboardEntry(text = text, timestamp = time, pinned = pinned, sensitive = sensitive)))!!.id
            }
            val a = entry("QA A", base)
            val b = entry("QA B", base + 1)
            val c = entry("QA C", base + 2)
            dao.updateTime(b, base + 3)
            val d = entry("QA D", base + 4)
            dao.pruneUnpinned(3)
            assertTrue(dao.get(a) == null && listOf(b, c, d).all { dao.get(it) != null })
            entry("QA_LONG_" + "x".repeat(2048) + "_END", base + 5, pinned = true)
            entry("QA_PRIVATE_TEST", base + 6, pinned = true, sensitive = true)
        } finally { db.close() }
    }
}
