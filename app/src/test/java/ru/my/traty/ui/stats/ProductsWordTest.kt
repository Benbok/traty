package ru.my.traty.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductsWordTest {
    @Test
    fun pluralForms() {
        assertEquals(listOf("товар", "товара", "товаров", "товаров", "товар", "товаров"), listOf(1, 3, 5, 11, 21, 112).map(::productsWord))
    }
}
