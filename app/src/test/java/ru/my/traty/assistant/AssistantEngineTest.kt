package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.Subcategory
import ru.my.traty.data.budget.Budget
import ru.my.traty.ui.formatRub
import java.time.LocalDate
import java.time.ZoneOffset

class AssistantEngineTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14)
    private var nextId = 1L

    private fun exp(
        date: LocalDate,
        rub: Long,
        cat: Long = 1,
        name: String = "Продукты",
        sub: String? = null,
        place: String? = null,
        source: String = ExpenseSource.VOICE,
    ) = ExpenseWithCategory(
        Expense(
            id = nextId++, amountKopecks = rub * 100, categoryId = cat, place = place, source = source,
            timestamp = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ),
        categoryName = name, categoryEmoji = "🛒", subcategoryName = sub,
    )

    private fun nov(d: Int) = LocalDate.of(2026, 11, d)
    private fun oct(d: Int) = LocalDate.of(2026, 10, d)

    private val rows = listOf(
        exp(nov(2), 1000, place = "Пятёрочка"),
        exp(nov(5), 2500, place = "Магнит"),
        exp(nov(9), 600, place = "пятёрочка"),
        exp(nov(10), 450, cat = 2, name = "Кафе и рестораны", sub = "Кофе"),
        exp(nov(12), 1200, cat = 3, name = "Транспорт", sub = "Такси", place = "Яндекс Такси"),
        exp(oct(3), 3000),
        exp(oct(8), 900, cat = 2, name = "Кафе и рестораны", sub = "Обед"),
        exp(nov(13), 99_999, source = ExpenseSource.RECONCILE),
    )

    private val categories = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Кафе и рестораны", emoji = "☕"), listOf(Subcategory(id = 20, categoryId = 2, name = "Кофе"))),
        CategoryWithSubs(Category(id = 3, name = "Транспорт", emoji = "🚕"), listOf(Subcategory(id = 30, categoryId = 3, name = "Такси"))),
        CategoryWithSubs(Category(id = 9, name = "Спорт", emoji = "🏋️"), emptyList()),
    )

    private fun ctx(
        expenses: List<ExpenseWithCategory> = rows,
        incomes: List<Income> = emptyList(),
        recurring: List<Recurring> = emptyList(),
        budgets: List<Budget> = emptyList(),
        balance: Balance? = null,
    ) = AssistantContext(expenses, incomes, recurring, today, 1, zone, budgets, categories, balance)

    private fun run(q: AssistantQuery, c: AssistantContext = ctx()) = AssistantEngine.run(q, c)

    @Test
    fun totalDefaultsToCurrentPeriodAndSkipsReconcile() {
        val d = run(AssistantQuery(intent = Intents.TOTAL))
        assertTrue(d.facts, "покупок: 5" in d.facts)
        assertEquals(5, d.expenseIds.size)
        assertFalse(d.facts.contains("99"))
    }

    @Test
    fun categoryAndSubcategoryNamesFilter() {
        val cafe = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("кафе и рестораны")))
        assertEquals(1, cafe.expenseIds.size)
        val taxi = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("Такси")))
        assertEquals(1, taxi.expenseIds.size)
        assertTrue("Такси" in taxi.caption!!)
    }

    @Test
    fun unknownCategoryIsReportedNotInvented() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, categories = listOf("Яхты")))
        assertTrue("Яхты" in d.facts && "нет" in d.facts)
        assertNull(d.headline)
        assertTrue(d.expenseIds.isEmpty())
    }

    @Test
    fun placeMatchesIgnoringCase() {
        val d = run(AssistantQuery(intent = Intents.TOTAL, place = "Пятёрочка"))
        assertEquals(2, d.expenseIds.size)
    }

    @Test
    fun explicitDatesAreUsedAndFutureIsClipped() {
        val (from, to) = AssistantEngine.range("2026-10-01", "2026-12-31", nov(1), today)
        assertEquals(oct(1), from)
        assertEquals(today, to)
        val d = run(AssistantQuery(intent = Intents.TOTAL, from = "2026-10-01", to = "2026-10-31"))
        assertEquals(2, d.expenseIds.size)
    }

    @Test
    fun swappedDatesAreFixed() {
        val (from, to) = AssistantEngine.range("2026-11-10", "2026-11-01", nov(1), today)
        assertEquals(nov(1), from)
        assertEquals(nov(10), to)
    }

    @Test
    fun topIsSortedAndLimited() {
        val d = run(AssistantQuery(intent = Intents.TOP, limit = 2))
        assertEquals(2, d.expenseIds.size)
        assertTrue(d.headline!!.startsWith("2"))
    }

    @Test
    fun placesGroupsSpellings() {
        val d = run(AssistantQuery(intent = Intents.PLACES, categories = listOf("Продукты")))
        // «Магнит» 2500 больше, чем «Пятёрочка» 1600 (в двух написаниях)
        assertTrue(d.caption!!.startsWith("Магнит"))
        assertTrue("покупок: 2" in d.facts)
    }

    @Test
    fun compareCurrentPeriodUsesSameDaysOfPrevious() {
        val d = run(AssistantQuery(intent = Intents.COMPARE))
        // 1–14 ноября: 5750 ₽, 1–14 октября: 3900 ₽
        assertTrue(d.facts, "1–14 октября" in d.facts)
        assertTrue(d.headline!!.startsWith("+"))
    }

    @Test
    fun averagePerDay() {
        val d = run(AssistantQuery(intent = Intents.AVERAGE, from = "2026-11-01", to = "2026-11-10"))
        // 1000 + 2500 + 600 + 450 = 4550 за 10 дней
        assertTrue(d.facts, "10 дней" in d.facts)
        assertTrue(d.headline!!.startsWith("455"))
    }

    @Test
    fun subscriptionsListedWithTotals() {
        val r = Recurring(title = "Яндекс Плюс", amountKopecks = 39_900, categoryId = 4, periodUnit = "month", nextEpochDay = nov(20).toEpochDay())
        val d = run(AssistantQuery(intent = Intents.SUBSCRIPTIONS), ctx(recurring = listOf(r)))
        assertTrue("Яндекс Плюс" in d.facts)
        assertTrue("20 ноября" in d.facts)
        assertNotNull(d.headline)
    }

    @Test
    fun incomeShowsBalance() {
        val inc = Income(amountKopecks = 5_000_000, source = "Зарплата", timestamp = nov(5).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val rec = inc.copy(amountKopecks = 1_000, origin = IncomeOrigin.RECONCILE)
        val d = run(AssistantQuery(intent = Intents.INCOME), ctx(incomes = listOf(inc, rec)))
        assertTrue("Зарплата" in d.facts)
        assertTrue(d.headline!!.startsWith("50"))
    }

    @Test
    fun affordWithoutIncomeSaysBalanceIsUnknown() {
        val d = run(AssistantQuery(intent = Intents.AFFORD, amount = 15000.0))
        assertTrue(d.facts, "неизвестно" in d.facts)
        assertNull(d.headline)
    }

    @Test
    fun affordCountsUpcomingPaymentsAndIncome() {
        val inc = Income(amountKopecks = 10_000_000, timestamp = nov(1).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val rent = Recurring(title = "Аренда", amountKopecks = 3_000_000, categoryId = 5, periodUnit = "month", nextEpochDay = nov(25).toEpochDay())
        val d = run(AssistantQuery(intent = Intents.AFFORD, amount = 15000.0), ctx(incomes = listOf(inc), recurring = listOf(rent)))
        assertTrue("Аренда" in d.facts)
        assertNotNull(d.headline)
    }

    @Test
    fun unknownIntentAnswersDirectly() {
        val d = run(AssistantQuery(intent = Intents.UNKNOWN))
        assertNotNull(d.direct)
    }

    @Test
    fun decodeHandlesFencesAndUnknownIntent() {
        val q = AssistantQueries.decode("```json\n{\"intent\":\"total\",\"categories\":[\"Кафе\"],\"extra\":1}\n```")!!
        assertEquals(Intents.TOTAL, q.intent)
        assertEquals(listOf("Кафе"), q.categories)
        assertEquals(Intents.UNKNOWN, AssistantQueries.decode("{\"intent\":\"dance\"}")!!.intent)
        assertNull(AssistantQueries.decode("не знаю"))
    }

    @Test
    fun spanFormatting() {
        assertEquals("1–14 ноября", AssistantEngine.span(nov(1), nov(14)))
        assertEquals("14 ноября", AssistantEngine.span(nov(14), nov(14)))
        assertEquals("28 октября — 3 ноября", AssistantEngine.span(oct(28), nov(3)))
    }

    @Test
    fun queryUserKeepsOnlyRecentHistory() {
        val h = (1..4).map { AssistantTurn("q$it", "{}") }
        val text = AssistantPrompts.queryUser("а в сентябре?", h)
        assertFalse("q1" in text)
        assertTrue("q3" in text && "q4" in text && "а в сентябре?" in text)
    }

    // --- Шаг 2: план трат и баланс ---

    private val cafePlan = Budget(id = 1, categoryId = 2, month = 202611, amountKopecks = 300_000) // 3000 ₽ на кафе
    private val foodPlan = Budget(id = 2, categoryId = 1, month = 202610, amountKopecks = 500_000) // повторяется с октября

    @Test
    fun planShowsProgressAndRepeatingRows() {
        val d = run(AssistantQuery(intent = Intents.PLAN), ctx(budgets = listOf(cafePlan, foodPlan)))
        // продукты: 1000 + 2500 + 600 = 4100 из 5000; кафе 450 из 3000
        assertTrue(d.facts, "Продукты: план 5" in d.facts)
        assertTrue(d.facts, "Кафе и рестораны: план 3" in d.facts)
    }

    @Test
    fun planForOneCategory() {
        val d = run(AssistantQuery(intent = Intents.PLAN, categories = listOf("Кафе и рестораны")), ctx(budgets = listOf(cafePlan, foodPlan)))
        assertTrue(d.headline!!.startsWith("+2"))
        assertTrue("Кафе" in d.caption!!)
    }

    @Test
    fun planForCategoryWithoutSpendingIsKnown() {
        // «Спорт» есть среди категорий, но трат в нём нет — это не «неизвестная категория»
        val d = run(AssistantQuery(intent = Intents.PLAN, categories = listOf("Спорт")), ctx(budgets = listOf(cafePlan)))
        assertTrue(d.facts, "для этой категории плана нет" in d.facts)
    }

    @Test
    fun noPlanIsExplained() {
        val d = run(AssistantQuery(intent = Intents.PLAN))
        assertTrue("не задан" in d.facts)
        assertNull(d.headline)
    }

    @Test
    fun balanceKnownAndUnknown() {
        assertTrue("неизвестен" in run(AssistantQuery(intent = Intents.BALANCE)).facts)
        val d = run(AssistantQuery(intent = Intents.BALANCE), ctx(balance = Balance(4_000_000, nov(10))))
        assertTrue(d.headline!!.startsWith("40"))
        assertTrue("10 ноября" in d.caption!!)
    }

    @Test
    fun affordPrefersBalanceAndSubtractsUpcoming() {
        val rent = Recurring(title = "Аренда", amountKopecks = 3_000_000, categoryId = 5, periodUnit = "month", nextEpochDay = nov(25).toEpochDay())
        val d = run(
            AssistantQuery(intent = Intents.AFFORD, amount = 5000.0),
            ctx(recurring = listOf(rent), balance = Balance(10_000_000, nov(10))),
        )
        assertTrue(d.facts, "На счёте сейчас" in d.facts)
        assertTrue("Аренда" in d.facts)
        assertEquals("останется к концу периода с покупкой", d.caption)
    }

    @Test
    fun affordInCategoryUsesItsPlan() {
        val d = run(
            AssistantQuery(intent = Intents.AFFORD, amount = 4000.0, categories = listOf("Кафе и рестораны")),
            ctx(budgets = listOf(cafePlan)),
        )
        // 3000 − 450 − 4000 = −1450
        assertTrue(d.facts, "План «Кафе и рестораны»" in d.facts)
        assertTrue(d.headline!!.startsWith("−1"))
    }

    @Test
    fun adviceMentionsPlanOverrunAndExpensiveSubscriptions() {
        val tightPlan = Budget(id = 3, categoryId = 1, month = 202611, amountKopecks = 200_000) // 2000 при 4100 потраченных
        val plus = Recurring(title = "Кинопоиск", amountKopecks = 39_900, categoryId = 4, periodUnit = "month", nextEpochDay = nov(20).toEpochDay())
        val d = run(AssistantQuery(intent = Intents.ADVICE), ctx(budgets = listOf(tightPlan), recurring = listOf(plus)))
        assertTrue(d.facts, "превышен" in d.facts)
        assertTrue("Кинопоиск" in d.facts && "сэкономит" in d.facts)
    }

    // --- Предложения в план трат ---

    @Test
    fun suggestPlanFromPreviousPeriods() {
        val d = run(AssistantQuery(intent = Intents.SUGGEST_PLAN), ctx(budgets = listOf(cafePlan)))
        // октябрь: продукты 3000 → 2700, кафе 900 → 800 (−10 %, до 100 ₽);
        // транспорта в октябре не было — по текущему периоду: одна поездка 1200 ₽, темп не размножаем
        assertEquals(listOf("Продукты", "Транспорт", "Кафе и рестораны"), d.planProposals.map { it.title })
        assertEquals(270_000L, d.planProposals[0].amountKopecks)
        assertEquals(120_000L, d.planProposals[1].amountKopecks)
        assertEquals("по тратам этого периода", d.planProposals[1].basis)
        assertEquals(80_000L, d.planProposals[2].amountKopecks)
        assertEquals(300_000L, d.planProposals[2].currentKopecks)
        assertEquals(202611, d.planMonth)
    }

    @Test
    fun suggestPlanForNamedCategoryOnly() {
        val d = run(AssistantQuery(intent = Intents.SUGGEST_PLAN, categories = listOf("Кафе и рестораны")))
        assertEquals(listOf("Кафе и рестораны"), d.planProposals.map { it.title })
    }

    @Test
    fun setPlanMakesOneProposalButChangesNothing() {
        val d = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Такси"), amount = 3000.0))
        val p = d.planProposals.single()
        assertEquals(3L to 30L, p.categoryId to p.subcategoryId)
        assertEquals(300_000L, p.amountKopecks)
        assertTrue("подтверждает" in d.facts)
    }

    @Test
    fun setPlanWithoutAmountAsksAgain() {
        val d = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Продукты")))
        assertTrue(d.planProposals.isEmpty())
        assertTrue("сумму" in d.facts)
    }

    @Test
    fun setPlanFindsCategoryByShortName() {
        val p = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("кафе"), amount = 2500.0)).planProposals.single()
        assertEquals(2L to null, p.categoryId to p.subcategoryId)
        assertEquals(null, p.newSubcategory)
    }

    @Test
    fun setPlanOnMissingSubcategoryProposesToCreateIt() {
        val d = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("метро"), amount = 1500.0))
        val p = d.planProposals.single()
        assertEquals(3L, p.categoryId)
        assertEquals("Метро", p.newSubcategory)
        assertEquals(150_000L, p.amountKopecks)
        assertTrue("Подкатегории «Метро» пока нет" in d.facts)
    }

    @Test
    fun setPlanOnUnrelatedNameAsksForCategory() {
        val d = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Хобби"), amount = 1500.0))
        assertTrue(d.planProposals.isEmpty())
        assertTrue("уточнить" in d.facts)
    }

    @Test
    fun commandsAboutPlanAreRecognizedWithoutModel() {
        val afford = AssistantQuery(intent = Intents.AFFORD, categories = listOf("Кафе и рестораны"))
        val fixed = AssistantQueries.correct("Поставь лимит на кафе 2500", afford)
        assertEquals(Intents.SET_PLAN, fixed.intent)
        assertEquals(2500.0, fixed.amount!!, 0.0)
        assertEquals(3000.0, AssistantQueries.correct("Поставь лимит на такси 3 000.", AssistantQuery(intent = Intents.PLAN)).amount!!, 0.0)
        assertEquals(8000.0, AssistantQueries.correct("установи план на кафе 8 тысяч", AssistantQuery()).amount!!, 0.0)
        assertEquals(Intents.SUGGEST_PLAN, AssistantQueries.correct("План трат на месяц", AssistantQuery(intent = Intents.ADVICE)).intent)
        assertEquals(Intents.SUGGEST_PLAN, AssistantQueries.correct("Предложи план трат на месяц", AssistantQuery(intent = Intents.ADVICE)).intent)
        assertEquals(Intents.PLAN, AssistantQueries.correct("Как я иду по плану трат?", AssistantQuery(intent = Intents.PLAN)).intent)
        assertEquals(Intents.AFFORD, AssistantQueries.correct("Могу ли я сходить в кафе за 2500?", afford).intent)
    }

    private val kino = Recurring(id = 5, title = "Кинопоиск", amountKopecks = 39_900, categoryId = 4, periodUnit = "month", nextEpochDay = nov(20).toEpochDay())
    private val plus = Recurring(id = 6, title = "Яндекс Плюс", amountKopecks = 29_900, categoryId = 4, periodUnit = "month", nextEpochDay = nov(22).toEpochDay())
    private val music = Recurring(id = 7, title = "Яндекс Музыка", amountKopecks = 19_900, categoryId = 4, periodUnit = "month", nextEpochDay = nov(23).toEpochDay())

    @Test
    fun removePlanProposesRemovalOnlyForExistingPlan() {
        val d = run(AssistantQuery(intent = Intents.REMOVE_PLAN, categories = listOf("Кафе и рестораны")), ctx(budgets = listOf(cafePlan)))
        val p = d.planProposals.single()
        assertTrue(p.remove)
        assertEquals(300_000L, p.currentKopecks)
        assertEquals(2L, p.categoryId)
        assertTrue("не убран" in d.facts)
    }

    @Test
    fun removePlanWithoutPlanOrNameProposesNothing() {
        val none = run(AssistantQuery(intent = Intents.REMOVE_PLAN, categories = listOf("Продукты")), ctx(budgets = listOf(cafePlan)))
        assertTrue(none.planProposals.isEmpty())
        assertTrue("плана нет" in none.facts && "Кафе и рестораны" in none.facts)
        val unnamed = run(AssistantQuery(intent = Intents.REMOVE_PLAN), ctx(budgets = listOf(cafePlan)))
        assertTrue(unnamed.planProposals.isEmpty())
        assertTrue("назвать" in unnamed.facts)
    }

    @Test
    fun relativeSetPlanAddsToCurrentPlan() {
        val up = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Кафе и рестораны"), amount = 1000.0, relative = true), ctx(budgets = listOf(cafePlan)))
        assertEquals(400_000L, up.planProposals.single().amountKopecks)
        val down = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Кафе и рестораны"), amount = -500.0, relative = true), ctx(budgets = listOf(cafePlan)))
        assertEquals(250_000L, down.planProposals.single().amountKopecks)
        val noPlan = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Продукты"), amount = 500.0, relative = true), ctx(budgets = listOf(cafePlan)))
        assertTrue(noPlan.planProposals.isEmpty())
        val toZero = run(AssistantQuery(intent = Intents.SET_PLAN, categories = listOf("Кафе и рестораны"), amount = -5000.0, relative = true), ctx(budgets = listOf(cafePlan)))
        assertTrue(toZero.planProposals.isEmpty() && "убери" in toZero.facts)
    }

    @Test
    fun cancelSubscriptionProposesOneChangeAndWarnsAboutService() {
        val d = run(AssistantQuery(intent = Intents.CANCEL_SUB, place = "кинопоиск"), ctx(recurring = listOf(kino, plus)))
        val ch = d.subscriptionChanges.single()
        assertEquals(5L to SubAction.CANCEL, ch.id to ch.action)
        assertTrue("самом сервисе" in d.facts && "ничего не изменено" in d.facts)
        assertEquals(formatRub(39_900L * 12), d.headline)
    }

    @Test
    fun ambiguousOrUnknownSubscriptionIsNotCancelled() {
        val c = ctx(recurring = listOf(plus, music))
        val several = run(AssistantQuery(intent = Intents.CANCEL_SUB, place = "яндекс"), c)
        assertTrue(several.subscriptionChanges.isEmpty())
        assertTrue("несколько" in several.facts)
        val exact = run(AssistantQuery(intent = Intents.CANCEL_SUB, place = "Яндекс Плюс"), c)
        assertEquals(6L, exact.subscriptionChanges.single().id)
        val unknown = run(AssistantQuery(intent = Intents.CANCEL_SUB, place = "Netflix"), c)
        assertTrue(unknown.subscriptionChanges.isEmpty() && "Яндекс Плюс" in unknown.facts)
        val unnamed = run(AssistantQuery(intent = Intents.CANCEL_SUB), c)
        assertTrue(unnamed.subscriptionChanges.isEmpty() && "назвать" in unnamed.facts)
    }

    @Test
    fun categoriesFieldWorksAsSubscriptionNameToo() {
        val d = run(AssistantQuery(intent = Intents.CANCEL_SUB, categories = listOf("Кинопоиск")), ctx(recurring = listOf(kino)))
        assertEquals(5L, d.subscriptionChanges.single().id)
    }

    @Test
    fun pauseAndResumeUseMatchingState() {
        val paused = kino.copy(state = RecurringState.PAUSED)
        val c = ctx(recurring = listOf(paused, plus))
        assertTrue(run(AssistantQuery(intent = Intents.PAUSE_SUB, place = "кинопоиск"), c).subscriptionChanges.isEmpty())
        assertEquals(SubAction.PAUSE, run(AssistantQuery(intent = Intents.PAUSE_SUB, place = "плюс"), c).subscriptionChanges.single().action)
        assertEquals(5L, run(AssistantQuery(intent = Intents.RESUME_SUB, place = "кинопоиск"), c).subscriptionChanges.single().id)
        assertTrue(run(AssistantQuery(intent = Intents.RESUME_SUB, place = "плюс"), c).subscriptionChanges.isEmpty())
    }

    @Test
    fun dismissedSubscriptionIsInvisible() {
        val gone = kino.copy(state = RecurringState.DISMISSED)
        val d = run(AssistantQuery(intent = Intents.CANCEL_SUB, place = "кинопоиск"), ctx(recurring = listOf(gone)))
        assertTrue(d.subscriptionChanges.isEmpty())
    }

    @Test
    fun editSubscriptionChangesAmountAndDate() {
        val c = ctx(recurring = listOf(kino))
        val d = run(AssistantQuery(intent = Intents.EDIT_SUB, place = "кинопоиск", amount = 499.0, from = nov(25).toString()), c)
        val ch = d.subscriptionChanges.single()
        assertEquals(49_900L, ch.newAmountKopecks)
        assertEquals(nov(25), ch.newNextDay)
        val past = run(AssistantQuery(intent = Intents.EDIT_SUB, place = "кинопоиск", from = nov(1).toString()), c)
        assertTrue(past.subscriptionChanges.isEmpty() && "что в ней менять" in past.facts)
    }

    @Test
    fun subscriptionAndPlanCommandsAreRecognizedWithoutModel() {
        fun fix(text: String, q: AssistantQuery = AssistantQuery(intent = Intents.ADVICE)) = AssistantQueries.correct(text, q)
        val cancel = fix("Отмени подписку Яндекс Плюс")
        assertEquals(Intents.CANCEL_SUB, cancel.intent)
        assertEquals("яндекс плюс", cancel.place)
        assertEquals("кинопоиск", fix("удали подписку на Кинопоиск").place)
        assertEquals(Intents.PAUSE_SUB, fix("Поставь подписку Netflix на паузу").intent)
        assertEquals(Intents.RESUME_SUB, fix("Возобнови подписку Netflix").intent)
        val edit = fix("Измени подписку Кинопоиск на 499")
        assertEquals(Intents.EDIT_SUB, edit.intent)
        assertEquals(499.0, edit.amount!!, 0.0)
        assertEquals(Intents.REMOVE_PLAN, fix("Убери лимит на кафе").intent)
        assertEquals(Intents.REMOVE_PLAN, fix("Отмени план на такси").intent)
        val up = fix("Увеличь лимит на кафе на 1000")
        assertTrue(up.intent == Intents.SET_PLAN && up.relative && up.amount == 1000.0)
        val down = fix("Уменьши лимит на кафе на 500")
        assertTrue(down.relative && down.amount == -500.0)
        val to = fix("Подними лимит на кафе до 5000")
        assertTrue(to.intent == Intents.SET_PLAN && !to.relative && to.amount == 5000.0)
        assertEquals(Intents.SET_PLAN, fix("Измени лимит на кафе на 5000").intent)
        assertFalse(fix("Измени лимит на кафе на 5000").relative)
        // обычные вопросы не трогаем
        assertEquals(Intents.SUBSCRIPTIONS, fix("Какие у меня подписки?", AssistantQuery(intent = Intents.SUBSCRIPTIONS)).intent)
        assertEquals(Intents.PLAN, fix("Как я иду по плану трат?", AssistantQuery(intent = Intents.PLAN)).intent)
    }
}
