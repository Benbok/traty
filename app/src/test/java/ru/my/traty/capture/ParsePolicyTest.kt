package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Rule

class ParsePolicyTest {
    private val rule = Rule(id = 1, pattern = "x", categoryId = 1)

    private fun expense(matched: Boolean, amount: Double = 100.0, type: String = "expense") =
        RuledEntry(ParsedEntry(type = type, amount = amount), if (matched) rule else null)

    @Test
    fun autoSavesWhenAllExpensesMatched() {
        assertEquals(EntryDecision.AUTO_SAVE, ParsePolicy.decide(listOf(expense(true), expense(true))))
    }

    @Test
    fun confirmsWhenAnyIncome() {
        assertEquals(EntryDecision.CONFIRM, ParsePolicy.decide(listOf(expense(true), expense(true, type = "income"))))
    }

    @Test
    fun confirmsWhenAnyWithoutRule() {
        assertEquals(EntryDecision.CONFIRM, ParsePolicy.decide(listOf(expense(true), expense(false))))
    }

    @Test
    fun confirmsWhenEmpty() {
        assertEquals(EntryDecision.CONFIRM, ParsePolicy.decide(emptyList()))
    }

    @Test
    fun confirmsWhenUnknownType() {
        assertEquals(EntryDecision.CONFIRM, ParsePolicy.decide(listOf(expense(true, type = "goal_deposit"))))
    }

    @Test
    fun confirmsWhenZeroAmount() {
        assertEquals(EntryDecision.CONFIRM, ParsePolicy.decide(listOf(expense(true, amount = 0.0))))
    }
}
