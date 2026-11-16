package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenTotalQuantityTest {
    @Test
    fun smallNumberWithItemAfterTotalWordIsQuantityNotTotal() {
        // «всего 2 билета 3000»: два билета, итог 3000, а не 2 ₽
        assertNull(SpokenTotal.find("купил всего 2 билета 3000"))
        assertNull(SpokenTotal.find("взял всего три пачки 450"))
    }

    @Test
    fun realTotalsStillWork() {
        assertEquals(2500.0, SpokenTotal.find("купил рыбу, хлеб, всего 2500")!!.value, 0.0)
        assertEquals(2500.0, SpokenTotal.find("итого 2500 рублей")!!.value, 0.0)
        assertEquals(2.0, SpokenTotal.find("всего 2 рубля")!!.value, 0.0)
        assertEquals(15.0, SpokenTotal.find("всего 15 ₽")!!.value, 0.0)
        assertEquals(2500.0, SpokenTotal.find("в продуктовом на 2500 купил рыбу, консерву")!!.value, 0.0)
    }
}
