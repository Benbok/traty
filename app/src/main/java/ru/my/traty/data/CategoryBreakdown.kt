package ru.my.traty.data

/** Как раскрыть категорию в статистике: по подкатегориям, по магазинам или просто списком трат. */
enum class BreakdownMode(val title: String) {
    SUBCATEGORIES("Подкатегории"),
    STORES("Магазины"),
    EXPENSES("Траты"),
}

/**
 * Одна строка раскрытой категории: подкатегория или магазин.
 * [isUnassigned] — траты без подкатегории (без магазина), такая группа всегда последняя.
 * [expenses] — траты группы, новые сверху.
 */
data class BreakdownGroup(
    val key: String,
    val name: String,
    val total: Long,
    val count: Int,
    val isUnassigned: Boolean,
    val expenses: List<ExpenseWithCategory>,
)

/** Из чего состоит одна категория за период. */
data class CategoryBreakdown(
    val categoryId: Long,
    val total: Long,
    val bySubcategory: List<BreakdownGroup>,
    val byStore: List<BreakdownGroup>,
    /** Все траты категории, новые сверху. */
    val expenses: List<ExpenseWithCategory>,
) {
    val hasSubcategories: Boolean get() = bySubcategory.any { !it.isUnassigned }
    val hasStores: Boolean get() = byStore.any { !it.isUnassigned }

    /** Какие способы раскрытия имеют смысл: «Траты» есть всегда. */
    val modes: List<BreakdownMode>
        get() = buildList {
            if (hasSubcategories) add(BreakdownMode.SUBCATEGORIES)
            if (hasStores) add(BreakdownMode.STORES)
            add(BreakdownMode.EXPENSES)
        }

    /** С чего начинать: подкатегории («Авто» → бензин, мойка), иначе магазины, иначе список трат. */
    val defaultMode: BreakdownMode get() = modes.first()

    fun groups(mode: BreakdownMode): List<BreakdownGroup> = when (mode) {
        BreakdownMode.SUBCATEGORIES -> bySubcategory
        BreakdownMode.STORES -> byStore
        BreakdownMode.EXPENSES -> emptyList()
    }
}

/**
 * Раскладка трат по категориям для статистики: категория → подкатегории или магазины → траты.
 * Магазины сравниваются так же, как в [Places] («магазин Пятёрочка» и «пятерочка» — один магазин).
 */
object CategoryBreakdowns {
    const val NO_SUBCATEGORY = "Без подкатегории"

    fun build(expenses: List<ExpenseWithCategory>): Map<Long, CategoryBreakdown> =
        expenses.groupBy { it.expense.categoryId }.mapValues { (id, list) ->
            val sorted = list.sortedWith(newestFirst)
            CategoryBreakdown(
                categoryId = id,
                total = sorted.sumOf { it.expense.amountKopecks },
                bySubcategory = bySubcategory(sorted),
                byStore = byStore(sorted),
                expenses = sorted,
            )
        }

    private val newestFirst =
        compareByDescending<ExpenseWithCategory> { it.expense.timestamp }.thenByDescending { it.expense.id }

    private val groupOrder =
        compareBy<BreakdownGroup> { it.isUnassigned }.thenByDescending { it.total }.thenBy { it.name }

    private fun bySubcategory(list: List<ExpenseWithCategory>): List<BreakdownGroup> =
        list.groupBy { it.expense.subcategoryId?.takeIf { _ -> !it.subcategoryName.isNullOrBlank() } }
            .map { (subId, items) ->
                BreakdownGroup(
                    key = subId?.toString().orEmpty(),
                    name = if (subId == null) NO_SUBCATEGORY else items.first().subcategoryName!!,
                    total = items.sumOf { it.expense.amountKopecks },
                    count = items.size,
                    isUnassigned = subId == null,
                    expenses = items,
                )
            }
            .sortedWith(groupOrder)

    private fun byStore(list: List<ExpenseWithCategory>): List<BreakdownGroup> =
        list.groupBy { Places.key(it.expense.place) }
            .map { (k, items) ->
                // Название — самое частое написание, как в Places.group
                val name = Places.group(items.placeTotals()).first().name
                BreakdownGroup(
                    key = k,
                    name = name,
                    total = items.sumOf { it.expense.amountKopecks },
                    count = items.size,
                    isUnassigned = k.isEmpty(),
                    expenses = items,
                )
            }
            .sortedWith(groupOrder)

    /**
     * Подпись траты в раскрытом списке: что купили. Заметка, если есть; иначе подкатегория, магазин
     * или исходная фраза. Пустая строка — описать нечем.
     */
    fun title(e: ExpenseWithCategory): String =
        listOf(e.expense.note, e.subcategoryName, Places.normalize(e.expense.place), e.expense.rawText)
            .firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}
