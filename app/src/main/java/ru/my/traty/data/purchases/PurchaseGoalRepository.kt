package ru.my.traty.data.purchases

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import java.time.Clock
import java.time.LocalDate

class PurchaseGoalRepository(
    private val db: AppDatabase,
    private val categories: CategoryRepository,
    private val clock: Clock? = null,
) {
    private val dao get() = db.purchaseGoals()

    fun observe(): Flow<List<PurchaseGoalStatus>> = dao.observe()

    suspend fun listActive(): List<PurchaseGoalStatus> = dao.listActive()

    suspend fun create(title: String, targetKopecks: Long, due: LocalDate): Long {
        val name = title.trim()
        require(name.isNotEmpty()) { "Введите название покупки" }
        require(targetKopecks > 0) { "Введите сумму покупки больше нуля" }
        requireCurrentOrFuture(due)
        return dao.insertGoal(PurchaseGoal(title = name, targetKopecks = targetKopecks, dueEpochDay = due.toEpochDay()))
    }

    suspend fun reschedule(goalId: Long, due: LocalDate) = db.withTransaction {
        requireCurrentOrFuture(due)
        val goal = active(goalId).goal
        dao.updateGoal(goal.copy(dueEpochDay = due.toEpochDay()))
    }

    suspend fun contribute(goalId: Long, amountKopecks: Long) = db.withTransaction {
        val status = active(goalId)
        validateContribution(amountKopecks, status.savedKopecks)
        val expenseId = addExpense(amountKopecks, "Отложено на «${status.goal.title}»", ExpenseSource.PURCHASE_CONTRIBUTION)
        dao.insertContribution(PurchaseContribution(expenseId, goalId))
    }

    suspend fun editContribution(expenseId: Long, amountKopecks: Long) = db.withTransaction {
        val (status, expense) = activeContribution(expenseId)
        validateContribution(amountKopecks, status.savedKopecks - expense.amountKopecks)
        db.expenses().update(expense.copy(amountKopecks = amountKopecks))
    }

    suspend fun deleteContribution(expenseId: Long) = db.withTransaction {
        val (_, expense) = activeContribution(expenseId)
        db.expenses().delete(expense)
    }

    suspend fun finish(goalId: Long, priceKopecks: Long) = db.withTransaction {
        require(priceKopecks > 0) { "Введите фактическую цену больше нуля" }
        val status = active(goalId)
        val goal = status.goal
        val difference = priceKopecks - status.savedKopecks
        when {
            difference > 0 -> addExpense(difference, "Доплата за «${goal.title}»", ExpenseSource.PURCHASE_SETTLEMENT)
            difference < 0 -> refund(goal, -difference)
        }
        dao.updateGoal(goal.copy(actualPriceKopecks = priceKopecks))
    }

    suspend fun cancel(goalId: Long) = db.withTransaction {
        val status = dao.status(goalId) ?: return@withTransaction
        if (status.goal.hidden) return@withTransaction
        check(status.isActive) { "Покупка уже завершена" }
        if (status.savedKopecks == 0L) {
            dao.deleteGoal(goalId)
        } else {
            refund(status.goal, status.savedKopecks, IncomeOrigin.PURCHASE_CANCELLATION_REFUND)
            dao.updateGoal(status.goal.copy(hidden = true))
        }
    }

    suspend fun hideCompleted(goalId: Long) = db.withTransaction {
        val goal = dao.status(goalId)?.goal ?: return@withTransaction
        if (goal.hidden) return@withTransaction
        check(goal.actualPriceKopecks != null) { "Покупка ещё не завершена" }
        dao.updateGoal(goal.copy(hidden = true))
    }

    private suspend fun refund(goal: PurchaseGoal, amountKopecks: Long, origin: String = IncomeOrigin.PURCHASE_REFUND) {
        db.incomes().insert(
            Income(
                amountKopecks = amountKopecks,
                source = "Возврат из накоплений",
                note = goal.title,
                timestamp = currentClock().millis(),
                origin = origin,
            ),
        )
    }

    private suspend fun active(goalId: Long): PurchaseGoalStatus {
        val status = checkNotNull(dao.status(goalId)) { "Цель больше недоступна" }
        check(status.isActive) { "Цель больше не активна" }
        return status
    }

    private suspend fun activeContribution(expenseId: Long): Pair<PurchaseGoalStatus, Expense> {
        val link = checkNotNull(dao.findContribution(expenseId)) { "Взнос больше недоступен" }
        val status = active(link.goalId)
        val expense = checkNotNull(status.contributions.firstOrNull { it.id == expenseId }) { "Взнос больше недоступен" }
        return status to expense
    }

    private fun validateContribution(amount: Long, savedWithoutContribution: Long) {
        require(amount > 0) { "Введите сумму взноса больше нуля" }
        require(amount <= Long.MAX_VALUE - savedWithoutContribution) { "Сумма взноса слишком большая" }
    }

    private fun requireCurrentOrFuture(due: LocalDate) {
        require(due >= LocalDate.now(currentClock())) { "Выберите сегодняшнюю или будущую дату" }
    }

    private fun currentClock(): Clock = clock ?: Clock.systemDefaultZone()

    private suspend fun addExpense(amount: Long, label: String, source: String): Long {
        val category = categories.resolveForSave("Накопления", "🎯", null)
        return db.expenses().insert(
            Expense(amountKopecks = amount, categoryId = category.categoryId, place = label, timestamp = currentClock().millis(), source = source),
        )
    }
}
