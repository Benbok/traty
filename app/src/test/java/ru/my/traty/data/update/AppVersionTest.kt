package ru.my.traty.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parsesPlainAndPrefixedVersions() {
        assertEquals(listOf(0, 1), AppVersion.parse("0.1"))
        assertEquals(listOf(0, 1, 1), AppVersion.parse("v0.1.1"))
        assertEquals(listOf(1, 20, 3), AppVersion.parse(" 1.20.3 "))
    }

    @Test
    fun rejectsSuffixesAndGarbage() {
        assertNull(AppVersion.parse("0.2-test"))
        assertNull(AppVersion.parse("1.0-beta"))
        assertNull(AppVersion.parse("main"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse("1..2"))
    }

    @Test
    fun comparesNumbersNotText() {
        assertTrue(AppVersion.isNewer("0.1.1", "0.1"))
        assertTrue(AppVersion.isNewer("0.2", "0.1.9"))
        assertTrue(AppVersion.isNewer("0.10", "0.9"))
        assertTrue(AppVersion.isNewer("1.0", "0.99.99"))
        assertTrue(AppVersion.isNewer("v0.1.1", "0.1"))
    }

    @Test
    fun equalOrOlderIsNotNewer() {
        assertFalse(AppVersion.isNewer("0.1", "0.1"))
        assertFalse(AppVersion.isNewer("0.1.0", "0.1"))
        assertFalse(AppVersion.isNewer("0.1", "0.1.0"))
        assertFalse(AppVersion.isNewer("0.1", "0.1.1"))
        assertFalse(AppVersion.isNewer("0.9", "0.10"))
    }

    @Test
    fun unparsableVersionIsNeverNewer() {
        assertFalse(AppVersion.isNewer("0.2-test", "0.1"))
        assertFalse(AppVersion.isNewer("", "0.1"))
        assertFalse(AppVersion.isNewer(null, "0.1"))
        // Не знаем, какая версия установлена (например, пустая) — людей не тревожим
        assertFalse(AppVersion.isNewer("0.2", ""))
        assertFalse(AppVersion.isNewer("0.2", "0.1-debug"))
    }

    @Test
    fun cleanDropsLeadingV() {
        assertEquals("0.1.1", AppVersion.clean("v0.1.1"))
        assertEquals("0.1.1", AppVersion.clean(" 0.1.1 "))
    }
}
