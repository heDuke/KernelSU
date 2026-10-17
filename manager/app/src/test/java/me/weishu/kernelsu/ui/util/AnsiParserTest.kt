package me.weishu.kernelsu.ui.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class AnsiParserTest {
    private val p = AnsiParser.PALETTE_16
    private fun parse(s: String) = AnsiParser().parse(s)

    @Test fun plainText() = assertEquals(listOf(AnsiSpan("hello")), parse("hello"))

    @Test fun basicAndBrightColorsAndReset() {
        assertEquals(listOf(AnsiSpan("a", fg = p[1]), AnsiSpan("b", fg = p[10], bg = p[4]), AnsiSpan("c")),
            parse("\u001B[31ma\u001B[92;44mb\u001B[0mc"))
        assertEquals(listOf(AnsiSpan("x", bg = p[9])), parse("\u001B[101mx"))
        assertEquals(listOf(AnsiSpan("y")), parse("\u001B[31m\u001B[39my"))
        assertEquals(listOf(AnsiSpan("z")), parse("\u001B[1;31m\u001B[mz"))
    }

    @Test fun boldAndNormal() {
        assertEquals(listOf(AnsiSpan("b", bold = true), AnsiSpan("n")), parse("\u001B[1mb\u001B[22mn"))
    }

    @Test fun colors256AndTrueColor() {
        assertEquals(AnsiParser.color256(196), parse("\u001B[38;5;196mr").single().fg)
        assertEquals(0xFFFF0000.toInt(), AnsiParser.color256(196))
        assertEquals(0xFF080808.toInt(), AnsiParser.color256(232))
        assertEquals(p[3], AnsiParser.color256(3))
        assertEquals(0xFF0A141E.toInt(), parse("\u001B[48;2;10;20;30mt").single().bg)
        assertNull(AnsiParser.color256(300))
    }

    @Test fun stripsNonSgrSequences() {
        assertEquals("ab", AnsiParser.strip("\u001B[2J\u001B[Ha\u001B[?25lb"))
        assertEquals("title gone", AnsiParser.strip("\u001B]0;my title\u0007title gone"))
        assertEquals("st", AnsiParser.strip("\u001B]2;x\u001B\\st"))
        assertEquals("cs", AnsiParser.strip("\u001B(Bcs"))
        assertEquals("cr", AnsiParser.strip("c\rr"))
        assertEquals("cut", AnsiParser.strip("cut\u001B[31"))
    }

    @Test fun stateCarriesAcrossLines() {
        val parser = AnsiParser()
        parser.parse("\u001B[32mstart")
        assertEquals(p[2], parser.parse("next").single().fg)
        parser.reset()
        assertNull(parser.parse("after").single().fg)
    }

    @Test fun annotatedStringUsesDefaultColorAndBold() {
        val s = parse("plain\u001B[1;31mred").toAnnotatedString(Color.Green)
        assertEquals("plainred", s.text)
        assertEquals(Color.Green, s.spanStyles[0].item.color)
        assertEquals(Color(p[1]), s.spanStyles[1].item.color)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, s.spanStyles[1].item.fontWeight)
    }
}
