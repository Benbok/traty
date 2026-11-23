package ru.my.traty.capture

import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Places
import ru.my.traty.data.Rule
import ru.my.traty.data.items.ItemNames
import ru.my.traty.data.sameName
import java.math.BigDecimal
import java.time.LocalDate

/** Одна покупка с товарами, готовая к подтверждению. */
data class ReceiptReview(
    val draft: EntryDraft,
    val notices: List<String>,
)

/** Собирает редактируемый черновик из распознанных строк. */
object ReceiptDraft {
    fun build(
        parsed: ParsedReceipt,
        categories: List<CategoryWithSubs>,
        rules: List<Rule>,
        knownPlaces: Collection<String>,
        today: LocalDate,
    ): ReceiptReview {
        val notices = mutableListOf<String>()
        val day = imageDay(parsed.date, today, notices)
        val total = parsed.totalKopecks
        if (total == null) notices += "Итог чека не прочитался. Введите его перед сохранением"
        val category = parsed.category
            ?.let { name -> categories.firstOrNull { sameName(it.category.name, name) }?.category?.name }
            ?: DefaultCategories.OTHER
        val entry = ParsedEntry(type = "expense", amount = 0.0, category = category, place = parsed.place, date = day?.toString())
        // Свои правила «магазин → категория» сильнее выбора ИИ
        val ruled = RuleMatcher.apply(listOf(entry), rules, categories).single()
        val items = parsed.lines.map(::draftItem)
        if (items.isEmpty()) notices += "Товары в чеке не распознаны: добавьте их вручную или переснимите чек"
        if (items.any { it.name.isBlank() || it.priceText.isBlank() }) {
            notices += "В некоторых строках отсутствует название или сумма. Проверьте эти позиции"
        }
        val row = EntryRow.fromParsed(ruled.entry, categories, today).copy(
            amountText = total?.let(::kopecksToText).orEmpty(),
            dateText = day?.toString().orEmpty(),
            place = Places.canonical(parsed.place, knownPlaces)?.let { PlaceSnap.snap(it, knownPlaces) ?: it }.orEmpty(),
            items = items,
            suggestedCategory = null,
        )
        return ReceiptReview(EntryDraft(listOf(row), rawText = "", origin = EntryOrigin.PHOTO), notices)
    }

    private fun imageDay(date: String?, today: LocalDate, notices: MutableList<String>): LocalDate? {
        val parsed = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (parsed == null) {
            notices += "Дата покупки не прочиталась. Укажите её перед сохранением"
        } else if (parsed.isAfter(today)) {
            notices += "Прочитана дата в будущем. Проверьте дату покупки"
            return null
        }
        return parsed
    }

    private fun draftItem(l: ReceiptLine): DraftItem {
        val (name, pack) = DraftItem.splitPack(l.name, l.pack)
        return DraftItem(
            name = ItemNames.normalize(name).orEmpty(),
            priceText = l.priceKopecks?.let(::kopecksToText).orEmpty(),
            qtyText = l.quantityMilli?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }.orEmpty(),
            unit = l.unit.orEmpty(),
            packText = pack?.text().orEmpty(),
            unitPriceText = l.unitPriceKopecks?.let(::kopecksToText).orEmpty(),
            discountText = l.discountKopecks?.let(::kopecksToText).orEmpty(),
        )
    }

}
