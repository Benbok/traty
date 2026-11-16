package ru.my.traty.assistant

// Отбор трат для ответа помощника: категории, подкатегории и место из запроса.

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
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

/** Все основы фразы встречаются в тексте траты (месте или заметке). */
private fun ExpenseWithCategory.mentions(words: List<String>): Boolean {
    if (words.isEmpty()) return false
    val text = fold(listOfNotNull(expense.place, expense.note).joinToString(" "))
    return words.all { it in text }
}

/**
 * Какие траты попадают в ответ.
 * [names] — категории и подкатегории (в нижнем регистре); [terms] — слова, которых нет среди категорий,
 * но они есть в местах или заметках трат («шаурма», «бензин»); [place] — место из запроса.
 */
internal class Filter(
    val names: Set<String>,
    val unknownNames: List<String>,
    val place: String?,
    val terms: List<String> = emptyList(),
) {
    private val placeWords = place?.let(::stems).orEmpty()
    private val termWords = terms.map(::stems)

    val isEmpty get() = names.isEmpty() && terms.isEmpty() && place == null

    fun matches(r: ExpenseWithCategory): Boolean {
        if (names.isNotEmpty() || terms.isNotEmpty()) {
            val cat = r.categoryName.lowercase(RU)
            val sub = r.subcategoryName?.lowercase(RU)
            if (cat !in names && sub !in names && termWords.none { r.mentions(it) }) return false
        }
        if (place != null && !r.mentions(placeWords)) return false
        return true
    }

    fun describe(): String? = listOfNotNull(
        names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
        terms.takeIf { it.isNotEmpty() }?.joinToString(", ") { "«$it»" },
        place?.let { "«$it»" },
    ).joinToString(", ").ifEmpty { null }

    companion object {
        /** В этих вопросах незнакомое слово ищем в местах и заметках; в плане трат оно — новая подкатегория. */
        private val TEXT_SEARCH = setOf(
            Intents.TOTAL, Intents.TOP, Intents.BREAKDOWN, Intents.PLACES, Intents.COMPARE, Intents.AVERAGE,
            Intents.TREND, Intents.LIST, Intents.DELETE_EXPENSE, Intents.MOVE_EXPENSE,
        )

        fun of(q: AssistantQuery, rows: List<ExpenseWithCategory>, categories: List<CategoryWithSubs>): Filter {
            val known = HashSet<String>()
            rows.forEach { r -> known += r.categoryName.lowercase(RU); r.subcategoryName?.let { known += it.lowercase(RU) } }
            categories.forEach { c -> known += c.category.name.lowercase(RU); c.subs.forEach { known += it.name.lowercase(RU) } }
            val asked = q.categories.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(RU) }
            var place = q.place?.trim()?.lowercase(RU)?.takeIf { it.isNotEmpty() }
            val found = HashSet<String>()
            // ИИ записал категорию местом («кофе»), а таких мест в записях нет — считаем по категории
            place?.let { p ->
                val asCategory = if (p in known) p else closest(p, known)
                if (asCategory != null && rows.none { it.mentions(stems(p)) }) { found += asCategory; place = null }
            }
            val terms = ArrayList<String>()
            val missing = ArrayList<String>()
            for (a in asked) {
                val n = a.lowercase(RU)
                val hit = if (n in known) n else closest(n, known)
                when {
                    hit != null -> found += hit
                    q.intent in TEXT_SEARCH && stems(a).let { w -> rows.any { it.mentions(w) } } -> terms += n
                    else -> missing += a
                }
            }
            return Filter(found, missing, place, terms)
        }
    }
}
