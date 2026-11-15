package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtState
import ru.my.traty.data.debt.PaidPart
import ru.my.traty.data.debt.PaymentPart
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantDebtsTest {
    private val today = LocalDate.of(2026, 11, 15)

    private val tv = DebtWithPaid(
        Debt(id = 1, title = "Телевизор", place = "Ozon", categoryId = 1, totalKopecks = 5_000_000),
        listOf(PaidPart(today.minusDays(5).toEpochDay(), PaymentPart.PRINCIPAL, 2_500_000)),
    )
    private val phone = DebtWithPaid(
        Debt(
            id = 2, title = "Телефон", place = "М.Видео", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
            totalKopecks = 6_000_000, paymentsCount = 6, firstDueEpochDay = today.minusMonths(1).toEpochDay(),
        ),
        listOf(PaidPart(today.minusMonths(1).toEpochDay(), PaymentPart.PRINCIPAL, 1_000_000)),
    )
    private val closed = DebtWithPaid(
        Debt(id = 3, title = "Диван", categoryId = 1, totalKopecks = 100_000, state = DebtState.CLOSED),
        listOf(PaidPart(today.toEpochDay(), PaymentPart.PRINCIPAL, 100_000)),
    )

    private fun ctx(vararg debts: DebtWithPaid) = AssistantContext(
        expenses = emptyList(), incomes = emptyList(), recurring = emptyList(),
        today = today, startDay = 1, zone = ZoneOffset.UTC, debts = debts.toList(),
    )

    @Test
    fun totalOwedOverOpenPurchases() {
        val data = AssistantDebts.answer(AssistantQuery(intent = Intents.DEBTS), ctx(tv, phone, closed))
        // 25 000 по телевизору + 50 000 по телефону; закрытый диван не считается
        assertEquals(formatRub(7_500_000), data.headline)
        assertTrue(data.facts.contains("«Телевизор», Ozon (оплата частями)"))
        assertTrue(data.facts.contains("«Телефон», М.Видео (рассрочка)"))
        assertTrue(data.facts.contains("следующий платёж 2 из 6"))
        assertFalse(data.facts.contains("Диван"))
    }

    @Test
    fun onePurchaseByTitleOrShop() {
        val byTitle = AssistantDebts.answer(AssistantQuery(intent = Intents.DEBTS, place = "телевизор"), ctx(tv, phone))
        assertEquals(formatRub(2_500_000), byTitle.headline)
        val byPlace = AssistantDebts.answer(AssistantQuery(intent = Intents.DEBTS, place = "м.видео"), ctx(tv, phone))
        assertEquals(formatRub(5_000_000), byPlace.headline)
    }

    @Test
    fun nothingOpen() {
        val data = AssistantDebts.answer(AssistantQuery(intent = Intents.DEBTS), ctx(closed))
        assertTrue(data.facts.contains("нет"))
        assertEquals(formatRub(0), data.headline)
    }

    @Test
    fun questionsAboutDebtsGoToDebts() {
        val q = AssistantQuery(intent = Intents.TOTAL)
        assertEquals(Intents.DEBTS, AssistantQueries.correct("Сколько я ещё должен?", q).intent)
        assertEquals(Intents.DEBTS, AssistantQueries.correct("когда следующий платёж по рассрочке", AssistantQuery(intent = Intents.SUBSCRIPTIONS)).intent)
        // Траты на кредит — это всё ещё вопрос о тратах
        assertEquals(Intents.TOTAL, AssistantQueries.correct("сколько я потратил на кредит в ноябре", q).intent)
        assertEquals(Intents.TOTAL, AssistantQueries.correct("сколько ушло на долги", q.copy(categories = listOf("Долги и кредиты"))).intent)
    }
}
