package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.ParsedItem
import ru.my.traty.data.items.ExpenseItem
import java.time.LocalDate

class DraftItemTest {
    @Test
    fun parsedItemBecomesTextFieldsAndBack() {
        val d = DraftItem.fromParsed(ParsedItem("молоко", price = 179.9, qty = 2.0, unit = "л"))
        assertEquals("179.9", d.priceText)
        assertEquals("2", d.qtyText)
        val e = d.toExpenseItem()
        assertEquals(17_990L, e.priceKopecks)
        assertEquals(2_000L, e.quantityMilli)
        assertEquals("л", e.unit)
    }

    @Test
    fun emptyFieldsMeanNotNamed() {
        val e = DraftItem("хлеб").toExpenseItem()
        assertNull(e.priceKopecks)
        assertNull(e.quantityMilli)
        assertNull(e.unit)
    }

    @Test
    fun receiptDiscountCheckLeavesThePrintedAmountIntact() {
        val item = DraftItem("Кофе", priceText = "200", qtyText = "2", unitPriceText = "100", discountText = "30")
        assertTrue(item.hasReceiptPriceMismatch())
        assertEquals(20_000L, item.toExpenseItem().priceKopecks)
        assertFalse(item.copy(priceText = "170").hasReceiptPriceMismatch())
    }

    @Test
    fun freeReceiptItemKeepsItsZeroPrice() {
        assertEquals(0L, DraftItem("Пакет", priceText = "0").toExpenseItem().priceKopecks)
    }

    @Test
    fun savedItemRoundTrip() {
        val d = DraftItem.fromSaved(ExpenseItem(name = "Яблоки", priceKopecks = 15_050, quantityMilli = 1_500, unit = "кг"))
        assertEquals("150.5", d.priceText)
        assertEquals("1.5", d.qtyText)
        assertEquals(1_500L, DraftItem("Яблоки", qtyText = "1,5").quantityMilli())
    }

    @Test
    fun packFromAiFieldOrFromName() {
        val d = DraftItem.fromParsed(ParsedItem("молочный коктейль", qty = 3.0, unit = "шт", pack = "200 мл"))
        assertEquals("200 мл", d.packText)
        assertEquals("3 шт по 200 мл", d.amountText())
        val e = d.toExpenseItem()
        assertEquals(200_000L, e.packMilli)
        assertEquals("мл", e.packUnit)
        // ИИ оставил фасовку в названии — в окне проверки она уже отдельно
        val fromName = DraftItem.fromParsed(ParsedItem("гуляш 500 г", price = 457.0))
        assertEquals("гуляш", fromName.name)
        assertEquals("500 г", fromName.packText)
        assertEquals("500 г", fromName.amountText())
    }

    @Test
    fun savedPackRoundTrip() {
        val d = DraftItem.fromSaved(ExpenseItem(name = "Сок", quantityMilli = 2_000, unit = "шт", packMilli = 1_000, packUnit = "л"))
        assertEquals("1 л", d.packText)
        assertEquals(1_000L, d.toExpenseItem().packMilli)
        assertNull(DraftItem("Сок", packText = "много").toExpenseItem().packMilli)
    }

    @Test
    fun entryRowCarriesItemsFromParsedEntry() {
        val entry = ParsedEntry(amount = 42000.0, category = "Прочее", items = listOf(ParsedItem("телефон", 40000.0), ParsedItem("чехол", 2000.0)))
        val row = EntryRow.fromParsed(entry, emptyList(), LocalDate.of(2026, 11, 16))
        assertEquals(listOf("телефон", "чехол"), row.items.map { it.name })
        assertEquals(listOf(4_000_000L, 200_000L), row.items.map { it.priceKopecks() })
    }
}
