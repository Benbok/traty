package ru.my.traty.data.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemsCheckTest {
    @Test
    fun pricesEqualToTotalMatch() {
        // Эльдорадо: телефон 40 000, чехол 2 000, клавиатура 3 000 = 45 000
        val b = ItemsCheck.balance(4_500_000, listOf(4_000_000, 200_000, 300_000))
        assertEquals(ItemsBalanceState.MATCH, b.state)
        assertEquals(0L, b.differenceKopecks)
        assertFalse(b.needsAttention)
    }

    @Test
    fun restGoesToUnpricedItems() {
        // «на 2500 купил рыбу 500, консерву, хлеб»: 2000 приходится на консерву и хлеб
        val b = ItemsCheck.balance(250_000, listOf(50_000, null, null))
        assertEquals(ItemsBalanceState.REST, b.state)
        assertEquals(200_000L, b.differenceKopecks)
        assertEquals(2, b.unpriced)
        assertFalse(b.needsAttention)
    }

    @Test
    fun restWithoutUnpricedItemsNeedsAttention() {
        val b = ItemsCheck.balance(4_500_000, listOf(4_000_000, 200_000))
        assertEquals(ItemsBalanceState.REST, b.state)
        assertTrue(b.needsAttention)
    }

    @Test
    fun pricesAboveTotalNeedAttention() {
        val b = ItemsCheck.balance(4_000_000, listOf(4_000_000, 200_000))
        assertEquals(ItemsBalanceState.OVER, b.state)
        assertEquals(-200_000L, b.differenceKopecks)
        assertTrue(b.needsAttention)
    }

    @Test
    fun noPricesAtAll() {
        val b = ItemsCheck.balance(250_000, listOf(null, null))
        assertEquals(ItemsBalanceState.NO_PRICES, b.state)
        assertFalse(b.needsAttention)
    }

    @Test
    fun totalOnlyWhenEveryItemHasPrice() {
        assertEquals(4_500_000L, ItemsCheck.totalOf(listOf(4_000_000, 200_000, 300_000)))
        assertNull(ItemsCheck.totalOf(listOf(4_000_000, null)))
        assertNull(ItemsCheck.totalOf(emptyList()))
    }
}
