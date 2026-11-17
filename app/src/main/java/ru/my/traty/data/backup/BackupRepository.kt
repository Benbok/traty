package ru.my.traty.data.backup

import androidx.room.withTransaction
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.PendingStatus

/**
 * Чтение всей базы и полная замена её содержимого — для полной резервной копии.
 * Замена идёт одной транзакцией: если что-то не записалось, база остаётся как была.
 */
class BackupRepository(private val db: AppDatabase) {

    /** Снимок всех таблиц; читается в одной транзакции, чтобы связи не разошлись. */
    suspend fun snapshot(): DbSnapshot = db.withTransaction {
        val cats = db.categories().getAllWithSubs()
        val pending = db.pendingPhrases().getByStatus(PendingStatus.WAITING) +
            db.pendingPhrases().getByStatus(PendingStatus.PARSED)
        val h = db.apartments()
        DbSnapshot(
            housing = ru.my.traty.data.apartments.ApartmentSnapshot(h.apartments(), h.meters(), h.registers(), h.readings(), h.submissions(), h.submissionReadings()),
            categories = cats.map { it.category },
            subcategories = cats.flatMap { it.subs },
            expenses = db.expenses().getAllWithCategory().map { it.expense },
            expenseItems = db.expenseItems().getAll(),
            incomes = db.incomes().getAll(),
            reconciliations = db.reconciliations().getAll(),
            templates = db.templates().getAll().map { it.template },
            rules = db.rules().getAll(),
            recurring = db.recurring().getAll(),
            budgets = db.budgets().getAll(),
            monthBudgets = db.monthBudgets().getAll(),
            weeklyTasks = db.weeklyTasks().getAll(),
            debts = db.debts().getAll(),
            debtPayments = db.debts().getAllPayments(),
            pendingPhrases = pending.sortedBy { it.createdAt },
        )
    }

    /**
     * Стирает все таблицы и записывает [s] с теми id, что в нём заданы. Всё в одной транзакции:
     * любая ошибка (нарушена связь, повтор уникального поля) откатывает и удаление, и запись.
     * [beforeCommit] — тестовый шов: выполняется внутри транзакции после записи.
     */
    suspend fun replaceAll(s: DbSnapshot, beforeCommit: suspend () -> Unit = {}) = db.withTransaction {
        val w = db.openHelper.writableDatabase
        // Сначала зависимые таблицы, потом те, на которые они ссылаются: внешние ключи проверяются сразу
        CLEAR_ORDER.forEach { w.execSQL("DELETE FROM `$it`") }

        val h = db.apartments()
        s.housing.apartments.forEach { h.insertApartment(it) }
        s.housing.meters.forEach { h.insertMeter(it) }
        s.housing.registers.forEach { h.insertRegister(it) }
        s.housing.readings.forEach { h.insertReading(it) }
        s.housing.submissions.forEach { h.insertSubmission(it) }
        s.housing.submissionReadings.forEach { h.insertSubmissionReading(it) }
        s.categories.forEach { db.categories().insert(it) }
        s.subcategories.forEach { db.categories().insertSub(it) }
        if (s.expenses.isNotEmpty()) db.expenses().insertAll(s.expenses)
        if (s.expenseItems.isNotEmpty()) db.expenseItems().insertAll(s.expenseItems)
        if (s.incomes.isNotEmpty()) db.incomes().insertAll(s.incomes)
        s.reconciliations.forEach { db.reconciliations().insert(it) }
        s.templates.forEach { db.templates().insert(it) }
        s.rules.forEach { db.rules().insert(it) }
        s.recurring.forEach { db.recurring().insert(it) }
        s.budgets.forEach { db.budgets().insert(it) }
        s.monthBudgets.forEach { db.monthBudgets().insert(it) }
        if (s.weeklyTasks.isNotEmpty()) db.weeklyTasks().insertAll(s.weeklyTasks)
        s.debts.forEach { db.debts().insert(it) }
        s.debtPayments.forEach { db.debts().insertPayment(it) }
        s.pendingPhrases.forEach { db.pendingPhrases().insert(it) }
        beforeCommit()
    }

    private companion object {
        val CLEAR_ORDER = listOf(
            "submission_readings", "reading_submissions", "meter_readings", "meter_registers", "meters", "apartments",
            "debt_payments", "expense_items", "budgets", "templates", "rules", "recurring",
            "reconciliations", "pending_phrases", "weekly_tasks", "month_budgets", "debts",
            "expenses", "incomes", "subcategories", "categories",
        )
    }
}
