package ru.my.traty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingSlicesTest {
    private fun t(id: Long, total: Long) = CategoryTotal(id, "c$id", "", total)

    @Test
    fun smallCategoriesGoToRest() {
        val s = RingSlices.of((1L..8L).map { t(it, it * 10) }, maxSlices = 3)
        assertEquals(listOf(8L, 7L, 6L, null), s.map { it.categoryId })
        assertEquals(RingSlices.REST, s.last().name)
        assertEquals(150L, s.last().total) // 10+20+30+40+50
        assertEquals(1f, s.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun singleLeftoverIsShownByItself() {
        val s = RingSlices.of((1L..4L).map { t(it, it) }, maxSlices = 3)
        assertEquals(4, s.size)
        assertTrue(s.all { it.categoryId != null })
    }

    @Test
    fun emptyAndZeroGiveNothing() {
        assertEquals(emptyList<RingSlice>(), RingSlices.of(emptyList()))
        assertEquals(emptyList<RingSlice>(), RingSlices.of(listOf(t(1, 0))))
        assertNull(RingSlices.of(listOf(t(1, 5), t(2, 0))).getOrNull(1))
    }
}
