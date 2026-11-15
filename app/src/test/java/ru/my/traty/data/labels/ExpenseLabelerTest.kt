package ru.my.traty.data.labels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.recurring.RecurrencePeriod
import ru.my.traty.data.recurring.RecurringCandidate
import java.time.LocalDate
import java.time.ZoneOffset

class ExpenseLabelerTest {
    private val zone = ZoneOffset.UTC
    private val base = LocalDate.of(2026, 8, 1)
    private var nextId = 1L

    private fun exp(
        day: Int,
        hour: Int = 12,
        kopecks: Long = 40_000,
        cat: Long = 1,
        sub: Long? = 10,
        place: String? = null,
        note: String? = null,
        catName: String = "Здоровье",
        subName: String? = "Аптека",
        source: String = ExpenseSource.VOICE,
    ) = ExpenseWithCategory(
        expense = Expense(
            id = nextId++, amountKopecks = kopecks, categoryId = cat, subcategoryId = sub, place = place, note = note,
            timestamp = base.plusDays(day.toLong()).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), source = source,
        ),
        categoryName = catName, categoryEmoji = "💊", subcategoryName = subName,
    )

    private fun label(rows: List<ExpenseWithCategory>, subscriptions: Set<Long> = emptySet()) =
        ExpenseLabeler.label(rows, subscriptions, zone)

    private fun labelsOf(map: Map<Long, List<ExpenseLabelHit>>, row: ExpenseWithCategory) =
        map[row.expense.id].orEmpty().map { it.label }

    /** 6 обычных покупок раз в неделю на 400 ₽. */
    private fun usual(count: Int = 6, kopecks: Long = 40_000, sub: Long? = 10, subName: String? = "Аптека") =
        (0 until count).map { exp(it * 7, kopecks = kopecks, sub = sub, subName = subName) }

    @Test
    fun nothingIsLabeledWhileHistoryIsShort() {
        val rows = (0..5).map { exp(it * 3, kopecks = 40_000) } + exp(20, kopecks = 900_000, place = "Новая аптека")
        assertTrue(label(rows).isEmpty())
    }

    @Test
    fun hugePurchaseIsLargeWithRatioInReason() {
        val rows = usual()
        val big = exp(42, kopecks = 300_000)
        val result = label(rows + big)
        assertEquals(listOf(ExpenseLabel.LARGE), labelsOf(result, big))
        val reason = result.getValue(big.expense.id).single().reason
        assertTrue(reason, "в 7,5 раза" in reason)
        assertTrue(reason, "Здоровье → Аптека" in reason)
        // обычные покупки не помечаются
        assertTrue(rows.all { labelsOf(result, it).isEmpty() })
    }

    @Test
    fun smallAmountsAreNeverLargeAndNeedAtLeastFiveSamples() {
        // 250 ₽ против медианы 50 ₽ — ниже порога 300 ₽
        val cheap = usual(kopecks = 5_000)
        val lowFloor = exp(42, kopecks = 25_000)
        assertTrue(labelsOf(label(cheap + lowFloor), lowFloor).isEmpty())
        // всего 4 предыдущие траты — сравнивать не с чем
        val few = (0 until 4).map { exp(it * 12, kopecks = 40_000) }
        val big = exp(50, kopecks = 300_000)
        assertTrue(labelsOf(label(few + big), big).isEmpty())
    }

    @Test
    fun categoryIsUsedWhenSubcategoryHasTooFewSamples() {
        val inOtherSub = usual(sub = 11, subName = "Врач")
        val big = exp(42, kopecks = 300_000, sub = 10, subName = "Аптека")
        assertEquals(listOf(ExpenseLabel.LARGE), labelsOf(label(inOtherSub + big), big))
    }

    @Test
    fun oldHistoryOutsideNinetyDaysIsIgnored() {
        val old = usual()
        val big = exp(200, kopecks = 300_000)
        assertTrue(labelsOf(label(old + big), big).isEmpty())
    }

    @Test
    fun burstAfterQuietPeriodIsFrequent() {
        val weekly = (0..11).map { exp(it * 7) }
        val burst = listOf(exp(80), exp(81), exp(82))
        val result = label(weekly + burst)
        assertEquals(listOf(ExpenseLabel.FREQUENT), labelsOf(result, burst[2]))
        assertEquals(listOf(ExpenseLabel.FREQUENT), labelsOf(result, burst[1]))
        assertTrue(labelsOf(result, burst[0]).isEmpty())
        assertTrue(weekly.all { labelsOf(result, it).isEmpty() })
        assertTrue("за 7 дней" in result.getValue(burst[2].expense.id).single().reason)
    }

    @Test
    fun lateEveningInTypicalImpulseCategoryIsImpulsive() {
        val night = exp(3, hour = 23, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val earlyMorning = exp(4, hour = 2, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val noon = exp(5, hour = 12, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val groceriesAtNight = exp(6, hour = 23, cat = 3, sub = null, catName = "Продукты", subName = null)
        val result = label(listOf(night, earlyMorning, noon, groceriesAtNight))
        assertEquals(listOf(ExpenseLabel.IMPULSIVE), labelsOf(result, night))
        assertEquals(listOf(ExpenseLabel.IMPULSIVE), labelsOf(result, earlyMorning))
        assertTrue(labelsOf(result, noon).isEmpty())
        assertTrue(labelsOf(result, groceriesAtNight).isEmpty())
        assertTrue("23:00" in result.getValue(night.expense.id).single().reason)
    }

    @Test
    fun impulsiveFlagComesFromTheListOfCategories() {
        val groceries = (0..2).map { exp(7, 23, kopecks = 20_000, cat = 3, sub = null, catName = "Продукты", subName = null) }
        val cafe = exp(8, 23, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val custom = ExpenseLabeler.label(groceries + cafe, emptySet(), zone, impulsiveCategories = listOf("продукты"))
        assertTrue(groceries.all { labelsOf(custom, it).contains(ExpenseLabel.IMPULSIVE) })
        // «Кафе» флажок снят — ⚡ нет, даже ночью
        assertTrue(labelsOf(custom, cafe).isEmpty())
        // совсем без импульсивных категорий метки ⚡ не бывает
        assertTrue(ExpenseLabeler.label(groceries + cafe, emptySet(), zone, impulsiveCategories = emptyList()).isEmpty())
    }

    @Test
    fun spendOverPlanIsImpulsiveOnlyInImpulsiveCategory() {
        val cafe = exp(5, hour = 12, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val pharmacy = exp(5, hour = 12)
        val overPlan = mapOf(cafe.expense.id to "Трата сверх плана.", pharmacy.expense.id to "Трата сверх плана.")
        val result = ExpenseLabeler.label(listOf(cafe, pharmacy), emptySet(), zone, overPlan = overPlan)
        assertEquals(listOf(ExpenseLabel.IMPULSIVE), labelsOf(result, cafe))
        assertEquals("Трата сверх плана.", result.getValue(cafe.expense.id).single().reason)
        // «Здоровье» не импульсивная категория — перерасход не делает трату импульсивной
        assertTrue(labelsOf(result, pharmacy).isEmpty())
    }

    @Test
    fun nightSpendOverPlanKeepsBothReasons() {
        val cafe = exp(5, hour = 23, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val result = ExpenseLabeler.label(listOf(cafe), emptySet(), zone, overPlan = mapOf(cafe.expense.id to "Трата сверх плана."))
        val reason = result.getValue(cafe.expense.id).single().reason
        assertTrue("23:00" in reason)
        assertTrue("сверх плана" in reason)
    }

    @Test
    fun threeSmallPurchasesPerDayAreImpulsive() {
        fun cafe(day: Int, hour: Int) = exp(day, hour, kopecks = 20_000, cat = 2, sub = null, catName = "Кафе и рестораны", subName = null)
        val three = listOf(cafe(1, 9), cafe(1, 12), cafe(1, 16))
        val two = listOf(cafe(2, 9), cafe(2, 12))
        val result = label(three + two)
        assertTrue(three.all { labelsOf(result, it) == listOf(ExpenseLabel.IMPULSIVE) })
        assertTrue(two.all { labelsOf(result, it).isEmpty() })
        assertTrue("3 мелкие траты" in result.getValue(three[0].expense.id).single().reason)
        // та же картина в «Продуктах» — не импульс
        val groceries = (0..2).map { exp(7, 9 + it, kopecks = 20_000, cat = 3, sub = null, catName = "Продукты", subName = null) }
        assertTrue(label(groceries).isEmpty())
    }

    @Test
    fun firstPurchaseAtAPlaceIsNewOnlyWithEnoughHistory() {
        val history = usual(7)
        val first = exp(45, place = "Пятёрочка", kopecks = 30_000)
        val second = exp(50, place = "магазин пятерочка", kopecks = 30_000)
        val result = label(history + first + second)
        assertEquals(listOf(ExpenseLabel.NEW_PLACE), labelsOf(result, first))
        assertTrue(labelsOf(result, second).isEmpty())

        // место, с которого всё началось, новым не считается, и позже тоже
        val early = exp(10, place = "Магнит", kopecks = 30_000)
        val later = exp(45, place = "Магнит", kopecks = 30_000)
        assertTrue(labelsOf(label(history + early + later), early).isEmpty())
        assertTrue(labelsOf(label(history + early + later), later).isEmpty())
    }

    @Test
    fun subscriptionLabelDoesNotNeedHistory() {
        val a = exp(1, place = "Яндекс Плюс", kopecks = 39_900)
        val b = exp(2, place = "Магнит", kopecks = 10_000)
        val result = label(listOf(a, b), subscriptions = setOf(a.expense.id))
        assertEquals(listOf(ExpenseLabel.SUBSCRIPTION), labelsOf(result, a))
        assertTrue(labelsOf(result, b).isEmpty())

        val paid = exp(3, source = ExpenseSource.RECURRING)
        val reason = label(listOf(paid), setOf(paid.expense.id)).getValue(paid.expense.id).single().reason
        assertTrue(reason, "Списалось" in reason)
    }

    @Test
    fun reconcileEntriesAreNeverLabeledNorCounted() {
        val rec = exp(1, kopecks = 9_000_000, cat = 5, sub = null, catName = "Неучтённые", subName = null, source = ExpenseSource.RECONCILE)
        assertTrue(label(listOf(rec), setOf(rec.expense.id)).isEmpty())
        // сверка не сдвигает начало истории: до неё трат не было, 30 дней отсчитываются от первой настоящей
        val real = exp(20, place = "Новое")
        assertTrue(label(listOf(rec, real)).isEmpty())
    }

    @Test
    fun labelsComeInDisplayOrder() {
        val rows = usual(7)
        val big = exp(45, kopecks = 300_000, place = "Дорогая аптека")
        val labels = labelsOf(label(rows + big, setOf(big.expense.id)), big)
        assertEquals(listOf(ExpenseLabel.LARGE, ExpenseLabel.SUBSCRIPTION, ExpenseLabel.NEW_PLACE), labels)
    }

    @Test
    fun subscriptionIdsCollectCandidatesRecurringMatchesAndPaidCharges() {
        val inCandidate = exp(1, place = "Облако")
        val matchesPayment = exp(2, place = "Яндекс Плюс", cat = 1)
        val matchesDismissed = exp(3, place = "Кино", cat = 1)
        val byNote = exp(4, place = null, note = "Оплата связи", cat = 1)
        val paid = exp(5, source = ExpenseSource.RECURRING)
        val other = exp(6, place = "Пятёрочка")

        fun payment(key: String, state: String = RecurringState.ACTIVE) = Recurring(
            title = "x", amountKopecks = 1, categoryId = 1, periodUnit = "month", nextEpochDay = 0, state = state, detectKey = key,
        )
        val candidate = RecurringCandidate(
            key = "1|облако", title = "Облако", place = "Облако", categoryId = 1, subcategoryId = null, amountKopecks = 1,
            period = RecurrencePeriod.MONTHLY, charges = 3, lastDate = base, nextDate = base, expenseIds = listOf(inCandidate.expense.id),
        )
        val ids = ExpenseLabeler.subscriptionExpenseIds(
            expenses = listOf(inCandidate, matchesPayment, matchesDismissed, byNote, paid, other),
            recurring = listOf(
                payment("1|яндекс плюс"),
                payment("1|кино", RecurringState.DISMISSED),
                payment("1|оплата связи", RecurringState.PAUSED),
            ),
            candidates = listOf(candidate),
        )
        assertEquals(
            setOf(inCandidate, matchesPayment, byNote, paid).map { it.expense.id }.toSet(),
            ids,
        )
        assertFalse(other.expense.id in ids)
    }

    @Test
    fun pluralForms() {
        assertEquals("трата", ExpenseLabeler.plural(1, "трата", "траты", "трат"))
        assertEquals("траты", ExpenseLabeler.plural(3, "трата", "траты", "трат"))
        assertEquals("трат", ExpenseLabeler.plural(5, "трата", "траты", "трат"))
        assertEquals("трат", ExpenseLabeler.plural(12, "трата", "траты", "трат"))
        assertEquals("трата", ExpenseLabeler.plural(21, "трата", "траты", "трат"))
    }
}
