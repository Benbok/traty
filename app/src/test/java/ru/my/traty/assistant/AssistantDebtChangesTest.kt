package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtState
import ru.my.traty.data.debt.PaidPart
import ru.my.traty.data.debt.PaymentPart
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantDebtChangesTest {
    private val today = LocalDate.of(2026, 11, 15)

    private val tv = DebtWithPaid(
        Debt(id = 1, title = "Телевизор", place = "Ozon", categoryId = 1, totalKopecks = 5_000_000),
        listOf(PaidPart(today.minusDays(5).toEpochDay(), PaymentPart.PRINCIPAL, 2_500_000)),
    )
    private val fridge = DebtWithPaid(
        Debt(
            id = 2, title = "Холодильник", place = "Ozon", categoryId = 1, kind = DebtKind.INSTALLMENT.key,
            totalKopecks = 8_500_000, paymentsCount = 4, firstDueEpochDay = today.toEpochDay(),
        ),
        emptyList(),
    )
    private val closed = DebtWithPaid(Debt(id = 3, title = "Диван", categoryId = 1, state = DebtState.CLOSED), emptyList())

    private fun ctx() = AssistantContext(
        expenses = emptyList(), incomes = emptyList(), recurring = emptyList(),
        today = today, startDay = 1, zone = ZoneOffset.UTC, debts = listOf(tv, fridge, closed),
    )

    private fun run(q: AssistantQuery) = AssistantEngine.run(q, ctx())

    private val debtsAnswer = AssistantQuery(intent = Intents.DEBTS)

    private fun correct(question: String, ai: AssistantQuery = AssistantQuery(intent = Intents.CANCEL_SUB), previous: AssistantQuery? = debtsAnswer) =
        AssistantQueries.correct(question, ai, previous)

    @Test
    fun screenshotCaseDeletesBothAfterDebtsAnswer() {
        // Так было на телефоне: ИИ решил, что это отмена подписки, и ничего не нашёл
        val q = correct("удали эти платежи, они ошибочные", AssistantQuery(intent = Intents.CANCEL_SUB, place = "эти"))
        assertEquals(Intents.DELETE_DEBT, q.intent)
        assertNull(q.place)
        assertTrue(q.all)
        val d = run(q)
        assertEquals(listOf(1L, 2L), d.debtChanges.map { it.id })
        assertTrue(d.debtChanges.all { it.preselected && it.action == DebtAction.DELETE })
        assertTrue(d.facts, "Пока ничего не изменено" in d.facts)
    }

    @Test
    fun namedPurchaseIsFoundByWordStem() {
        val d = run(AssistantQuery(intent = Intents.DELETE_DEBT, place = "холодильника"))
        assertEquals(listOf(2L), d.debtChanges.map { it.id })
        assertEquals("Холодильник · Ozon", d.debtChanges.single().title)
    }

    @Test
    fun unnamedWithSeveralOpenIsOfferedUnchecked() {
        val d = run(AssistantQuery(intent = Intents.CLOSE_DEBT))
        assertEquals(2, d.debtChanges.size)
        assertFalse(d.debtChanges.any { it.preselected })
        assertTrue(d.facts, "не уточнил" in d.facts)
    }

    @Test
    fun unknownNameListsOpenPurchases() {
        val d = run(AssistantQuery(intent = Intents.CANCEL_DEBT, place = "велосипед"))
        assertTrue(d.debtChanges.isEmpty())
        assertTrue(d.facts, "Покупки «велосипед» среди открытых нет" in d.facts)
        assertTrue(d.facts, "«Телевизор»" in d.facts)
    }

    @Test
    fun closeWarnsWhenSomethingIsStillOwed() {
        val d = run(AssistantQuery(intent = Intents.CLOSE_DEBT, place = "телевизор"))
        assertTrue(d.debtChanges.single().summary, "осталось" in d.debtChanges.single().summary)
    }

    @Test
    fun commandsAreRecognisedByRules() {
        assertEquals(Intents.CLOSE_DEBT, correct("я выплатил рассрочку за телефон", AssistantQuery(intent = Intents.DEBTS), null).intent)
        assertEquals(Intents.CANCEL_DEBT, correct("вернул телевизор, отмени покупку", AssistantQuery(intent = Intents.TOTAL)).intent)
        assertEquals(Intents.DELETE_DEBT, correct("удали рассрочку за холодильник", AssistantQuery(intent = Intents.TOTAL, place = "холодильник"), null).intent)
        assertEquals("холодильник", correct("удали рассрочку за холодильник", AssistantQuery(intent = Intents.TOTAL, place = "холодильник"), null).place)
    }

    @Test
    fun questionsAndSubscriptionsAreNotDebtCommands() {
        // Вопрос, а не команда
        assertEquals(Intents.DEBTS, correct("когда я закрою кредит?", AssistantQuery(intent = Intents.DEBTS), null).intent)
        // Подписка названа прямо — это подписка, даже после ответа про рассрочки
        assertEquals(Intents.CANCEL_SUB, correct("отмени подписку Netflix", AssistantQuery(intent = Intents.CANCEL_SUB, place = "Netflix")).intent)
        // «Удали платёж» без разговора о рассрочках остаётся подпиской
        assertEquals(Intents.CANCEL_SUB, correct("удали платёж за интернет", AssistantQuery(intent = Intents.CANCEL_SUB), null).intent)
        // «удалось» — не «удали»
        assertEquals(Intents.TOTAL, correct("сколько удалось отложить", AssistantQuery(intent = Intents.TOTAL)).intent)
    }

    @Test
    fun followUpKeepsPurchaseFromPreviousQuestion() {
        val previous = AssistantQuery(intent = Intents.DEBTS, place = "телевизор")
        val q = correct("удали его", AssistantQuery(intent = Intents.UNKNOWN), previous)
        assertEquals(Intents.DELETE_DEBT, q.intent)
        assertEquals("телевизор", q.place)
    }
}
