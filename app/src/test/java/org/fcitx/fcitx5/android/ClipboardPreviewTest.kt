/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.clipboard.ClipboardPreview
import org.junit.Assert.*
import org.junit.Test

class ClipboardPreviewTest {
    @Test fun ordinaryTextAndEmptyTextArePreserved() {
        assertEquals("", ClipboardPreview.excerpt(""))
        assertEquals("你好\nhello", ClipboardPreview.excerpt("你好\nhello"))
    }

    @Test fun emojiAtTheBoundaryIsNotSplit() {
        val text = "a".repeat(127) + "😀" + "b".repeat(1000)
        val preview = ClipboardPreview.excerpt(text)
        assertFalse(preview.any { it.isSurrogate() })
        assertTrue(preview.endsWith("…"))
        assertEquals(1129, text.length)
        assertTrue(text.contains("😀"))
    }

    @Test fun fullPreviewBoundaryDoesNotLeaveAnUnpairedSurrogate() {
        val preview = ClipboardPreview.excerpt("a".repeat(511) + "😀" + "z")
        for (i in preview.indices) {
            if (preview[i].isHighSurrogate()) assertTrue(i + 1 < preview.length && preview[i + 1].isLowSurrogate())
            if (preview[i].isLowSurrogate()) assertTrue(i > 0 && preview[i - 1].isHighSurrogate())
        }
    }

    @Test fun multiMegabyteInputHasABoundedPreviewAndRemainsIntact() {
        val original = "x".repeat(2_000_000) + "THE_END"
        assertTrue(ClipboardPreview.excerpt(original).length <= 513)
        assertTrue(original.endsWith("THE_END"))
        assertEquals(2_000_007, original.length)
    }

    @Test fun sensitivePreviewDoesNotExposeContent() {
        val preview = ClipboardPreview.excerpt("private😀\nsecret", mask = true)
        assertTrue(preview.all { it == '•' || it == '\n' })
        assertFalse(preview.contains("private"))
    }

    @Test fun lineAndParameterBoundaries() {
        assertEquals("1\n2\n3\n4", ClipboardPreview.excerpt("1\n2\n3\n4\n5"))
        assertEquals("", ClipboardPreview.excerpt("hello", lines = 0))
        assertEquals("", ClipboardPreview.excerpt("hello", chars = -1))
    }
}
