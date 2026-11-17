package ru.my.traty.capture

import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Places
import ru.my.traty.data.Rule
import ru.my.traty.data.items.ItemsCheck
import ru.my.traty.data.sameName
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Чек, готовый к проверке: одна трата с товарами. [notices] — что человеку стоит проверить глазами,
 * [receiptText] — распознанный текст чека (показывается на экране проверки по кнопке).
 */
data class ReceiptReview(
    val draft: EntryDraft,
    val notices: List<String>,
    val receiptText: String,
)

/**
 * Собирает черновик траты из разобранного чека. Сумму выбирает приложение:
 * итог, напечатанный в чеке ([printedTotal], найден без ИИ) → итог из ответа ИИ → сумма цен товаров.
 */
object ReceiptDraft {
    /** Дата из чека старше этого срока — скорее ошибка распознавания: ставится сегодняшняя. */
    private const val MAX_AGE_DAYS = 366L

    fun build(
        parsed: ParsedReceipt,
        printedTotal: Long?,
        categories: List<CategoryWithSubs>,
        rules: List<Rule>,
        knownPlaces: Collection<String>,
        today: LocalDate,
        receiptText: String,
    ): ReceiptReview {
        val notices = mutableListOf<String>()
        val day = dayOf(parsed.date, today, notices)
        val total = totalOf(parsed, printedTotal, notices)
        val category = parsed.category
            ?.let { name -> categories.firstOrNull { sameName(it.category.name, name) }?.category?.name }
            ?: DefaultCategories.OTHER
        val entry = ParsedEntry(type = "expense", amount = 0.0, category = category, place = parsed.place, date = day.toString())
        // Свои правила «магазин → категория» сильнее выбора ИИ
        val ruled = RuleMatcher.apply(listOf(entry), rules, categories).single()
        val items = parsed.lines.map(::draftItem)
        if (items.isEmpty()) notices += "Товары в чеке не распознаны: добавьте их вручную или переснимите чек"
        val row = EntryRow.fromParsed(ruled.entry, categories, today).copy(
            amountText = total?.let(::kopecksToText).orEmpty(),
            place = Places.canonical(parsed.place, knownPlaces)?.let { PlaceSnap.snap(it, knownPlaces) ?: it }.orEmpty(),
            items = items,
            suggestedCategory = null,
        )
        return ReceiptReview(EntryDraft(listOf(row), rawText = "", origin = EntryOrigin.PHOTO), notices, receiptText)
    }

    private fun totalOf(parsed: ParsedReceipt, printed: Long?, notices: MutableList<String>): Long? {
        val fromAi = parsed.totalKopecks
        if (printed != null) {
            if (fromAi != null && fromAi != printed) {
                notices += "ИИ прочитал итог как ${rub(fromAi)}, а в чеке напечатано ${rub(printed)}. Взят итог из чека"
            }
            return printed
        }
        if (fromAi != null) return fromAi
        val sum = ItemsCheck.totalOf(parsed.lines.map { it.priceKopecks })
        notices += if (sum != null) "Итог в чеке не найден: сумма сложена из цен товаров" else "Итог в чеке не найден: введите сумму"
        return sum
    }

    private fun dayOf(date: String?, today: LocalDate, notices: MutableList<String>): LocalDate {
        val parsed = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return today
        if (parsed.isAfter(today) || parsed.isBefore(today.minusDays(MAX_AGE_DAYS))) {
            notices += "Дата в чеке прочиталась как $parsed — поставлена сегодняшняя, поправьте, если нужно"
            return today
        }
        return parsed
    }

    private fun draftItem(l: ReceiptLine): DraftItem {
        val (name, pack) = DraftItem.splitPack(l.name, l.pack)
        return DraftItem(
            name = name,
            priceText = l.priceKopecks?.let(::kopecksToText).orEmpty(),
            qtyText = l.quantityMilli?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }.orEmpty(),
            unit = l.unit.orEmpty(),
            packText = pack?.text().orEmpty(),
        )
    }

    private fun rub(kopecks: Long) = "${kopecksToText(kopecks)} ₽"
}
