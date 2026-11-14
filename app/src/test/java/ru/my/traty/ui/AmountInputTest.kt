package ru.my.traty.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    private val sp = AmountInput.GROUP_SEPARATOR
    private fun shown(s: String) = s.replace(' ', sp)

    // --- показ ---

    @Test
    fun formatsThousandsCommaAndSuffix() {
        assertEquals(shown("20 000,0 руб"), AmountInput.format("20000,0").text)
        assertEquals(shown("20 000 руб"), AmountInput.format("20000").text)
        assertEquals(shown("1 234 567,89 руб"), AmountInput.format("1234567,89").text)
        assertEquals(shown("999 руб"), AmountInput.format("999").text)
        assertEquals(shown("1 000 руб"), AmountInput.format("1000").text)
    }

    @Test
    fun pointIsShownAsComma() {
        assertEquals(shown("250,5 руб"), AmountInput.format("250.5").text)
    }

    @Test
    fun separatorWhileTypingStaysVisible() {
        assertEquals(shown("20 000, руб"), AmountInput.format("20000,").text)
        assertEquals(shown(",5 руб"), AmountInput.format(",5").text)
    }

    @Test
    fun emptyStaysEmpty() {
        val f = AmountInput.format("")
        assertEquals("", f.text)
        assertEquals(0, f.toDisplay[0])
        assertEquals(0, f.toRaw[0])
    }

    @Test
    fun cursorMappingSkipsGroupSpacesAndSuffix() {
        // «20000,5» → «20 000,5 руб»
        val f = AmountInput.format("20000,5")
        assertArrayEquals(intArrayOf(0, 1, 3, 4, 5, 6, 7, 8), f.toDisplay)
        // Позиции 0..8 в показанном тексте, затем суффикс « руб» (4 символа) и конец
        assertArrayEquals(intArrayOf(0, 1, 2, 2, 3, 4, 5, 6, 7, 7, 7, 7, 7), f.toRaw)
        assertEquals(8, f.numberLength)
    }

    @Test
    fun cursorMappingIsConsistentForAnyLength() {
        for (n in 1..9) {
            val raw = "1".repeat(n) + ",25"
            val f = AmountInput.format(raw)
            assertEquals(raw.length + 1, f.toDisplay.size)
            assertEquals(f.text.length + 1, f.toRaw.size)
            for (i in 0..raw.length) {
                // Туда и обратно — та же позиция: курсор не прыгает
                assertEquals("n=$n i=$i", i, f.toRaw[f.toDisplay[i]])
            }
            assertEquals(raw.length, f.toRaw[f.text.length])
        }
    }

    // --- чистка ввода ---

    @Test
    fun pointAndCommaBothBecomeComma() {
        assertEquals("250,5", AmountInput.sanitize("250.5").text)
        assertEquals("250,5", AmountInput.sanitize("250,5").text)
    }

    @Test
    fun onlyOneSeparatorAndTwoDecimals() {
        assertEquals("1,23", AmountInput.sanitize("1,2,3").text)
        assertEquals("1,23", AmountInput.sanitize("1.234").text)
    }

    @Test
    fun dropsSpacesSuffixAndLetters() {
        assertEquals("20000,5", AmountInput.sanitize(shown("20 000,5 руб")).text)
        assertEquals("1240", AmountInput.sanitize("1240 ₽").text)
    }

    @Test
    fun stripsLeadingZeros() {
        assertEquals("7", AmountInput.sanitize("007").text)
        assertEquals("0,5", AmountInput.sanitize("00,5").text)
        assertEquals("0", AmountInput.sanitize("0").text)
        assertEquals("", AmountInput.sanitize("").text)
    }

    @Test
    fun limitsIntegerDigits() {
        assertEquals("123456789", AmountInput.sanitize("1234567890").text)
    }

    @Test
    fun cursorStaysAfterSameCharWhenSomethingIsDropped() {
        // Буква вставлена в середину и отброшена: курсор остаётся после «12»
        assertEquals(AmountInput.Edit("1234", 2), AmountInput.sanitize("12a34", 3))
        // Точку заменили запятой — длина та же, курсор на месте
        assertEquals(AmountInput.Edit("12,5", 3), AmountInput.sanitize("12.5", 3))
        // Вставка в конец
        assertEquals(AmountInput.Edit("12345", 5), AmountInput.sanitize("12345", 5))
    }

    // --- разбор и обратно ---

    @Test
    fun parsesRawAndShownText() {
        assertEquals(2_000_050L, AmountInput.parseKopecks("20000,5"))
        assertEquals(2_000_050L, AmountInput.parseKopecks("20000.5"))
        assertEquals(2_000_050L, AmountInput.parseKopecks(shown("20 000,5 руб")))
        assertEquals(124_000L, AmountInput.parseKopecks("1240 ₽"))
        assertEquals(50L, AmountInput.parseKopecks(",5"))
        assertEquals(500L, AmountInput.parseKopecks("5,"))
        assertEquals(29L, AmountInput.parseKopecks("0,29")) // без потери копейки на Double
    }

    @Test
    fun emptyOrGarbageIsNull() {
        assertNull(AmountInput.parseKopecks(""))
        assertNull(AmountInput.parseKopecks(","))
        assertNull(AmountInput.parseKopecks("abc"))
        assertNull(AmountInput.parsePositiveKopecks("0"))
    }

    @Test
    fun kopecksToRawText() {
        assertEquals("20000", AmountInput.fromKopecks(2_000_000))
        assertEquals("20000,5", AmountInput.fromKopecks(2_000_050))
        assertEquals("0,05", AmountInput.fromKopecks(5))
        assertEquals("0", AmountInput.fromKopecks(0))
        assertEquals("1000000", AmountInput.fromKopecks(100_000_000))
    }

    @Test
    fun savedValueRoundTrips() {
        for (k in listOf(1L, 29L, 100L, 2_000_050L, 99_999_999_999L)) {
            val raw = AmountInput.fromKopecks(k)
            assertEquals(raw, AmountInput.sanitize(raw).text)
            assertEquals(k, AmountInput.parseKopecks(raw))
            assertEquals(k, AmountInput.parseKopecks(AmountInput.format(raw).text))
        }
    }
}
