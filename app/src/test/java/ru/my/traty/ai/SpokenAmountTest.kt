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
        // неразрывный пробел (U+00A0) между тысячами — так пишет распознавание речи
        assertEquals(1500.0, value("такси 1${0x00A0.toChar()}500"))
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

    @Test
    fun letterKIsThousandOnlyWhenGluedToNumber() {
        assertEquals(300.0, value("такси 300 к маме"))
        assertEquals(5000.0, value("продукты 5к"))
    }

    @Test
    fun kopecksAfterDashAsOnPriceTag() {
        // «восемьдесят три девяносто» распознавание речи пишет через тире: это 83,90, а не два числа
        assertEquals(83.9, value("Гречка фасованная — 83—90")!!, 1e-9)
        assertEquals(69.9, value("сок 69-90")!!, 1e-9)
        assertEquals(91.05, value("оливки 91–05")!!, 1e-9)
        assertEquals(1299.9, value("чайник 1 299—90")!!, 1e-9)
    }

    @Test
    fun dashWithoutTwoDigitsIsNotKopecks() {
        // Диапазоны и три цифры после тире — не копейки: берётся первое число, как раньше
        assertEquals(100.0, value("на чаевые 100-200"))
        assertEquals(2.0, value("2-3 пачки"))
        assertEquals(300.0, value("такси 300 — дорого"))
    }

    @Test
    fun slangThousandsInAnyForm() {
        assertEquals(2000.0, value("два косаря на подарок маме"))
        assertEquals(1200.0, value("тыщу двести за интернет"))
        assertEquals(1500.0, value("полтора косаря"))
        assertEquals(3000.0, value("три тыщи"))
    }
}
