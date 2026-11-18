package ru.my.traty.assistant

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.items.Pack
import ru.my.traty.data.items.PackSize
import ru.my.traty.data.search.SearchText
import java.time.LocalDate

/** Позиции с тем же названием товара, но без сведений о запрошенном вкусе. */
data class ItemSuggestion(
    val text: String,
    val headline: String?,
    val caption: String?,
    val expenseIds: List<Long>,
)

internal class ItemFlavor private constructor(
    val baseName: String,
    private val flavor: String,
    private val pack: Pack?,
) {
    fun confirmedBy(item: ExpenseItem): Boolean {
        val words = SearchText.queryWords(item.name)
        return !negation.containsMatchIn(fold(item.name)) &&
            flavor in flavorsIn(words) &&
            SearchText.matches(baseName, listOf(item.name)) &&
            samePack(item)
    }

    fun canSuggest(item: ExpenseItem): Boolean {
        // Для подсказки требуем совпадения названий в обе стороны: добавочные слова
        // могут описывать другой вкус, марку или вид товара.
        val name = PackSize.strip(item.name).replace(fatPercent, " ")
        return SearchText.matches(baseName, listOf(name)) &&
            SearchText.matches(name, listOf(baseName)) &&
            samePack(item)
    }

    private fun samePack(item: ExpenseItem): Boolean =
        pack == null || ItemMeasure.packOf(item.name, item.packMilli, item.packUnit)?.base() == pack.base()

    companion object {
        private val fatPercent = Regex("""\b\d+(?:[.,]\d+)?\s*%""")
        private val negation = Regex("""(?<!\p{L})без(?!\p{L})""")
        private val flavors = listOf("шоколад", "ванил", "клубнич", "банан", "карамел", "малин")
        private val tasteAdjectives = listOf("шоколадн", "ванильн", "клубничн", "бананов", "карамельн", "малинов")
        private val tasteWords = setOf("вкус", "вкусом", "вкуса")
        private val otherScope = Regex("""(?:^|\s)(?:в|во|на|из|у|по|для|или|и)(?:\s|$)""")
        private val allTimeTitle = Regex(
            """^(?:сколько\s+(?:я\s+)?потратил[аи]?\s+на\s+)?([\p{L}\s-]+?)\s+${QuestionDates.ALL_TIME_WORDS}[.!?]*$""",
        )

        fun parse(phrase: String): ItemFlavor? {
            if (negation.containsMatchIn(fold(phrase))) return null
            val words = SearchText.queryWords(PackSize.strip(phrase))
            if (words.none { word -> word in tasteWords || tasteAdjectives.any(word::startsWith) }) return null
            val flavor = flavorsIn(words).singleOrNull() ?: return null
            val base = words.filterNot { flavorOf(it) == flavor || it in tasteWords }
            if (base.isEmpty()) return null
            return ItemFlavor(base.joinToString(" "), flavor, PackSize.find(phrase))
        }

        fun allTimeSubject(question: String): String? {
            val subject = allTimeTitle.matchEntire(fold(question))?.groupValues?.get(1) ?: return null
            val item = parse(subject) ?: return null
            val words = SearchText.queryWords(item.baseName)
            // Для короткой фразы без ИИ оставляем только название, без других условий.
            return subject.takeIf { words.size in 1..4 && !otherScope.containsMatchIn(it) }
        }

        private fun flavorOf(word: String): String? {
            if (word.startsWith("клубник")) return "клубнич"
            return flavors.firstOrNull { word.startsWith(it) }
        }

        private fun flavorsIn(words: List<String>): Set<String> = words.mapNotNull(::flavorOf).toSet()
    }
}

internal object AssistantItemSuggestions {
    fun find(
        filter: Filter,
        spending: List<ExpenseWithCategory>,
        dates: ClosedRange<LocalDate>,
        ctx: AssistantContext,
    ): AssistantData? {
        if (filter.names.isNotEmpty() || filter.terms.isNotEmpty() || filter.unknownNames.isNotEmpty()) return null
        val item = filter.qualifiedItem ?: return null
        val term = filter.itemTerms.single()
        val rows = spending.filter { row ->
            day(row.expense.timestamp, ctx.zone) in dates && (filter.place?.let(row::mentions) ?: true)
        }
        fun positions(row: ExpenseWithCategory) = ctx.expenseItems[row.expense.id].orEmpty()
        if (rows.any { row -> positions(row).any(item::confirmedBy) }) return null

        val matchedRows = rows.mapNotNull { row ->
            AssistantItems.partForPositions(row, positions(row), item::canSuggest)?.let { row to it }
        }
        val range = span(dates.start, dates.endInclusive)
        val missing = "За $range не нашёл в чеках «$term»."
        if (matchedRows.isEmpty()) return AssistantData(facts = "", direct = missing)

        val subject = "«${item.baseName}» без указания вкуса"
        val picked = Picked(matchedRows.map { it.first }, matchedRows.associate { (row, part) -> row.expense.id to part }, ctx.zone)
        val summary = AssistantSpending.total(picked, unfiltered = false, range, subject, base = "")
        return AssistantData(
            facts = "",
            direct = "$missing Есть позиции «${item.baseName}» без указания вкуса. Показать расчёт по ним?",
            itemSuggestion = ItemSuggestion(summary.facts.trim(), summary.headline, summary.caption, summary.expenseIds),
        )
    }
}
