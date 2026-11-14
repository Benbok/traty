package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Rule
import ru.my.traty.data.Subcategory

class RuleMatcherTest {
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), listOf(Subcategory(id = 10, categoryId = 1, name = "Супермаркет"))),
        CategoryWithSubs(Category(id = 2, name = "Кафе и рестораны", emoji = "☕"), emptyList()),
    )
    private val rules = listOf(
        Rule(id = 1, pattern = "пятёрочка", categoryId = 1, subcategoryId = 10),
        Rule(id = 2, pattern = "кафе", categoryId = 2),
    )

    @Test
    fun matchesPlaceAndOverridesAiCategory() {
        val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 500.0, category = "Прочее", place = "Пятёрочка")), rules, cats).single()
        assertTrue(r.ruleMatched)
        assertEquals("Продукты", r.entry.category)
    }

    @Test
    fun yoAndYeAreTheSameInRules() {
        val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 500.0, category = "Прочее", place = "Пятерочка")), rules, cats).single()
        assertTrue(r.ruleMatched)
        assertEquals("Продукты", r.entry.category)
    }

    @Test
    fun matchesNote() {
        val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 300.0, category = "Прочее", note = "обед в кафе")), rules, cats).single()
        assertTrue(r.ruleMatched)
        assertEquals("Кафе и рестораны", r.entry.category)
    }

    @Test
    fun categoryNameAloneDoesNotMatch() {
        val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 300.0, category = "Кафе и рестораны")), rules, cats).single()
        assertFalse(r.ruleMatched)
    }

    @Test
    fun incomeIsNeverMatched() {
        val r = RuleMatcher.apply(listOf(ParsedEntry(type = "income", amount = 1.0, note = "кафе")), rules, cats).single()
        assertFalse(r.ruleMatched)
    }

    @Test
    fun ruleForMissingOrArchivedCategoryDoesNotCountAsMatch() {
        val orphanRules = listOf(Rule(id = 9, pattern = "магнит", categoryId = 777))
        val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 100.0, category = "Прочее", place = "Магнит")), orphanRules, cats).single()
        assertFalse(r.ruleMatched)
        assertEquals("Прочее", r.entry.category)
    }

    @Test
    fun subcategoryFromRuleWins() {
        val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 500.0, place = "пятёрочка", subcategory = "Рынок")), rules, cats).single()
        assertEquals("Супермаркет", r.entry.subcategory)
    }
}
