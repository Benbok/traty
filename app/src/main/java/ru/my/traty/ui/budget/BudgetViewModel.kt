package ru.my.traty.ui.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.BudgetPlans
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.budget.TotalProgress
import ru.my.traty.data.budget.PlanKey
import ru.my.traty.data.budget.PlanMonth
import ru.my.traty.data.budget.PlanProgress
import ru.my.traty.data.budget.PlanProgresses
import ru.my.traty.data.budget.PlanSpend
import ru.my.traty.data.budget.Promised
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.selectable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Строка плана для экрана: прогресс и подписи категории. */
data class PlanItem(
    val progress: PlanProgress,
    val emoji: String,
    val categoryName: String,
    val subcategoryName: String?,
) {
    val key: PlanKey get() = PlanKey(progress.plan.categoryId, progress.plan.subcategoryId)
    val title: String get() = if (subcategoryName != null) "$categoryName · $subcategoryName" else categoryName
}

/** Итог по планам месяца. План подкатегории не суммируется, если у её категории есть свой план. */
data class PlanTotals(val planned: Long, val spent: Long)

fun ExpenseWithCategory.toPlanSpend(zone: ZoneId) = PlanSpend(
    categoryId = expense.categoryId,
    subcategoryId = expense.subcategoryId,
    amountKopecks = expense.amountKopecks,
    date = Instant.ofEpochMilli(expense.timestamp).atZone(zone).toLocalDate(),
)

/** Планы периода с прогрессом и названиями категорий. Общая сборка для экрана плана, Статистики и Трат. */
fun planItems(
    rows: List<Budget>,
    period: BillingPeriod,
    periodExpenses: List<ExpenseWithCategory>,
    categories: List<CategoryWithSubs>,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(),
): List<PlanItem> {
    val plans = BudgetPlans.effective(rows, PlanMonth.of(period))
    if (plans.isEmpty()) return emptyList()
    val spent = BudgetPlans.spent(periodExpenses.map { it.toPlanSpend(zone) })
    val byId = categories.associateBy { it.category.id }
    return PlanProgresses.all(plans, spent, period, today).mapNotNull { p ->
        // План архивной категории не показываем: тратить в неё всё равно нельзя
        val cat = byId[p.plan.categoryId] ?: return@mapNotNull null
        PlanItem(
            progress = p,
            emoji = cat.category.emoji,
            categoryName = cat.category.name,
            subcategoryName = p.plan.subcategoryId?.let { id -> cat.subs.firstOrNull { it.id == id }?.name },
        )
    }
}

fun planTotals(items: List<PlanItem>): PlanTotals {
    val withCategoryPlan = items.filter { it.progress.plan.subcategoryId == null }.map { it.progress.plan.categoryId }.toSet()
    val counted = items.filter { it.progress.plan.subcategoryId == null || it.progress.plan.categoryId !in withCategoryPlan }
    return PlanTotals(counted.sumOf { it.progress.planned }, counted.sumOf { it.progress.spent })
}

/** Экран «План трат»: планы выбранного месяца, добавление, правка и подсказка суммы. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val repo = c.budgets
    private val zone: ZoneId = ZoneId.systemDefault()

    val startDay: StateFlow<Int> = c.settings.flow.map { it.periodStartDay }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    /** Выбранный месяц (ГГГГММ); null — текущий расчётный период. */
    private val month = MutableStateFlow<Int?>(null)

    val period: StateFlow<BillingPeriod> = combine(month, startDay) { m, day ->
        if (m == null) BillingPeriod.containing(LocalDate.now(), day) else PlanMonth.period(m, day)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BillingPeriod.containing(LocalDate.now(), 1))

    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeActiveWithSubs().map { it.selectable() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rows: StateFlow<List<Budget>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val periodExpenses = period.flatMapLatest { p ->
        c.db.expenses().observeRange(p.startMillis(zone), p.endMillisExclusive(zone))
    }

    val items: StateFlow<List<PlanItem>> =
        combine(rows, period, periodExpenses, categories) { r, p, e, cats -> planItems(r, p, e, cats, zone) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val totalRows: StateFlow<List<MonthBudget>> =
        repo.observeTotals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Общий лимит месяца на все категории; null — не задан. */
    val total: StateFlow<TotalProgress?> =
        combine(totalRows, period, periodExpenses) { r, p, e -> totalProgress(r, p, e) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Что ещё спишется до конца периода: показывается в карточке общего лимита. */
    val promised: StateFlow<Promised> = promisedFlow(c, period, zone)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Promised())

    val alertsEnabled: StateFlow<Boolean> = c.settings.flow.map { it.budgetAlertsEnabled }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Вся история трат — для подсказки суммы плана. */
    private val history: StateFlow<List<PlanSpend>> =
        c.db.expenses().observeAllWithCategory().map { list -> list.map { it.toPlanSpend(zone) } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Подсказка суммы: средняя за 3 прошлых периода минус 10 %. */
    fun hint(key: PlanKey): Long? = BudgetPlans.hint(history.value, key, period.value, startDay.value)

    /** Есть ли у [key] план, заданный именно на этот месяц, и повторяется ли он. */
    fun ownRow(key: PlanKey): Budget? {
        val m = PlanMonth.of(period.value)
        return rows.value.filter { it.month == m && it.categoryId == key.categoryId && it.subcategoryId == key.subcategoryId }
            .maxByOrNull { it.id }
    }

    fun shift(direction: Int) {
        val p = period.value
        val next = if (direction > 0) p.next(startDay.value) else p.previous(startDay.value)
        month.value = PlanMonth.of(next)
    }

    fun save(key: PlanKey, amountKopecks: Long, repeats: Boolean) {
        val m = PlanMonth.of(period.value)
        viewModelScope.launch { repo.set(key, m, amountKopecks, repeats) }
    }

    fun remove(key: PlanKey) {
        val m = PlanMonth.of(period.value)
        viewModelScope.launch { repo.removeFrom(key, m) }
    }

    /** Повторяется ли общий лимит, заданный именно на этот месяц (для диалога правки). */
    fun ownTotalRepeats(): Boolean? {
        val m = PlanMonth.of(period.value)
        return totalRows.value.filter { it.month == m }.maxByOrNull { it.id }?.repeats
    }

    fun saveTotal(amountKopecks: Long, repeats: Boolean) {
        val m = PlanMonth.of(period.value)
        viewModelScope.launch { repo.setTotal(m, amountKopecks, repeats) }
    }

    fun removeTotal() {
        val m = PlanMonth.of(period.value)
        viewModelScope.launch { repo.removeTotalFrom(m) }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { c.settings.setBudgetAlertsEnabled(enabled) }
    }
}
