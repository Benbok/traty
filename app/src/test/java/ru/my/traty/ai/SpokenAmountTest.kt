package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenAmountTest {
    private fun value(text: String) = SpokenAmountParser.find(text)?.value

    @Test
    fun digits() {
        assertEquals(731.0, value("зоомагазин вчера 731 лекарство для кошки"))
        assertEquals(1500.0, value("такси 1 500"))
        assertEquals(99.5, value("кофе 99,5"))
    }

    @Test
    fun digitsWithThousand() {
        assertEquals(2500.0, value("ремонт 2,5 тысячи"))
        assertEquals(5000.0, value("продукты 5к"))
    }

    @Test
    fun words() {
        assertEquals(731.0, value("зоомагазин вчера семьсот тридцать один лекарство для кошки"))
        assertEquals(250.0, value("кофе двести пятьдесят"))
        assertEquals(1500.0, value("полторы тысячи на бензин"))
        assertEquals(12000.0, value("двенадцать тысяч за курс"))
        assertEquals(3200.0, value("три тысячи двести"))
        assertEquals(1000.0, value("косарь на такси"))
    }

    @Test
    fun colloquialThousands() {
        assertEquals(2100.0, value("две сто продукты"))
        assertEquals(25300.0, value("двадцать пять триста"))
        assertEquals(150.0, value("сто пятьдесят"))
    }

    @Test
    fun biggestWordNumberWins() {
        assertEquals(500.0, value("две пачки корма пятьсот"))
    }

    @Test
    fun rangeCoversWholeNumber() {
        val text = "аптека семьсот тридцать один бинт"
        val a = SpokenAmountParser.find(text)!!
        assertEquals("семьсот тридцать один", text.substring(a.range))
    }

    @Test
    fun noNumber() {
        assertNull(value("лекарство для кошки"))
    }
}
