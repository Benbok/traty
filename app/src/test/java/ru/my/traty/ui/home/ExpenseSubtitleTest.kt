package ru.my.traty.ui.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseSubtitleTest {
    private val longNote = "яблоки Гала 184,59 ₽, творог Простоквашино 149,98 ₽, яйцо куриное Ц1 114,90 ₽, масло 139 ₽"

    @Test
    fun purchaseWithItemsShowsCountInsteadOfTheWholeList() {
        assertEquals("10:23 · 4 товара", expenseSubtitle("10:23", longNote, itemCount = 4))
        assertEquals("10:23 · 5 товаров", expenseSubtitle("10:23", longNote, itemCount = 5))
        assertEquals("10:23 · 21 товар", expenseSubtitle("10:23", longNote, itemCount = 21))
        assertEquals("10:23 · 2 товара", expenseSubtitle("10:23", longNote, itemCount = 2))
    }

    @Test
    fun oneOrNoItemsKeepsTheNote() {
        assertEquals("10:23 · кофе с собой", expenseSubtitle("10:23", "кофе с собой", itemCount = 0))
        assertEquals("10:23 · кофе с собой", expenseSubtitle("10:23", "кофе с собой", itemCount = 1))
    }

    @Test
    fun onlyTheItemCountIsHighlighted() {
        val accent = Color(0xFF10B981)
        val text = expenseSubtitleText("10:23", longNote, itemCount = 5, accent = accent)
        assertEquals("10:23 · 5 товаров", text.text)
        val span = text.spanStyles.single()
        // Выделено ровно «5 товаров», время и разделитель без цвета акцента
        assertEquals("5 товаров", text.text.substring(span.start, span.end))
        assertEquals(accent, span.item.color)
    }

    @Test
    fun noteAndTimeHaveNoHighlight() {
        val text = expenseSubtitleText("10:23", "кофе с собой", itemCount = 1, accent = Color.Red)
        assertEquals("10:23 · кофе с собой", text.text)
        assertEquals(0, text.spanStyles.size)
    }

    @Test
    fun noNoteAndNoItemsIsJustTheTime() {
        assertEquals("10:23", expenseSubtitle("10:23", null, itemCount = 0))
        assertEquals("10:23", expenseSubtitle("10:23", "  ", itemCount = 0))
    }
}
