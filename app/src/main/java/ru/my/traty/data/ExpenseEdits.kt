package ru.my.traty.data

import androidx.room.withTransaction
import ru.my.traty.data.debt.DebtPaymentRemoval
import ru.my.traty.data.debt.RemovedPayment

/** Что нужно перенести: трата и куда. [fromCategoryId] и [fromSubcategoryId] — где лежала, для «Вернуть». */
data class ExpenseMove(
    val expenseId: Long,
    val toCategoryId: Long,
    val toSubcategoryId: Long?,
    val fromCategoryId: Long,
    val fromSubcategoryId: Long?,
)

/** Результат пакетного изменения трат: что именно сделано, чтобы это можно было вернуть. */
class ExpenseEditResult(val done: Int, internal val removed: List<RemovedPayment>, internal val moved: List<ExpenseMove>)

/**
 * Пакетное удаление и перенос трат (например, по просьбе из вкладки «ИИ»). Каждый пакет выполняется целиком
 * или не выполняется совсем и может быть возвращён. Записи расхождения сверки не трогаются.
 * Удаление учитывает покупки частями, как удаление из ленты ([DebtPaymentRemoval]).
 */
class ExpenseEdits(private val db: AppDatabase, private val removal: DebtPaymentRemoval) {
    suspend fun delete(ids: List<Long>): ExpenseEditResult = db.withTransaction {
        val removed = ArrayList<RemovedPayment>()
        for (id in ids) {
            val e = db.expenses().getByIds(listOf(id)).firstOrNull()?.expense ?: continue
            if (!e.isUserEditable) continue
            removed += removal.delete(e)
        }
        ExpenseEditResult(removed.size, removed, emptyList())
    }

    suspend fun move(moves: List<ExpenseMove>): ExpenseEditResult = db.withTransaction {
        val done = ArrayList<ExpenseMove>()
        for (m in moves) {
            val e = db.expenses().getByIds(listOf(m.expenseId)).firstOrNull()?.expense ?: continue
            if (!e.isUserEditable) continue
            db.expenses().update(e.copy(categoryId = m.toCategoryId, subcategoryId = m.toSubcategoryId))
            done += m
        }
        ExpenseEditResult(done.size, emptyList(), done)
    }

    /** «Вернуть»: удалённые траты возвращаются (вместе с покупками частями), перенесённые встают на прежнее место. */
    suspend fun undo(result: ExpenseEditResult) = db.withTransaction {
        result.removed.forEach { removal.restore(it) }
        for (m in result.moved) {
            val e = db.expenses().getByIds(listOf(m.expenseId)).firstOrNull()?.expense ?: continue
            db.expenses().update(e.copy(categoryId = m.fromCategoryId, subcategoryId = m.fromSubcategoryId))
        }
    }
}
