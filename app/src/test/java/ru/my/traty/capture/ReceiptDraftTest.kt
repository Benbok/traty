package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Rule
import java.time.LocalDate

class ReceiptDraftTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()),
        CategoryWithSubs(Category(id = 2, name = "Дом и ремонт", emoji = "🔨"), emptyList()),
        CategoryWithSubs(Category(id = 3, name = DefaultCategories.OTHER, emoji = "📦"), emptyList()),
    )
    private val lines = listOf(ReceiptLine("Молоко", 8999, 1000, "шт"), ReceiptLine("Хлеб", 8000, 2000, "шт"))

    private fun build(parsed: ParsedReceipt, printed: Long?, rules: List<Rule> = emptyList(), places: List<String> = emptyList()) =
        ReceiptDraft.build(parsed, printed, cats, rules, places, today, "текст")

    @Test fun oneExpenseWithItems_printedTotalWins() {
        val r = build(ParsedReceipt("Пятёрочка", "2026-11-15", 16000, "продукты", lines), printed = 16999)
        val row = r.draft.rows.single()
        assertEquals(EntryOrigin.PHOTO, r.draft.origin)
        assertEquals("169.99", row.amountText)
        assertEquals("Продукты", row.category)
        assertEquals("Пятёрочка", row.place)
        assertEquals("2026-11-15", row.dateText)
        assertEquals(listOf("Молоко", "Хлеб"), row.items.map { it.name })
        assertEquals(listOf("89.99", "80"), row.items.map { it.priceText })
        assertEquals(listOf("1", "2"), row.items.map { it.qtyText })
        assertTrue(r.notices.single().contains("Взят итог из чека"))
    }

    @Test fun noTotals_sumOfItems() {
        val r = build(ParsedReceipt(null, null, null, null, lines), printed = null)
        assertEquals("169.99", r.draft.rows.single().amountText)
        assertEquals(DefaultCategories.OTHER, r.draft.rows.single().category)
        assertTrue(r.notices.single().contains("сложена из цен"))
    }

    @Test fun unknownCategory_isNotCreated_andFutureDateReplaced() {
        val r = build(ParsedReceipt("Леруа", "2027-01-01", 100, "Стройматериалы", emptyList()), printed = null)
        val row = r.draft.rows.single()
        assertEquals(DefaultCategories.OTHER, row.category)
        assertEquals(null, row.suggestedCategory)
        assertEquals(today.toString(), row.dateText)
        assertEquals(2, r.notices.size)
    }

    @Test fun ruleByStoreBeatsAi_andPlaceSnapsToKnown() {
        val r = build(
            ParsedReceipt("пятерочка", null, 100, "Продукты", lines),
            printed = null,
            rules = listOf(Rule(pattern = "пятёрочка", categoryId = 2)),
            places = listOf("Пятёрочка"),
        )
        val row = r.draft.rows.single()
        assertEquals("Пятёрочка", row.place)
        assertEquals("Дом и ремонт", row.category)
    }
}
