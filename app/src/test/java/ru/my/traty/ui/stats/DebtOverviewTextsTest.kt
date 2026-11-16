package ru.my.traty.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Recurring
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtOverviews
import ru.my.traty.data.debt.DebtPaymentRow
import java.time.LocalDate
import java.time.ZoneId

class DebtOverviewTextsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 16)

    private fun installment(firstDue: LocalDate, id: Long = 1) = Debt(
        id = id, title = "Телевизор", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
        totalKopecks = 6_000_000, paymentsCount = 6, firstDueEpochDay = firstDue.toEpochDay(),
    )

    private fun line(debt: Debt, payments: List<DebtPaymentRow> = emptyList(), recurring: List<Recurring> = emptyList()) =
        DebtOverviews.of(listOf(debt), payments, recurring, null, today, zone).lines.single()

    private fun nbsp(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun nextPaymentWording() {
        assertEquals("Сегодня платёж 10 000 ₽", nbsp(DebtOverviewTexts.next(line(installment(today)), today)))
        assertEquals("Завтра платёж 10 000 ₽", nbsp(DebtOverviewTexts.next(line(installment(today.plusDays(1))), today)))
        assertEquals("Следующий платёж 1 дек: 10 000 ₽", nbsp(DebtOverviewTexts.next(line(installment(LocalDate.of(2026, 12, 1))), today)))
        assertEquals("Просрочен платёж от 10 ноя: 10 000 ₽", nbsp(DebtOverviewTexts.next(line(installment(LocalDate.of(2026, 11, 10))), today)))
    }

    @Test
    fun severalOverduePayments() {
        val text = nbsp(DebtOverviewTexts.next(line(installment(LocalDate.of(2026, 9, 1))), today))
        assertEquals("Просрочено 3 платежа на 30 000 ₽", text)
    }

    @Test
    fun partialWithoutSchedule() {
        val fridge = Debt(id = 2, title = "Холодильник", categoryId = 1, totalKopecks = 5_000_000)
        val l = line(fridge)
        assertEquals("Без графика: доплачивайте, когда удобно", DebtOverviewTexts.next(l, today))
        assertEquals("Осталось 50 000 ₽ из 50 000 ₽", nbsp(DebtOverviewTexts.left(l)))
        assertEquals("Частями", DebtOverviewTexts.kind(fridge))
    }

    @Test
    fun loanLeftMentionsInterestAndOverpayment() {
        val car = Debt(
            id = 3, title = "Машина", categoryId = 1, kind = DebtKind.LOAN.key, totalKopecks = 12_000_000,
            rateBasisPoints = 2000, paymentsCount = 12, firstDueEpochDay = LocalDate.of(2026, 12, 1).toEpochDay(),
        )
        val o = DebtOverviews.of(listOf(car), emptyList(), emptyList(), null, today, zone)
        assertTrue(DebtOverviewTexts.left(o.lines.single()).contains("с процентами"))
        assertTrue(DebtOverviewTexts.overpayment(o).orEmpty().startsWith("Переплата банку по кредитам:"))
    }

    @Test
    fun noOverpaymentWithoutLoans() {
        val o = DebtOverviews.of(listOf(installment(today)), emptyList(), emptyList(), null, today, zone)
        assertNull(DebtOverviewTexts.overpayment(o))
        assertEquals("1 покупка · считается на сегодня", DebtOverviewTexts.subtitle(o))
    }

    @Test
    fun unknownPriceNote() {
        val noPrice = Debt(id = 4, title = "Диван", categoryId = 1)
        val o = DebtOverviews.of(listOf(noPrice), emptyList(), emptyList(), null, today, zone)
        assertEquals("Уже внесено 0 ₽. У 1 покупки не указана цена, её остаток сюда не вошёл", nbsp(DebtOverviewTexts.paidNote(o)))
        assertTrue(DebtOverviewTexts.left(o.lines.single()).startsWith("Цена не указана"))
    }

    @Test
    fun duplicateAndSubscriptionOnlyNotices() {
        val sub = Recurring(id = 9, title = "Рассрочка ТВ телевизор", amountKopecks = 1_000_000, categoryId = 1, periodUnit = "month", nextEpochDay = 0)
        val l = line(installment(today), recurring = listOf(sub))
        assertTrue(DebtOverviewTexts.duplicate(l).orEmpty().contains("считается дважды"))
        val ipoteka = sub.copy(id = 10, title = "Ипотека")
        assertEquals(
            "Платёж «Ипотека» в «Долгах и кредитах» заведён только подпиской, поэтому его остаток здесь не виден.",
            DebtOverviewTexts.subscriptionOnly(listOf(ipoteka)),
        )
        assertTrue(DebtOverviewTexts.subscriptionOnly(listOf(ipoteka, ipoteka.copy(title = "Автокредит"))).orEmpty().startsWith("Ещё 2 платежа"))
        assertNull(DebtOverviewTexts.subscriptionOnly(emptyList()))
    }

    @Test
    fun emptyTexts() {
        assertTrue(DebtOverviewTexts.empty(0).contains("рассрочку на 6 месяцев"))
        assertTrue(DebtOverviewTexts.empty(2).contains("всё выплачено"))
    }
}
