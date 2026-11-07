/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.clipboard

/** Bounded preview only. Paste, edit and share retain the original entry. */
object ClipboardPreview {
    fun excerpt(text: String, mask: Boolean = false, lines: Int = 4, chars: Int = 128): String {
        if (lines <= 0 || chars <= 0) return ""
        // Do not search an entire multi-megabyte paragraph just to display four lines.
        val budget = (lines.toLong() * chars).coerceAtMost(512).toInt()
        val prefix = text.safePrefix(budget)
        val preview = prefix.lineSequence().take(lines).map { line ->
            val head = line.safePrefix(chars)
            if (mask) "•".repeat(head.codePointCount(0, head.length))
            else head + if (head.length < line.length) "…" else ""
        }.joinToString("\n")
        return if (!mask && prefix.length < text.length && !preview.endsWith("…")) "$preview…" else preview
    }

    private fun String.safePrefix(limit: Int): String {
        var end = limit.coerceAtMost(length)
        if (end > 0 && end < length && this[end - 1].isHighSurrogate() && this[end].isLowSurrogate()) end--
        return substring(0, end)
    }
}
