package ru.my.traty.data.debt

import androidx.room.withTransaction
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.items.ExpenseItem
import java.time.ZoneId

/** Удалённая трата-платёж: для «Отменить» в снэкбаре. [items] — позиции удалённых трат (товары), по id траты. */
data class RemovedPayment(
    val expenses: List<Expense>,
    val debt: Debt?,
    val parts: Map<Long, String>,
    val debtRemoved: Boolean,
    val items: Map<Long, List<ExpenseItem>> = emptyMap(),
)

/** Удаление траты с учётом покупки частями и «Отменить». */
class DebtPaymentRemoval(private val db: AppDatabase) {
    private val dao get() = db.debts()

    /**
     * Удаляет трату. Если это платёж по кредиту, вместе с ним уходит и строка процентов того же платежа.
     * Если у покупки не осталось платежей и её завели из этой траты, покупка удаляется тоже.
     */
    suspend fun delete(expense: Expense, zone: ZoneId = ZoneId.systemDefault()): RemovedPayment = db.withTransaction {
        val debt = dao.getDebtByExpenseId(expense.id)
        if (debt == null) {
            val items = itemsOf(listOf(expense))
            db.expenses().delete(expense)
            return@withTransaction RemovedPayment(listOf(expense), null, emptyMap(), debtRemoved = false, items = items)
        }
        val rows = dao.getPayments(debt.id)
        // Проценты кредита — отдельная трата того же платежа с тем же временем
        val sameMoment = rows.filter { it.expenseId == expense.id || (it.timestamp == expense.timestamp && it.part == PaymentPart.INTEREST) }
        val ids = sameMoment.map { it.expenseId }.toSet()
        val removed = db.expenses().getByIds(ids.toList()).map { it.expense }
            .filter { it.id == expense.id || it.source == ExpenseSource.DEBT }
        val parts = rows.filter { r -> removed.any { it.id == r.expenseId } }.associate { it.expenseId to it.part }
        val items = itemsOf(removed)
        removed.forEach { db.expenses().delete(it) }
        val left = rows.size - parts.size
        val orphan = DebtOrphans.isOrphan(debt, left, zone)
        if (orphan) dao.delete(debt)
        RemovedPayment(removed, debt, parts, debtRemoved = orphan, items = items)
    }

    /** Позиции удаляются вместе с тратой: запоминаем их до удаления, чтобы «Отменить» вернул и их. */
    private suspend fun itemsOf(expenses: List<Expense>): Map<Long, List<ExpenseItem>> =
        db.expenseItems().getForAll(expenses.map { it.id }).groupBy { it.expenseId }

    /** «Отменить»: траты возвращаются вместе со связью с покупкой (и с самой покупкой, если её удалили). */
    suspend fun restore(removed: RemovedPayment) = db.withTransaction {
        val debtId = removed.debt?.let { d -> if (removed.debtRemoved) dao.insert(d) else d.id }
        for (e in removed.expenses) {
            val id = db.expenses().insert(e)
            val part = removed.parts[e.id]
            if (debtId != null && part != null) dao.insertPayment(DebtPayment(debtId = debtId, expenseId = id, part = part))
            removed.items[e.id]?.takeIf { it.isNotEmpty() }?.let { items ->
                db.expenseItems().insertAll(items.map { it.copy(id = 0, expenseId = id) })
            }
        }
    }

    /**
     * Убирает покупки, у которых не осталось платежей, хотя их завели из траты. Такие остались,
     * если трату удалили до этой версии приложения. @return сколько убрано.
     */
    suspend fun dropOrphans(zone: ZoneId = ZoneId.systemDefault()): Int = db.withTransaction {
        var n = 0
        for (debt in dao.getActive()) {
            if (DebtOrphans.isOrphan(debt, dao.getPaymentExpenseIds(debt.id).size, zone)) {
                dao.delete(debt)
                n++
            }
        }
        n
    }
}
