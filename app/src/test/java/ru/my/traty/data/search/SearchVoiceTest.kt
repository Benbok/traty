package ru.my.traty.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchVoiceTest {
    @Test
    fun trailingPeriodAndQuotesAreDropped() {
        assertEquals("Шоколад, масло", SearchVoice.clean("Шоколад, масло."))
        assertEquals("шоколад", SearchVoice.clean("«шоколад»"))
        assertEquals("шоколад масло", SearchVoice.clean("  шоколад   масло!  "))
    }

    @Test
    fun leadingCommandIsDropped() {
        assertEquals("шоколад", SearchVoice.clean("Найди шоколад"))
        assertEquals("шоколад и масло", SearchVoice.clean("Найди, шоколад и масло."))
        assertEquals("такси", SearchVoice.clean("покажи такси"))
    }

    @Test
    fun commandAloneOrNothingGivesEmptyQuery() {
        assertEquals("", SearchVoice.clean("Найди"))
        assertEquals("", SearchVoice.clean("   "))
        assertEquals("", SearchVoice.clean("..."))
    }

    @Test
    fun wordsInsideStayAsSpoken() {
        // «найди» в середине фразы — часть названия, не команда
        assertEquals("кофе найди", SearchVoice.clean("кофе найди"))
        // Регистр остаётся как сказано: поиск от него не зависит
        assertEquals("Кофе 250", SearchVoice.clean("Кофе 250."))
    }
}
