package ru.my.traty.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableTest {
    @Test
    fun reconcileEntriesAreNotUserEditable() {
        val e = Expense(amountKopecks = 100, categoryId = 1, timestamp = 1)
        assertFalse(e.copy(source = ExpenseSource.RECONCILE).isUserEditable)
        assertTrue(e.copy(source = ExpenseSource.MANUAL).isUserEditable)
        assertTrue(e.copy(source = ExpenseSource.VOICE).isUserEditable)
        assertTrue(e.copy(source = ExpenseSource.TEXT).isUserEditable)
        assertTrue(e.copy(source = ExpenseSource.IMPORT).isUserEditable)

        val i = Income(amountKopecks = 100, timestamp = 1)
        assertFalse(i.copy(origin = IncomeOrigin.RECONCILE).isUserEditable)
        assertTrue(i.copy(origin = IncomeOrigin.MANUAL).isUserEditable)
        assertTrue(i.copy(origin = IncomeOrigin.TEXT).isUserEditable)
        assertTrue(i.copy(origin = IncomeOrigin.VOICE).isUserEditable)
        assertTrue(i.isUserEditable)
    }
}
