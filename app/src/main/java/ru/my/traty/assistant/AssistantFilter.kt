package ru.my.traty.assistant

// Отбор трат для ответа помощника: категории, подкатегории и место из запроса.

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.search.SearchText
import java.util.Locale

private val RU: Locale = Locale.forLanguageTag("ru")

/** «кафе» → «кафе и рестораны», «ресторан» → «кафе и рестораны»: модель не всегда пишет название точно. */
internal fun closest(n: String, known: Collection<String>): String? {
    if (n.length < 4) return null
    known.filter { it.contains(n) }.minByOrNull { it.length }?.let { return it }
    val stem = n.take(maxOf(4, n.length - 2))
    return known.filter { k -> k.split(' ', ',', '-').any { w -> w.length >= 4 && (w.startsWith(stem) || n.startsWith(w.take(maxOf(4, w.length - 2)))) } }
        .minByOrNull { it.length }
}

/** Строчные буквы, ё → е: «Пятёрочка» и «пятерочка» — одно и то же. */
internal fun fold(s: String): String = s.trim().lowercase(RU).replace('ё', 'е')

/** Основа слова без окончания: «пятерочке» → «пятерочк», «шаурму» → «шаур». */
internal fun stem(word: String): String = if (word.length >= 5) word.take(maxOf(4, word.length - 2)) else word

/** Основы слов фразы: «в Яндекс Такси» → [«янде», «такс»]; предлоги не нужны. */
internal fun stems(text: String): List<String> =
    fold(text).split(Regex("""[^\p{L}\d]+""")).filter { it.length >= 2 && it !in PREPOSITIONS }.map(::stem)

private val PREPOSITIONS = setOf("в", "во", "на", "из", "у", "за", "по", "до", "от", "со")

/** Фраза есть в месте или заметке траты: все основы слов встречаются в тексте, а «Озон» находит и «Ozon». */
private fun ExpenseWithCategory.mentions(phrase: String): Boolean {
    val words = stems(phrase)
    if (words.isEmpty()) return false
    val text = fold(listOfNotNull(expense.place, expense.note).joinToString(" "))
    return words.all { it in text } || SearchText.sameBrand(phrase, listOf(expense.place, expense.note))
}

/** Фраза есть в названии позиции (товара) внутри покупки. */
internal fun ExpenseItem.mentionsItem(phrase: String): Boolean {
    val words = stems(phrase)
    if (words.isEmpty()) return false
    val text = fold(name)
    return words.all { it in text }
}

/**
 * Какие траты попадают в ответ.
 * [names] — категории и подкатегории (в нижнем регистре); [terms] — слова, которых нет среди категорий,
 * но они есть в местах или заметках трат («шаурма», «бензин»); [place] — место из запроса.
 * [itemTerms] — слова, найденные в именах позиций товаров внутри покупок.
 */
internal class Filter(
    val names: Set<String>,
    val unknownNames: List<String>,
    val place: String?,
    val terms: List<String> = emptyList(),
    /** Термины, которые нашлись в именах товарных позиций (а не в месте/заметке и не в категории). */
    val itemTerms: List<String> = emptyList(),
) {
    val isEmpty get() = names.isEmpty() && terms.isEmpty() && place == null && itemTerms.isEmpty()

    /** Фильтр по транзакции целиком: проходит, если хотя бы одна из позиций совпадает с [itemTerms]. */
    fun matches(r: ExpenseWithCategory, items: List<ExpenseItem> = emptyList()): Boolean {
        if (names.isNotEmpty() || terms.isNotEmpty() || itemTerms.isNotEmpty()) {
            val cat = r.categoryName.lowercase(RU)
            val sub = r.subcategoryName?.lowercase(RU)
            val byCategory = cat in names || sub in names
            val byTerm = terms.any { r.mentions(it) }
            val byItem = itemTerms.isNotEmpty() && items.any { item -> itemTerms.any { item.mentionsItem(it) } }
            if (!byCategory && !byTerm && !byItem) return false
        }
        if (place != null && !r.mentions(place)) return false
        return true
    }

    /**
     * Сумма, которую нужно засчитать за данную транзакцию при данном фильтре.
     * Если запрос — по конкретному товару ([itemTerms] непуст и совпадение только по ним) —
     * суммируем только прайсы совпавших позиций; иначе берём всю сумму транзакции.
     */
    fun amountOf(r: ExpenseWithCategory, items: List<ExpenseItem>): Long {
        // Нет товарного фильтра, или транзакция прошла по категории/термину — берём всю сумму
        if (itemTerms.isEmpty()) return r.expense.amountKopecks
        val cat = r.categoryName.lowercase(RU)
        val sub = r.subcategoryName?.lowercase(RU)
        if (cat in names || sub in names || terms.any { r.mentions(it) }) return r.expense.amountKopecks
        // Только товарный матч: суммируем priceKopecks совпавших позиций
        val matchedItems = items.filter { item -> itemTerms.any { item.mentionsItem(it) } }
        if (matchedItems.isEmpty()) return r.expense.amountKopecks
        val itemsSum = matchedItems.sumOf { it.priceKopecks ?: 0L }
        // Если у всех позиций нет цен — возвращаем всю сумму (лучше, чем 0)
        return if (itemsSum > 0) itemsSum else r.expense.amountKopecks
    }

    fun describe(): String? = listOfNotNull(
        names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
        (terms + itemTerms).takeIf { it.isNotEmpty() }?.joinToString(", ") { "«$it»" },
        place?.let { "«$it»" },
    ).joinToString(", ").ifEmpty { null }

    companion object {
        /** В этих вопросах незнакомое слово ищем в местах и заметках; в плане трат оно — новая подкатегория. */
        private val TEXT_SEARCH = setOf(
            Intents.TOTAL, Intents.TOP, Intents.BREAKDOWN, Intents.PLACES, Intents.COMPARE, Intents.AVERAGE,
            Intents.TREND, Intents.LIST, Intents.DELETE_EXPENSE, Intents.MOVE_EXPENSE,
        )

        fun of(
            q: AssistantQuery,
            rows: List<ExpenseWithCategory>,
            categories: List<CategoryWithSubs>,
            expenseItems: Map<Long, List<ExpenseItem>> = emptyMap(),
        ): Filter {
            val known = HashSet<String>()
            rows.forEach { r -> known += r.categoryName.lowercase(RU); r.subcategoryName?.let { known += it.lowercase(RU) } }
            categories.forEach { c -> known += c.category.name.lowercase(RU); c.subs.forEach { known += it.name.lowercase(RU) } }
            val asked = q.categories.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(RU) }
            var place = q.place?.trim()?.lowercase(RU)?.takeIf { it.isNotEmpty() }
            val found = HashSet<String>()
            // ИИ записал категорию местом («кофе»), а таких мест в записях нет — считаем по категории
            place?.let { p ->
                val asCategory = if (p in known) p else closest(p, known)
                if (asCategory != null && rows.none { it.mentions(p) }) { found += asCategory; place = null }
            }
            val terms = ArrayList<String>()
            val itemTerms = ArrayList<String>()
            val missing = ArrayList<String>()
            for (a in asked) {
                val n = a.lowercase(RU)
                val hit = if (n in known) n else closest(n, known)
                when {
                    hit != null -> found += hit
                    q.intent in TEXT_SEARCH && rows.any { it.mentions(a) } -> terms += n
                    // Товарный поиск: слова нет ни в категориях, ни в местах/заметках,
                    // но оно есть в именах позиций хотя бы одной покупки
                    q.intent in TEXT_SEARCH && expenseItems.values.any { items ->
                        items.any { item -> item.mentionsItem(a) }
                    } -> itemTerms += n
                    else -> missing += a
                }
            }
            return Filter(found, missing, place, terms, itemTerms)
        }
    }
}
