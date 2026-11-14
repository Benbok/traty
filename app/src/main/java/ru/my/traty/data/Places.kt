package ru.my.traty.data

/** Сумма трат одной категории в одном месте — как место записано в базе (без нормализации). */
data class PlaceTotal(
    val categoryId: Long,
    val place: String?,
    val total: Long,
    val count: Int,
)

/**
 * Траты в одном магазине. [key] — ключ сравнения («пятерочка»), пустой — трата без магазина.
 * [name] — как показать название: самое частое написание из базы.
 */
data class StoreTotal(
    val key: String,
    val name: String,
    val totalKopecks: Long,
    val count: Int,
    /** Категории, в которых были траты в этом магазине (по убыванию суммы). */
    val categoryIds: List<Long> = emptyList(),
) {
    val isNoPlace: Boolean get() = key.isEmpty()
}

/**
 * Названия магазинов: «магазин Пятёрочка», «пятерочка» и «"Пятерочка"» — это один магазин.
 * Сравнение — без регистра, кавычек, «ё» и родовых слов «магазин / супермаркет …» в начале.
 */
object Places {
    const val NO_PLACE = "Без магазина"

    private val quotes = Regex("[\"«»„“”'`]")
    private val spaces = Regex("""\s+""")

    // «магазин», «строительный магазин», «супермаркет» и т.п. в начале названия.
    // Прилагательное без слова «магазин» не трогаем: «Строительный двор» — это название.
    private val genericPrefix = Regex(
        """^(?:(?:строительн\S*|продуктов\S*|хозяйственн\S*|хоз)\s+)?(?:магазин|супермаркет|гипермаркет|минимаркет|маркет)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** Как сохранить место: без кавычек и «магазин», лишних пробелов, с заглавной буквы. null — места нет. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace(quotes, " ").replace(spaces, " ").trim().trim(',', '.', ';', ':', '-').trim()
        val stripped = s.replaceFirst(genericPrefix, "").trim()
        if (stripped.isNotEmpty()) s = stripped
        if (s.isEmpty()) return null
        return s.take(60).trim().replaceFirstChar { it.uppercaseChar() }
    }

    /** Ключ для сравнения двух написаний одного магазина. Пустая строка — места нет. */
    fun key(raw: String?): String =
        normalize(raw)?.lowercase()?.replace('ё', 'е')?.replace('-', ' ')?.replace(spaces, " ")?.trim().orEmpty()

    /**
     * Место для сохранения: если такой магазин уже встречался в [known] (в другом написании),
     * берём уже сохранённое написание — чтобы в базе был один «Пятёрочка», а не три варианта.
     */
    fun canonical(raw: String?, known: Collection<String>): String? {
        val norm = normalize(raw) ?: return null
        val k = key(norm)
        return known.firstOrNull { key(it) == k } ?: norm
    }

    /** Свести суммы по написаниям в суммы по магазинам. Сортировка: по сумме, «Без магазина» — последним. */
    fun group(rows: List<PlaceTotal>): List<StoreTotal> =
        rows.groupBy { key(it.place) }
            .map { (k, items) ->
                val name = if (k.isEmpty()) NO_PLACE else items
                    .groupBy { normalize(it.place)!! }
                    .maxWith(compareBy<Map.Entry<String, List<PlaceTotal>>>({ e -> e.value.sumOf { it.count } }, { e -> e.value.sumOf { it.total } }))
                    .key
                val cats = items.groupBy { it.categoryId }
                    .mapValues { (_, v) -> v.sumOf { it.total } }
                    .entries.sortedByDescending { it.value }.map { it.key }
                StoreTotal(k, name, items.sumOf { it.total }, items.sumOf { it.count }, cats)
            }
            .sortedWith(compareBy<StoreTotal> { it.isNoPlace }.thenByDescending { it.totalKopecks }.thenBy { it.name })

    /** Разбивка по магазинам внутри каждой категории. */
    fun byCategory(rows: List<PlaceTotal>): Map<Long, List<StoreTotal>> =
        rows.groupBy { it.categoryId }.mapValues { (_, v) -> group(v) }

    /** Есть ли что показать: хотя бы один названный магазин. */
    fun hasNamed(stores: List<StoreTotal>?): Boolean = stores?.any { !it.isNoPlace } == true
}

/** Траты из списка → суммы по (категория, место) для [Places.group]. */
fun List<ExpenseWithCategory>.placeTotals(): List<PlaceTotal> =
    groupBy { it.expense.categoryId to it.expense.place }
        .map { (k, v) -> PlaceTotal(k.first, k.second, v.sumOf { it.expense.amountKopecks }, v.size) }
