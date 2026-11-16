package ru.my.traty.data.items

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.my.traty.data.AppDatabase

/** Позиции трат: товары внутри одной покупки. Все записи идут через этот репозиторий. */
class ExpenseItemRepository(private val db: AppDatabase) {
    private val dao get() = db.expenseItems()

    fun observeFor(expenseId: Long): Flow<List<ExpenseItem>> = dao.observeFor(expenseId)

    suspend fun getFor(expenseId: Long): List<ExpenseItem> = dao.getFor(expenseId)

    /** Позиции нескольких трат сразу, по тратам: для «Изменить» после сохранения и для «Отменить». */
    suspend fun getForAll(expenseIds: List<Long>): Map<Long, List<ExpenseItem>> =
        if (expenseIds.isEmpty()) emptyMap() else dao.getForAll(expenseIds).groupBy { it.expenseId }

    fun observeExpenseIdsWithItems(): Flow<List<Long>> = dao.observeExpenseIdsWithItems()

    /** Сколько товаров у каждой покупки по номеру траты; трат без товаров в карте нет. */
    fun observeCounts(): Flow<Map<Long, Int>> = dao.observeCounts().map { list -> list.associate { it.expenseId to it.count } }

    fun observeSales(from: Long, to: Long): Flow<List<ItemSale>> = dao.observeSales(from, to)

    suspend fun getSales(from: Long, to: Long): List<ItemSale> = dao.getSales(from, to)

    /**
     * Заменяет позиции траты целиком: порядок берётся из списка, пустые названия отбрасываются,
     * названия приводятся к одному виду, неположительные цены и количества считаются не названными.
     */
    suspend fun replace(expenseId: Long, items: List<ExpenseItem>) = db.withTransaction {
        dao.deleteFor(expenseId)
        val clean = clean(items).mapIndexed { i, it -> it.copy(id = 0, expenseId = expenseId, position = i) }
        if (clean.isNotEmpty()) dao.insertAll(clean)
    }

    companion object {
        internal fun clean(items: List<ExpenseItem>): List<ExpenseItem> = items.mapNotNull { item ->
            val name = ItemNames.normalize(item.name) ?: return@mapNotNull null
            item.copy(
                name = name,
                priceKopecks = item.priceKopecks?.takeIf { it > 0 },
                quantityMilli = item.quantityMilli?.takeIf { it > 0 },
                unit = item.unit?.trim()?.take(MAX_UNIT)?.ifEmpty { null },
            )
        }

        private const val MAX_UNIT = 10
    }
}
