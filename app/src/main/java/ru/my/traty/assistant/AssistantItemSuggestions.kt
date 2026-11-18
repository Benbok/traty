package ru.my.traty.assistant

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.items.ItemMeasure
import ru.my.traty.data.items.Pack
import ru.my.traty.data.items.PackSize
import ru.my.traty.data.search.SearchText
import java.time.LocalDate

/** Расчёт по варианту товара, который можно показать после выбора пользователя. */
data class ItemSuggestion(
    val text: String,
    val headline: String?,
    val caption: String?,
    val expenseIds: List<Long>,
    val itemName: String? = null,
)

internal class ItemFlavor private constructor(
    val baseName: String,
    private val flavor: String,
    private val pack: Pack?,
) {
    fun confirmedBy(item: ExpenseItem): Boolean {
        return hasRequestedFlavor(item) && SearchText.matches(baseName, listOf(item.name))
    }

    fun hasRequestedFlavor(item: ExpenseItem): Boolean =
        !negation.containsMatchIn(fold(item.name)) &&
            flavor in flavorsIn(SearchText.queryWords(item.name)) &&
            samePack(item)

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
        query: AssistantQuery,
        filter: Filter,
        spending: List<ExpenseWithCategory>,
        dates: ClosedRange<LocalDate>,
        ctx: AssistantContext,
    ): AssistantData? {
        if (query.intent != Intents.TOTAL || filter.names.isNotEmpty() || filter.terms.isNotEmpty()) return null
        val item = filter.qualifiedItem
        val quantity = !query.purchasedItem.isNullOrBlank()
        val term = when {
            filter.unknownNames.isEmpty() && (quantity || item != null) -> filter.itemTerms.singleOrNull()
            filter.itemTerms.isEmpty() -> filter.unknownNames.singleOrNull()
            else -> null
        } ?: return null
        val rows = spending.filter { row ->
            day(row.expense.timestamp, ctx.zone) in dates && (filter.place?.let(row::mentions) ?: true)
        }
        fun positions(row: ExpenseWithCategory) = ctx.expenseItems[row.expense.id].orEmpty()
        val range = span(dates.start, dates.endInclusive)
        val missing = "За $range не нашёл в чеках «$term»."
        val positionsInScope = rows.asSequence().flatMap { positions(it).asSequence() }
        fun offer(names: List<String>, eligible: Sequence<ExpenseItem>): AssistantData {
            fun calculate(selected: List<String>): ItemSuggestion {
                val normalized = selected.map(::fold).toSet()
                val matchedRows = rows.mapNotNull { row ->
                    AssistantItems.partForPositions(row, positions(row)) { fold(it.name) in normalized }?.let { row to it }
                }
                val subject = selected.joinToString(", ") { "«$it»" }
                return summarize(matchedRows, ctx, range, subject, quantityFirst = quantity)
            }

            val choices = names.map { name -> calculate(listOf(name)).copy(itemName = name) }
            val catalog = eligible.map { it.name.trim() }.filter { it.isNotEmpty() }.distinctBy(::fold).take(49).toList()
            val candidates = if (catalog.size <= 48) catalog else names
            return AssistantData(
                facts = "", direct = missing, itemSuggestions = choices, candidateNames = candidates, calculateItems = ::calculate,
            )
        }

        if (item == null) {
            if (quantity && rows.any { row -> filter.itemPart(row, positions(row)) != null }) return null
            val similar = similarNames(term, positionsInScope)
            return offer(similar, positionsInScope)
        }
        if (rows.any { row -> positions(row).any(item::confirmedBy) }) return null

        val matchedRows = rows.mapNotNull { row ->
            AssistantItems.partForPositions(row, positions(row), item::canSuggest)?.let { row to it }
        }
        if (matchedRows.isEmpty()) {
            val eligible = positionsInScope.filter(item::hasRequestedFlavor)
            return offer(similarNames(item.baseName, eligible), eligible)
        }

        val subject = "«${item.baseName}» без указания вкуса"
        return AssistantData(
            facts = "",
            direct = "$missing Есть позиции «${item.baseName}» без указания вкуса. Расчёт по этим позициям приведён отдельно.",
            itemSuggestions = listOf(summarize(matchedRows, ctx, range, subject, quantityFirst = quantity)),
        )
    }

    private fun summarize(
        matchedRows: List<Pair<ExpenseWithCategory, ItemPart>>,
        ctx: AssistantContext,
        range: String,
        subject: String,
        quantityFirst: Boolean,
    ): ItemSuggestion {
        val picked = Picked(matchedRows.map { it.first }, matchedRows.associate { (row, part) -> row.expense.id to part }, ctx.zone)
        val summary = AssistantSpending.total(picked, unfiltered = false, range, subject, base = "", quantityFirst = quantityFirst)
        return ItemSuggestion(summary.facts.trim(), summary.headline, summary.caption, summary.expenseIds)
    }

    private fun similarNames(term: String, items: Sequence<ExpenseItem>): List<String> {
        val words = SearchText.queryWords(PackSize.strip(term)).filter { it.length >= 4 }.distinct()
        if (words.isEmpty()) return emptyList()
        val candidates = items.map { it.name.trim() }.distinctBy(::fold).mapNotNull { name ->
            val title = SearchText.queryWords(name)
            val scores = words.map { word ->
                when {
                    SearchText.matches(word, listOf(name)) -> 2
                    word.length >= 5 && title.any { it.length >= 5 && it.startsWith(word.take(4)) } -> 1
                    else -> 0
                }
            }
            // Одно слово может отличаться, но основная часть запроса должна дать совпадение.
            name.takeIf { scores.count { it > 0 } >= maxOf(1, words.size - 1) }?.let { it to scores.sum() }
        }.toList()
        val best = candidates.maxOfOrNull { it.second } ?: return emptyList()
        return candidates.asSequence().filter { it.second == best }.map { it.first }.take(3).toList()
    }
}
