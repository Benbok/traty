package ru.my.traty.data.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtMatcherTest {
    private val fridge = Debt(id = 1, title = "Холодильник", place = "Ozon", categoryId = 1, createdAt = 10)
    private val sofa = Debt(id = 2, title = "Диван", place = "Hoff", categoryId = 1, createdAt = 20)
    private val kettle = Debt(id = 3, title = "Чайник", place = "Ozon", categoryId = 1, createdAt = 30)

    @Test
    fun findsByItemInAnyForm() {
        assertEquals(listOf(fridge), DebtMatcher.find(listOf(fridge, sofa), "за холодильник", "Ozon"))
        assertEquals(listOf(fridge), DebtMatcher.find(listOf(fridge, sofa), "доплата за холодильника", null))
        assertEquals(listOf(sofa), DebtMatcher.find(listOf(fridge, sofa), "диван", null))
    }

    @Test
    fun placeAloneWorksOnlyWhenUnambiguous() {
        assertEquals(listOf(fridge), DebtMatcher.find(listOf(fridge, sofa), null, "Ozon"))
        assertTrue(DebtMatcher.find(listOf(fridge, sofa, kettle), null, "Ozon").isEmpty())
    }

    @Test
    fun itemBeatsPlace() {
        assertEquals(kettle, DebtMatcher.find(listOf(fridge, kettle), "чайник", "Ozon").first())
    }

    @Test
    fun otherItemIsRegularExpense() {
        assertTrue(DebtMatcher.find(listOf(fridge, sofa), "наушники", "Ozon").isEmpty())
        assertTrue(DebtMatcher.find(listOf(fridge.copy(state = DebtState.CLOSED)), "холодильник", null).isEmpty())
    }

    @Test
    fun wordEndings() {
        assertTrue(DebtMatcher.sameWord("холодильник", "холодильнику"))
        assertTrue(DebtMatcher.sameWord("диван", "дивана"))
        assertFalse(DebtMatcher.sameWord("кухня", "кухонный"))
        assertFalse(DebtMatcher.sameWord("диван", "дизайн"))
    }
}
