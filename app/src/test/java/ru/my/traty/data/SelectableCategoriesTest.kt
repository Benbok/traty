package ru.my.traty.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectableCategoriesTest {
    private fun cws(name: String) = CategoryWithSubs(Category(name = name, emoji = "x"), emptyList())

    @Test
    fun excludesUnaccountedInAnyCase() {
        for (variant in listOf("Неучтённые", "неучтённые", "НЕУЧТЁННЫЕ", " Неучтённые ")) {
            val result = listOf(cws("Продукты"), cws(variant), cws("Прочее")).selectable()
            assertEquals(variant, listOf("Продукты", "Прочее"), result.map { it.category.name })
        }
    }

    @Test
    fun keepsOthersInOrder() {
        val list = listOf(cws("Б"), cws("А"), cws("Прочее"))
        assertEquals(list, list.selectable())
    }
}
