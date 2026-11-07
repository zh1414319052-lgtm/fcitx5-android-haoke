/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardDatabase
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClipboardPruningTest {
    private lateinit var db: ClipboardDatabase
    private val dao get() = db.clipboardDao()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ClipboardDatabase::class.java
        ).build()
    }
    @After fun close() { db.close() }

    private suspend fun add(text: String, time: Long, pinned: Boolean = false): Int =
        dao.get(dao.insert(ClipboardEntry(text = text, timestamp = time, pinned = pinned)))!!.id

    @Test fun recopyingAnOlderEntryKeepsTheThreeMostRecent() = runBlocking {
        val a = add("A", 1); val b = add("B", 2); val c = add("C", 3)
        dao.updateTime(b, 4)
        val d = add("D", 5)
        dao.pruneUnpinned(3)
        assertNull(dao.get(a))
        assertEquals(setOf(b, c, d), dao.findAllIds().toSet())
        assertEquals(3, dao.itemCount())
    }

    @Test fun equalTimestampsHaveADeterministicExactLimit() = runBlocking {
        val ids = (1..5).map { add("item-$it", 10) }
        dao.pruneUnpinned(3)
        assertEquals(ids.takeLast(3).toSet(), dao.findAllIds().toSet())
    }

    @Test fun zeroLimitKeepsOnlyPinnedEntries() = runBlocking {
        val pinned = add("pinned", 0, true)
        add("normal", 1); add("future-time", Long.MAX_VALUE)
        dao.pruneUnpinned(0)
        assertEquals(setOf(pinned), dao.findAllIds().toSet())
    }

    @Test fun reducingTheLimitDoesNotResurrectDeletedRows() = runBlocking {
        val ids = (1..6).map { add("item-$it", it.toLong()) }
        dao.markAsDeleted(ids.last())
        dao.pruneUnpinned(2)
        assertEquals(setOf(ids[3], ids[4]), dao.findAllIds().toSet())
        dao.pruneUnpinned(10)
        assertEquals(2, dao.itemCount())
    }

    @Test fun pinUnpinAndUndoKeepTheirExistingMeaning() = runBlocking {
        val pinned = add("pinned", 1, true)
        val latest = add("latest", 2)
        dao.pruneUnpinned(1)
        assertEquals(2, dao.itemCount())
        dao.markAsDeleted(latest)
        dao.undoDelete(latest)
        assertEquals("latest", dao.get(latest)?.text)
        dao.updatePinStatus(pinned, false)
        dao.pruneUnpinned(1)
        assertNull(dao.get(pinned))
        assertEquals(1, dao.itemCount())
    }

    @Test fun noOverflowAndEmptyHistoryAreSafe() = runBlocking {
        dao.pruneUnpinned(3)
        val id = add("keep", 1)
        dao.pruneUnpinned(3)
        assertEquals(setOf(id), dao.findAllIds().toSet())
    }
}
