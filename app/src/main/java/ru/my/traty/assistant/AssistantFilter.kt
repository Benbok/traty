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

internal class Filter(val names: Set<String>, val unknownNames: List<String>, val place: String?) {
    val isEmpty get() = names.isEmpty() && place == null

    fun matches(r: ExpenseWithCategory): Boolean {
        if (names.isNotEmpty()) {
            val cat = r.categoryName.lowercase(RU)
            val sub = r.subcategoryName?.lowercase(RU)
            if (cat !in names && sub !in names) return false
        }
        if (place != null) {
            val p = r.expense.place?.lowercase(RU).orEmpty()
            val n = r.expense.note?.lowercase(RU).orEmpty()
            if (place !in p && place !in n) return false
        }
        return true
    }

    fun describe(): String? = listOfNotNull(
        names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
        place?.let { "«$it»" },
    ).joinToString(", ").ifEmpty { null }

    companion object {
        fun of(q: AssistantQuery, rows: List<ExpenseWithCategory>, categories: List<CategoryWithSubs>): Filter {
            val known = HashSet<String>()
            rows.forEach { r -> known += r.categoryName.lowercase(RU); r.subcategoryName?.let { known += it.lowercase(RU) } }
            categories.forEach { c -> known += c.category.name.lowercase(RU); c.subs.forEach { known += it.name.lowercase(RU) } }
            val asked = q.categories.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(RU) }
            val place = q.place?.trim()?.lowercase(RU)?.takeIf { it.isNotEmpty() }
            val found = HashSet<String>()
            val missing = ArrayList<String>()
            for (a in asked) {
                val n = a.lowercase(RU)
                val hit = if (n in known) n else closest(n, known)
                if (hit != null) found += hit else missing += a
            }
            return Filter(found, missing, place)
        }
    }
}
