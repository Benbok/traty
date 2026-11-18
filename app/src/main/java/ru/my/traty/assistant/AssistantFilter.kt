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
internal fun ExpenseWithCategory.mentions(phrase: String): Boolean {
    val words = stems(phrase)
    if (words.isEmpty()) return false
    val text = fold(listOfNotNull(expense.place, expense.note).joinToString(" "))
    return words.all { it in text } || SearchText.sameBrand(phrase, listOf(expense.place, expense.note))
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
    val qualifiedItem: ItemFlavor? = null,
    private val useSearchWords: Boolean = false,
) {
    val isEmpty get() = names.isEmpty() && terms.isEmpty() && place == null && itemTerms.isEmpty()

    /** Трата прошла по категории, подкатегории или слову из места и заметки: засчитывается вся сумма. */
    private fun wholeMatch(r: ExpenseWithCategory): Boolean {
        val cat = r.categoryName.lowercase(RU)
        val sub = r.subcategoryName?.lowercase(RU)
        return cat in names || sub in names || terms.any { r.mentions(it) }
    }

    /** Какая часть траты относится к товарам из вопроса; null — вопрос не про товар или трата прошла целиком. */
    fun itemPart(r: ExpenseWithCategory, items: List<ExpenseItem>): ItemPart? {
        if (itemTerms.isEmpty() || wholeMatch(r)) return null
        return if (qualifiedItem != null) AssistantItems.partForPositions(r, items, qualifiedItem::confirmedBy)
        else AssistantItems.part(r, items, itemTerms, useSearchWords) { phrase ->
            if (useSearchWords) SearchText.matches(phrase, listOf(r.expense.note, r.expense.place))
            else r.mentions(phrase)
        }
    }

    /** Трата попадает в ответ: по категории, по слову в месте или заметке или по товару внутри покупки. */
    fun matches(r: ExpenseWithCategory, items: List<ExpenseItem> = emptyList()): Boolean {
        if (names.isNotEmpty() || terms.isNotEmpty() || itemTerms.isNotEmpty()) {
            if (!wholeMatch(r) && itemPart(r, items) == null) return false
        }
        if (place != null && !r.mentions(place)) return false
        return true
    }

    /**
     * Сколько засчитать за трату: при вопросе про товар — только цены совпавших позиций
     * ([AssistantItems.part]), иначе всю сумму.
     */
    fun amountOf(r: ExpenseWithCategory, items: List<ExpenseItem>): Long =
        itemPart(r, items)?.amount ?: r.expense.amountKopecks

    fun describe(): String? = listOfNotNull(
        names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
        (terms + itemTerms).takeIf { it.isNotEmpty() }?.joinToString(", ") { "«$it»" },
        place?.let { "«$it»" },
    ).joinToString(", ").ifEmpty { null }

    fun unresolvedAnswer(rows: List<ExpenseWithCategory>, ctx: AssistantContext): AssistantData? {
        if (unknownNames.isEmpty() || names.isNotEmpty() || terms.isNotEmpty() || itemTerms.isNotEmpty()) return null
        val known = rows.map { it.categoryName }.distinct().sorted().joinToString(", ")
        return AssistantData(facts = header(ctx) + "Категорий «${unknownNames.joinToString("», «")}» в записях нет. Есть категории: $known.")
    }

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
            val placeFromQuery = q.place?.trim()?.lowercase(RU)?.takeIf { it.isNotEmpty() }
            q.purchasedItem?.takeIf { q.intent == Intents.TOTAL && it.isNotBlank() }?.let { item ->
                return Filter(
                    emptySet(), emptyList(), placeFromQuery, itemTerms = listOf(item),
                    qualifiedItem = ItemFlavor.parse(item), useSearchWords = true,
                )
            }
            val known = HashSet<String>()
            rows.forEach { r -> known += r.categoryName.lowercase(RU); r.subcategoryName?.let { known += it.lowercase(RU) } }
            categories.forEach { c -> known += c.category.name.lowercase(RU); c.subs.forEach { known += it.name.lowercase(RU) } }
            val asked = q.categories.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(RU) }
            var place = placeFromQuery
            val found = HashSet<String>()
            // ИИ записал категорию местом («кофе»), а таких мест в записях нет — считаем по категории
            place?.let { p ->
                val asCategory = when {
                    p in known -> p
                    q.intent == Intents.TOTAL && ItemFlavor.parse(p) != null -> null
                    else -> closest(p, known)
                }
                if (asCategory != null && rows.none { it.mentions(p) }) { found += asCategory; place = null }
            }
            val terms = ArrayList<String>()
            val itemTerms = ArrayList<String>()
            // ИИ записал товар местом («шоколадки»), а такого места нет — ищем товар в покупках
            place?.let { p ->
                val isPlace = rows.any { r -> r.expense.place?.let { stems(p).all { w -> w in fold(it) } } == true }
                if (q.intent in TEXT_SEARCH && !isPlace &&
                    (hasItem(expenseItems, p) || (q.intent == Intents.TOTAL && ItemFlavor.parse(p) != null))
                ) {
                    itemTerms += p
                    place = null
                }
            }
            val missing = ArrayList<String>()
            for (a in asked) {
                val n = a.lowercase(RU)
                val hit = when {
                    n in known -> n
                    q.intent == Intents.TOTAL && ItemFlavor.parse(a) != null -> null
                    else -> closest(n, known)
                }
                when {
                    hit != null -> found += hit
                    // Товар из списков покупок проверяем раньше заметок: в заметке такой покупки тот же список,
                    // и по ней засчиталась бы вся сумма чека, а не цена товара
                    q.intent == Intents.TOTAL && ItemFlavor.parse(a) != null -> itemTerms += n
                    q.intent in TEXT_SEARCH && hasItem(expenseItems, a) -> itemTerms += n
                    q.intent in TEXT_SEARCH && rows.any { it.mentions(a) } -> terms += n
                    else -> missing += a
                }
            }
            val distinctItems = itemTerms.distinct()
            val qualifiedItem = distinctItems.singleOrNull()?.takeIf { q.intent == Intents.TOTAL }?.let(ItemFlavor::parse)
            return Filter(found, missing, place, terms, distinctItems, qualifiedItem)
        }

        private fun hasItem(expenseItems: Map<Long, List<ExpenseItem>>, phrase: String) =
            expenseItems.values.any { items -> items.any { it.mentionsItem(phrase) } }
    }
}
