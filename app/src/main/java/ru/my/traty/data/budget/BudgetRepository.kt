package ru.my.traty.data.budget

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import ru.my.traty.data.AppDatabase

/** Запись и удаление планов трат. Какой план действует в месяце, решает [BudgetPlans]. */
class BudgetRepository(private val db: AppDatabase) {
    private val dao = db.budgets()

    fun observeAll(): Flow<List<Budget>> = dao.observeAll()

    /** Задать план [key] на месяц [month]; [repeats] — действует и дальше, пока его не сменят. */
    suspend fun set(key: PlanKey, month: Int, amountKopecks: Long, repeats: Boolean) = db.withTransaction {
        require(amountKopecks > 0) { "План должен быть больше нуля" }
        val row = BudgetPlans.set(dao.getAll(), key, month, amountKopecks, repeats)
        if (row.id == 0L) dao.insert(row) else dao.update(row)
    }

    /** Убрать план [key] начиная с месяца [month] (прошлые месяцы не меняются). */
    suspend fun removeFrom(key: PlanKey, month: Int) = db.withTransaction {
        val r = BudgetPlans.removeFrom(dao.getAll(), key, month)
        if (r.delete.isNotEmpty()) dao.deleteAll(r.delete)
        r.insert?.let { dao.insert(it) }
    }
}
