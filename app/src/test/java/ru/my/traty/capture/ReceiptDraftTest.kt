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

    private fun build(parsed: ParsedReceipt, rules: List<Rule> = emptyList(), places: List<String> = emptyList()) =
        ReceiptDraft.build(parsed, cats, rules, places, today)

    @Test fun oneExpenseWithItems_keepsPrintedTotal() {
        val r = build(ParsedReceipt("Пятёрочка", "2026-11-15", 16999, "продукты", lines))
        val row = r.draft.rows.single()
        assertEquals(EntryOrigin.PHOTO, r.draft.origin)
        assertEquals("169.99", row.amountText)
        assertEquals("Продукты", row.category)
        assertEquals("Пятёрочка", row.place)
        assertEquals("2026-11-15", row.dateText)
        assertEquals(listOf("Молоко", "Хлеб"), row.items.map { it.name })
        assertEquals(listOf("89.99", "80"), row.items.map { it.priceText })
        assertEquals(listOf("1", "2"), row.items.map { it.qtyText })
        assertTrue(r.notices.isEmpty())
    }

    @Test fun missingDateAndTotalStayBlank() {
        val r = build(ParsedReceipt(null, null, null, null, lines))
        assertEquals("", r.draft.rows.single().amountText)
        assertEquals("", r.draft.rows.single().dateText)
        assertEquals(DefaultCategories.OTHER, r.draft.rows.single().category)
        assertTrue(r.notices.any { it.contains("Итог чека не прочитался") })
    }

    @Test fun unknownCategoryIsNotCreated() {
        val r = build(ParsedReceipt("Леруа", "2026-11-15", 100, "Стройматериалы", emptyList()))
        val row = r.draft.rows.single()
        assertEquals(DefaultCategories.OTHER, row.category)
        assertEquals(null, row.suggestedCategory)
        assertEquals("2026-11-15", row.dateText)
        assertEquals(1, r.notices.size)
    }

    @Test fun imageDateInFutureStaysBlank() {
        val parsed = ParsedReceipt(null, "2096-11-23", 100, null, lines)
        val r = ReceiptDraft.build(parsed, cats, emptyList(), emptyList(), today)
        assertEquals("", r.draft.rows.single().dateText)
        assertTrue(r.notices.any { it.contains("будуще") })
    }

    @Test fun ruleByStoreBeatsAi_andPlaceSnapsToKnown() {
        val r = build(
            ParsedReceipt("пятерочка", null, 100, "Продукты", lines),
            rules = listOf(Rule(pattern = "пятёрочка", categoryId = 2)),
            places = listOf("Пятёрочка"),
        )
        val row = r.draft.rows.single()
        assertEquals("Пятёрочка", row.place)
        assertEquals("Дом и ремонт", row.category)
    }
}
