package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.Recurring
import java.time.LocalDate

class DebtDuplicatesTest {
    private val phone = Debt(
        id = 1, title = "Телефон", place = "Ozon", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
        totalKopecks = 6_000_000, paymentsCount = 6, firstDueEpochDay = LocalDate.of(2026, 12, 1).toEpochDay(),
    )

    private fun sub(title: String, kopecks: Long, categoryId: Long = 50, place: String? = null) =
        Recurring(id = 7, title = title, amountKopecks = kopecks, categoryId = categoryId, place = place, periodUnit = "month", nextEpochDay = 0)

    @Test
    fun sameWordInTitleAndSameAmount() {
        assertEquals(7L, DebtDuplicates.find(phone, listOf(sub("Рассрочка за телефона", 1_000_000)))?.id)
    }

    @Test
    fun bankRoundingIsTolerated() {
        assertEquals(7L, DebtDuplicates.find(phone, listOf(sub("Телефон", 1_001_500)))?.id)
    }

    @Test
    fun differentAmountIsNotDuplicate() {
        assertNull(DebtDuplicates.find(phone, listOf(sub("Телефон", 1_500_000))))
    }

    @Test
    fun sameAmountWithoutAnythingElseIsNotDuplicate() {
        assertNull(DebtDuplicates.find(phone, listOf(sub("Спортзал", 1_000_000))))
    }

    @Test
    fun samePlaceOrLoanCategoryIsEnough() {
        assertEquals(7L, DebtDuplicates.find(phone, listOf(sub("Платёж", 1_000_000, place = "ozon")))?.id)
        assertEquals(7L, DebtDuplicates.find(phone, listOf(sub("Банк", 1_000_000, categoryId = 9)), loanCategoryId = 9)?.id)
    }

    @Test
    fun contractPaymentWins() {
        val withContract = phone.copy(paymentKopecks = 1_050_000)
        assertEquals(1_050_000L, DebtDuplicates.regularPayment(withContract))
        assertEquals(7L, DebtDuplicates.find(withContract, listOf(sub("Телефон", 1_050_000)))?.id)
    }

    @Test
    fun partialPaymentHasNoDuplicate() {
        val partial = Debt(id = 2, title = "Холодильник", categoryId = 1, totalKopecks = 5_000_000)
        assertNull(DebtDuplicates.find(partial, listOf(sub("Холодильник", 1_000_000))))
    }
}
