package me.weishu.kernelsu.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Text run with SGR attributes; colors are ARGB ints, null = terminal default. */
data class AnsiSpan(val text: String, val fg: Int? = null, val bg: Int? = null, val bold: Boolean = false)

/**
 * Parses ANSI SGR (`ESC[...m`): reset, bold, 16/256/truecolor foreground and background.
 * All other escape sequences (cursor movement, erase, OSC titles, charset selection) are stripped.
 * State is carried across [parse] calls so colors spanning lines keep working.
 */
class AnsiParser {
    private var fg: Int? = null
    private var bg: Int? = null
    private var bold = false

    fun reset() { fg = null; bg = null; bold = false }

    fun parse(input: String): List<AnsiSpan> {
        val spans = ArrayList<AnsiSpan>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                val last = spans.lastOrNull()
                if (last != null && last.fg == fg && last.bg == bg && last.bold == bold) spans[spans.size - 1] = last.copy(text = last.text + text)
                else spans += AnsiSpan(text.toString(), fg, bg, bold)
                text.clear()
            }
        }
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == ESC && i + 1 < input.length && input[i + 1] == '[' -> {
                    var j = i + 2
                    while (j < input.length && input[j].code in 0x30..0x3F) j++ // parameter bytes
                    while (j < input.length && input[j].code in 0x20..0x2F) j++ // intermediate bytes
                    if (j >= input.length) { i = input.length; continue } // truncated: drop
                    if (input[j] == 'm') { flush(); applySgr(input.substring(i + 2, j)) }
                    i = j + 1
                }
                c == ESC && i + 1 < input.length && input[i + 1] == ']' -> { // OSC ... BEL or ST
                    var j = i + 2
                    while (j < input.length && input[j] != BEL && !(input[j] == ESC && j + 1 < input.length && input[j + 1] == '\\')) j++
                    i = when { j >= input.length -> input.length; input[j] == BEL -> j + 1; else -> j + 2 }
                }
                c == ESC -> { // two/three-char escapes such as ESC(B, ESC=, ESC7
                    i += if (i + 1 < input.length && input[i + 1] in "()*+#%") 3 else 2
                }
                c == '\r' || c == BEL || (c.code < 0x20 && c != '\t') -> i++
                else -> { text.append(c); i++ }
            }
        }
        flush()
        return spans
    }

    private fun applySgr(params: String) {
        val codes = if (params.isEmpty()) listOf(0) else params.split(';', ':').map { it.toIntOrNull() ?: 0 }
        var k = 0
        while (k < codes.size) {
            when (val code = codes[k]) {
                0 -> reset()
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> fg = PALETTE_16[code - 30]
                in 90..97 -> fg = PALETTE_16[code - 90 + 8]
                39 -> fg = null
                in 40..47 -> bg = PALETTE_16[code - 40]
                in 100..107 -> bg = PALETTE_16[code - 100 + 8]
                49 -> bg = null
                38, 48 -> {
                    val color: Int? = when (codes.getOrNull(k + 1)) {
                        5 -> codes.getOrNull(k + 2)?.let { k += 2; color256(it) }
                        2 -> if (k + 4 < codes.size) {
                            val (r, g, b) = Triple(codes[k + 2], codes[k + 3], codes[k + 4]); k += 4
                            rgb(r, g, b)
                        } else null
                        else -> null
                    }
                    if (code == 38) fg = color ?: fg else bg = color ?: bg
                }
            }
            k++
        }
    }

    companion object {
        private const val ESC = '\u001B'
        private const val BEL = '\u0007'

        private fun rgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or ((r.coerceIn(0, 255)) shl 16) or ((g.coerceIn(0, 255)) shl 8) or b.coerceIn(0, 255)

        val PALETTE_16: IntArray = intArrayOf(
            rgb(0, 0, 0), rgb(205, 49, 49), rgb(13, 188, 121), rgb(229, 229, 16),
            rgb(36, 114, 200), rgb(188, 63, 188), rgb(17, 168, 205), rgb(229, 229, 229),
            rgb(102, 102, 102), rgb(241, 76, 76), rgb(35, 209, 139), rgb(245, 245, 67),
            rgb(59, 142, 234), rgb(214, 112, 214), rgb(41, 184, 219), rgb(255, 255, 255),
        )

        fun color256(n: Int): Int? = when (n) {
            in 0..15 -> PALETTE_16[n]
            in 16..231 -> {
                val v = n - 16
                val steps = intArrayOf(0, 95, 135, 175, 215, 255)
                rgb(steps[v / 36], steps[(v / 6) % 6], steps[v % 6])
            }
            in 232..255 -> (8 + (n - 232) * 10).let { rgb(it, it, it) }
            else -> null
        }

        /** Removes every escape sequence, e.g. for plain-text export. */
        fun strip(input: String): String = AnsiParser().parse(input).joinToString("") { it.text }
    }
}

fun List<AnsiSpan>.toAnnotatedString(defaultColor: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    for (span in this@toAnnotatedString) {
        withStyle(SpanStyle(
            color = span.fg?.let { Color(it) } ?: defaultColor,
            background = span.bg?.let { Color(it) } ?: Color.Unspecified,
            fontWeight = if (span.bold) FontWeight.Bold else null,
        )) { append(span.text) }
    }
}
