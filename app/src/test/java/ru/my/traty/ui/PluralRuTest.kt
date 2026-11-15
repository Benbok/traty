package ru.my.traty.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralRuTest {
    private fun traty(n: Long) = pluralRu(n, "трата", "траты", "трат")

    @Test
    fun one() {
        assertEquals("трата", traty(1))
        assertEquals("трата", traty(21))
        assertEquals("трата", traty(101))
    }

    @Test
    fun few() {
        assertEquals("траты", traty(2))
        assertEquals("траты", traty(4))
        assertEquals("траты", traty(33))
    }

    @Test
    fun many() {
        assertEquals("трат", traty(0))
        assertEquals("трат", traty(5))
        assertEquals("трат", traty(11))
        assertEquals("трат", traty(12))
        assertEquals("трат", traty(14))
        assertEquals("трат", traty(111))
        assertEquals("трат", traty(100))
    }

    @Test
    fun negativeUsesAbsoluteValue() {
        assertEquals("трата", traty(-1))
        assertEquals("траты", traty(-3))
    }
}
