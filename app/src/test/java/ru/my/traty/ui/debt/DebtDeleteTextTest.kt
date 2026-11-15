package ru.my.traty.ui.debt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.debt.DeletionImpact
import ru.my.traty.ui.formatRub

class DebtDeleteTextTest {
    @Test
    fun saysWhatDisappearsAndWhatStays() {
        val t = DebtDeleteText.of(DeletionImpact(removedCount = 2, removedKopecks = 1_500_000, keptCount = 1))
        assertTrue(t.contains("удалятся 2 платежа"))
        assertTrue(t.contains(formatRub(1_500_000)))
        assertTrue(t.contains("В ленте останется 1 трата, записанных вами"))
    }

    @Test
    fun nothingRemovedMeansNoPromiseOfDeletion() {
        val t = DebtDeleteText.of(DeletionImpact(removedCount = 0, removedKopecks = 0, keptCount = 3))
        assertFalse(t.contains("удалятся"))
        assertTrue(t.contains("В ленте останется 3 траты, записанных вами"))
    }

    @Test
    fun emptyPurchase() {
        val t = DebtDeleteText.of(DeletionImpact(0, 0, 0))
        assertTrue(t.contains("платежей у неё нет"))
        assertTrue(t.contains("у этой покупки нет"))
    }
}
