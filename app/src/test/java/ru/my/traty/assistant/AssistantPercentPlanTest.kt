package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.budget.Budget
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantPercentPlanTest {
    private val categories = listOf(CategoryWithSubs(Category(id = 2, name = "Кафе и рестораны", emoji = "☕"), emptyList()))

    private fun ctx(planRub: Long) = AssistantContext(
        emptyList(), emptyList(), emptyList(), LocalDate.of(2026, 11, 14), 1, ZoneOffset.UTC,
        listOf(Budget(id = 1, categoryId = 2, month = 202611, amountKopecks = planRub * 100)), categories, null,
    )

    private fun percent(amount: Double, planRub: Long) = AssistantEngine.run(
        AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Кафе и рестораны"), amount = amount, relative = true, percent = true),
        ctx(planRub),
    )

    @Test
    fun percentIsShareOfCurrentPlanNotRubles() {
        assertEquals(330_000L, percent(10.0, 3000).planProposals.single().amountKopecks)
        assertEquals(240_000L, percent(-20.0, 3000).planProposals.single().amountKopecks)
        // до целых рублей: 3333 ₽ + 10% = 3666,3 ₽ → 3666 ₽
        assertEquals(366_600L, percent(10.0, 3333).planProposals.single().amountKopecks)
    }

    @Test
    fun percentFactsNameThePercent() {
        val d = percent(10.0, 3000)
        assertTrue(d.facts, "+10% от действующего плана" in d.facts)
        assertTrue("ещё не изменён" in d.facts)
    }

    @Test
    fun hundredPercentCutIsRefusedAsZeroPlan() {
        val d = percent(-100.0, 3000)
        assertTrue(d.planProposals.isEmpty())
        assertTrue(d.facts, "убери лимит" in d.facts)
    }

    @Test
    fun spokenPercentsBecomePercentQuery() {
        val ai = AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Кафе и рестораны"), amount = 10.0)
        val up = AssistantQueries.correct("подними лимит на кафе на 10 процентов", ai)
        assertEquals(Intents.SET_PLAN, up.intent)
        assertTrue(up.relative && up.percent)
        assertEquals(10.0, up.amount!!, 0.0)

        val down = AssistantQueries.correct("уменьши лимит на кафе на 20%", ai)
        assertTrue(down.relative && down.percent)
        assertEquals(-20.0, down.amount!!, 0.0)
        assertEquals(-12.5, AssistantQueries.correct("уменьши план на кафе на 12,5 процента", ai).amount!!, 0.0)
    }

    @Test
    fun rublesStayRubles() {
        val ai = AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Кафе и рестораны"))
        val rel = AssistantQueries.correct("увеличь лимит на кафе на 1000", ai)
        assertTrue(rel.relative)
        assertFalse(rel.percent)
        assertEquals(1000.0, rel.amount!!, 0.0)
        val abs = AssistantQueries.correct("поставь лимит на кафе 5000", ai)
        assertFalse(abs.relative || abs.percent)
        assertEquals(5000.0, abs.amount!!, 0.0)
    }

    @Test
    fun idsAndPercentNeverReachTheModel() {
        val q = AssistantQuery(intent = Intents.DELETE_EXPENSE, ids = listOf(1, 2, 3))
        assertFalse("ids" in AssistantQueries.encode(q))
        assertEquals(emptyList<Long>(), AssistantQueries.decode("""{"intent":"delete_expense","ids":[5]}""")!!.ids)
    }
}
