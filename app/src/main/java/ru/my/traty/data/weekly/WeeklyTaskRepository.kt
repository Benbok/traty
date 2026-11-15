package ru.my.traty.data.weekly

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.recurring.RecurringSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Траты, по которым считаются задания: без сверки и без списаний подписок (их неделей не сократить). */
fun List<ExpenseWithCategory>.toTaskSpends(zone: ZoneId): List<TaskSpend> =
    filter { it.expense.source != ExpenseSource.RECONCILE && it.expense.source != ExpenseSource.RECURRING }
        .map { TaskSpend(it.expense.categoryId, it.expense.amountKopecks, Instant.ofEpochMilli(it.expense.timestamp).atZone(zone).toLocalDate()) }

/** Задания на неделю: создание по понедельникам, замена, свои задания и отметки. */
class WeeklyTaskRepository(private val db: AppDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dao = db.weeklyTasks()

    fun observeAll(): Flow<List<WeeklyTask>> = dao.observeAll()

    /**
     * Если на неделю [today] заданий ещё не было, предлагает их по истории трат.
     * Если данных мало, ничего не создаёт и попробует в следующий раз.
     * @return задания недели после вызова (только действующие).
     */
    suspend fun ensureWeek(today: LocalDate = LocalDate.now()): List<WeeklyTask> = db.withTransaction {
        val week = Weeks.monday(today)
        val existing = dao.getWeek(week.toEpochDay())
        if (existing.isNotEmpty()) return@withTransaction existing.filter { it.active }
        val inputs = inputs()
        val tasks = WeeklyTaskGenerator.generate(week, inputs.history, inputs.categories, inputs.subscriptions)
            .map { it.copy(createdAt = System.currentTimeMillis()) }
        if (tasks.isNotEmpty()) dao.insertAll(tasks)
        dao.getWeek(week.toEpochDay()).filter { it.active }
    }

    /** Заменить задание другим предложением. false — замены не нашлось, задание осталось. */
    suspend fun replace(task: WeeklyTask): Boolean = db.withTransaction {
        val week = dao.getWeek(task.weekStart)
        val inputs = inputs()
        val next = WeeklyTaskGenerator.replacement(
            LocalDate.ofEpochDay(task.weekStart),
            week.map { if (it.id == task.id) it.copy(active = false) else it },
            inputs.history, inputs.categories, inputs.subscriptions,
        ) ?: return@withTransaction false
        dao.update(task.copy(active = false))
        dao.insert(next.copy(createdAt = System.currentTimeMillis()))
        true
    }

    suspend fun remove(task: WeeklyTask) = dao.update(task.copy(active = false))

    suspend fun setChecked(task: WeeklyTask, checked: Boolean) = dao.update(task.copy(checked = checked))

    suspend fun add(task: WeeklyTask) = dao.insert(task.copy(custom = true, createdAt = System.currentTimeMillis()))

    private class Inputs(val history: List<TaskSpend>, val categories: List<TaskCategory>, val subscriptions: SubscriptionsSummary)

    private suspend fun inputs(): Inputs {
        val categories = db.categories().getAllWithSubs()
            .filter { !it.category.isArchived && it.category.name != DefaultCategories.UNACCOUNTED }
            .map { TaskCategory(it.category.id, it.category.name, it.category.isImpulsive) }
        val subs = RecurringSummary.totals(db.recurring().getAll())
        return Inputs(
            db.expenses().getAllWithCategory().toTaskSpends(zone),
            categories,
            SubscriptionsSummary(subs.count, subs.monthlyKopecks),
        )
    }
}
