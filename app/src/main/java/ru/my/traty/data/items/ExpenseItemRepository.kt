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

    suspend fun getSalesForCategory(categoryId: Long, from: Long, to: Long): List<ItemSale> =
        dao.getSalesForCategory(categoryId, from, to)

    fun observeCategoryIdsWithItems(from: Long, to: Long): Flow<Set<Long>> =
        dao.observeCategoryIdsWithItems(from, to).map { it.toSet() }

    /**
     * Заменяет позиции траты целиком: порядок берётся из списка, пустые названия отбрасываются,
     * названия приводятся к одному виду, отрицательные цены и неположительные количества считаются не названными.
     */
    suspend fun replace(expenseId: Long, items: List<ExpenseItem>) = db.withTransaction {
        dao.deleteFor(expenseId)
        val clean = clean(items).mapIndexed { i, it -> it.copy(id = 0, expenseId = expenseId, position = i) }
        if (clean.isNotEmpty()) dao.insertAll(clean)
    }

    companion object {
        /**
         * Фасовку из названия («Коктейль молочный 200мл») переносим в своё поле, название остаётся без неё:
         * так «коктейль 200 мл» и «коктейль 450 мл» — один товар, а литры считаются по полю.
         */
        internal fun clean(items: List<ExpenseItem>): List<ExpenseItem> = items.mapNotNull { item ->
            val raw = ItemNames.normalize(item.name) ?: return@mapNotNull null
            val unit = item.unit?.trim()?.take(MAX_UNIT)?.ifEmpty { null }
            val quantity = item.quantityMilli?.takeIf { it > 0 }
            val given = item.packUnit?.let(PackSize::unitOf)?.let { u -> item.packMilli?.takeIf { it > 0 }?.let { Pack(it, u) } }
            val pack = given ?: PackSize.find(raw)
            val name = if (pack != null) ItemNames.normalize(PackSize.strip(raw)) ?: raw else raw
            item.copy(
                name = name,
                priceKopecks = item.priceKopecks?.takeIf { it >= 0 },
                quantityMilli = quantity,
                unit = unit,
                packMilli = pack?.milli,
                packUnit = pack?.unit,
            )
        }

        private const val MAX_UNIT = 10
    }
}
