package ru.my.traty.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Что ожидалось на счёте к моменту сверки: капитал прошлой сверки + пополнения - траты после её якоря. */
data class Expectation(
    val capitalKopecks: Long,
    val sinceMillis: Long,
    val incomesKopecks: Long,
    val expensesKopecks: Long,
) {
    val expectedKopecks: Long get() = capitalKopecks + incomesKopecks - expensesKopecks
}

/** Результат сверки; суммы положительные. */
sealed interface ReconcileOutcome {
    data class Opening(val capitalKopecks: Long) : ReconcileOutcome
    data object Matched : ReconcileOutcome
    data class Unaccounted(val amountKopecks: Long) : ReconcileOutcome
    data class Surplus(val amountKopecks: Long) : ReconcileOutcome
}

class ReconcileException(message: String) : Exception(message)

class ReconciliationRepository(
    private val db: AppDatabase,
    private val categories: CategoryRepository,
) {
    private val dao get() = db.reconciliations()

    suspend fun last(): Reconciliation? = dao.getLast()

    fun observeAll(): Flow<List<Reconciliation>> = dao.observeAll()

    /** Сверка перед последней (`null`, если последней нет или она единственная). */
    suspend fun previousOfLast(): Reconciliation? = dao.getAll().let { if (it.size >= 2) it[it.size - 2] else null }

    /** `null`, если сверок ещё не было. Якорь - `timestamp` самой записи, не порядок вставки. */
    suspend fun expectation(): Expectation? = db.withTransaction { expectationAfter(dao.getLast()) }

    private suspend fun expectationAfter(last: Reconciliation?): Expectation? {
        last ?: return null
        return Expectation(
            capitalKopecks = last.actualKopecks,
            sinceMillis = last.timestamp,
            incomesKopecks = db.incomes().sumAfter(last.timestamp),
            expensesKopecks = db.expenses().sumAfter(last.timestamp),
        )
    }

    suspend fun reconcile(
        actualKopecks: Long,
        nowMillis: Long,
        entryDate: LocalDate,
        zone: ZoneId,
    ): ReconcileOutcome = db.withTransaction { reconcileInTx(actualKopecks, nowMillis, entryDate, zone) }

    /**
     * Переделать последнюю сверку: удаляет её и записи расхождения, затем сверяет заново
     * относительно предыдущей, сохраняя дату записи расхождения.
     */
    suspend fun redoLast(
        actualKopecks: Long,
        nowMillis: Long,
        zone: ZoneId,
        fallbackEntryDate: LocalDate? = null,
    ): ReconcileOutcome =
        db.withTransaction {
            val last = dao.getLast() ?: throw ReconcileException("Сверок ещё не было")
            val removedTimestamp = last.expenseId?.let { id ->
                db.expenses().getAllWithCategory().firstOrNull { it.expense.id == id }?.expense?.timestamp
            } ?: last.incomeId?.let { id ->
                db.incomes().getAll().firstOrNull { it.id == id }?.timestamp
            }
            last.expenseId?.let { db.expenses().deleteById(it) }
            last.incomeId?.let { db.incomes().deleteById(it) }
            dao.delete(last)
            val date = removedTimestamp?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: fallbackEntryDate
                ?: Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            reconcileInTx(actualKopecks, nowMillis, date, zone)
        }

    private suspend fun reconcileInTx(
        actualKopecks: Long,
        nowMillis: Long,
        entryDate: LocalDate,
        zone: ZoneId,
    ): ReconcileOutcome {
        if (actualKopecks < 0) throw ReconcileException("Остаток не может быть отрицательным")
        val last = dao.getLast()
        if (last != null && nowMillis <= last.timestamp) throw ReconcileException("Сверка уже сделана")
        val expectation = expectationAfter(last)
        if (expectation == null) {
            dao.insert(
                Reconciliation(
                    timestamp = nowMillis,
                    actualKopecks = actualKopecks,
                    expectedKopecks = null,
                    differenceKopecks = 0,
                    expenseId = null,
                    incomeId = null,
                    isOpening = true,
                ),
            )
            return ReconcileOutcome.Opening(actualKopecks)
        }
        val expected = expectation.expectedKopecks
        val diff = actualKopecks - expected
        // Запись расхождения не позже якоря, иначе она попала бы в «ожидаемое» следующей сверки.
        val entryTime = minOf(entryDate.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), nowMillis)
        var expenseId: Long? = null
        var incomeId: Long? = null
        val outcome = when {
            diff == 0L -> ReconcileOutcome.Matched
            diff < 0 -> {
                expenseId = db.expenses().insert(
                    Expense(
                        amountKopecks = -diff,
                        categoryId = categories.ensureUnaccounted(),
                        timestamp = entryTime,
                        source = ExpenseSource.RECONCILE,
                    ),
                )
                ReconcileOutcome.Unaccounted(-diff)
            }
            else -> {
                incomeId = db.incomes().insert(
                    Income(
                        amountKopecks = diff,
                        source = DefaultCategories.OTHER,
                        timestamp = entryTime,
                        origin = IncomeOrigin.RECONCILE,
                    ),
                )
                ReconcileOutcome.Surplus(diff)
            }
        }
        dao.insert(
            Reconciliation(
                timestamp = nowMillis,
                actualKopecks = actualKopecks,
                expectedKopecks = expected,
                differenceKopecks = diff,
                expenseId = expenseId,
                incomeId = incomeId,
                isOpening = false,
            ),
        )
        return outcome
    }
}
