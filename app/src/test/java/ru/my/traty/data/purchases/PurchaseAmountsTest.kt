package ru.my.traty.data.purchases

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PurchaseAmountsTest {
    private val today = LocalDate.of(2026, 12, 3)
    private val due = LocalDate.of(2027, 1, 1)

    @Test
    fun calculatesInitialAmountIncludingDueDay() {
        assertEquals(40_000L, dailyPurchaseAmount(1_200_000L, today, due))
    }

    @Test
    fun largerContributionLowersTomorrowsAmount() {
        assertEquals(37_932L, dailyPurchaseAmount(1_100_000L, today.plusDays(1), due))
    }

    @Test
    fun missedContributionRaisesTomorrowsAmount() {
        assertEquals(41_380L, dailyPurchaseAmount(1_200_000L, today.plusDays(1), due))
    }

    @Test
    fun overdueGoalSuggestsEntireRemainingAmount() {
        assertEquals(23_400L, dailyPurchaseAmount(23_400L, due.plusDays(1), due))
    }

    @Test
    fun roundsUpWithoutOverflow() {
        assertEquals(2L, dailyPurchaseAmount(3L, due.minusDays(1), due))
        assertEquals(4_611_686_018_427_387_904L, dailyPurchaseAmount(Long.MAX_VALUE, due.minusDays(1), due))
    }
}
