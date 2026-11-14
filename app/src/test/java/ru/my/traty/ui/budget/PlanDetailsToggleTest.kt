package ru.my.traty.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanDetailsToggleTest {
    @Test
    fun allCategoriesOnPlan() {
        assertEquals("Категория идёт по плану", planDetailsSummary(1, 0))
        assertEquals("Все 2 категории по плану", planDetailsSummary(2, 0))
        assertEquals("Все 5 категорий по плану", planDetailsSummary(5, 0))
        assertEquals("Все 21 категория по плану", planDetailsSummary(21, 0))
    }

    @Test
    fun troubledCategoriesAreCounted() {
        assertEquals("Категория требует внимания", planDetailsSummary(1, 1))
        assertEquals("1 из 5 категорий требует внимания", planDetailsSummary(5, 1))
        assertEquals("2 из 4 категорий требуют внимания", planDetailsSummary(4, 2))
        assertEquals("3 из 21 категории требуют внимания", planDetailsSummary(21, 3))
        assertEquals("2 из 11 категорий требуют внимания", planDetailsSummary(11, 2))
    }

    @Test
    fun emptyPlan() {
        assertEquals("Категорий в плане нет", planDetailsSummary(0, 0))
    }

    @Test
    fun actionLabelFollowsState() {
        assertEquals("Подробнее", planDetailsAction(expanded = false))
        assertEquals("Свернуть", planDetailsAction(expanded = true))
    }
}
