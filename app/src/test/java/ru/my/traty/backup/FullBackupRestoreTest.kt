package ru.my.traty.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** Чистая логика восстановления: связи по названиям и ключам → новые id. */
class FullBackupRestoreTest {
    private val base = FullBackup(
        formatVersion = 1, schemaVersion = 15, createdAt = 1,
        categories = listOf(BackupCategory("Еда", "🍔"), BackupCategory("Прочее", "📦")),
        subcategories = listOf(BackupSubcategory("Еда", "Кафе"), BackupSubcategory("Прочее", "Кафе")),
        expenses = listOf(
            BackupExpense(key = 10, amountKopecks = 100, category = "Прочее", subcategory = "Кафе", timestamp = 1, source = "voice"),
            BackupExpense(key = 20, amountKopecks = 200, category = "Еда", subcategory = "Кафе", timestamp = 2, source = "voice"),
        ),
        debts = listOf(BackupDebt(key = 5, title = "Диван", category = "Удалённая", kind = "partial", direction = "i_owe", periodUnit = "month", state = "active")),
        debtPayments = listOf(BackupDebtPayment(debt = 5, expense = 20, part = "principal")),
    )

    private fun assertDamaged(b: FullBackup) {
        try {
            FullBackupRestore.toSnapshot(b)
            fail("Ожидалась ошибка")
        } catch (e: FullBackupException) {
            assert(e.message.orEmpty().startsWith("Файл копии повреждён"))
        }
    }

    @Test
    fun subcategoryIsResolvedInsideItsCategory() {
        val s = FullBackupRestore.toSnapshot(base)
        val subOf = s.subcategories.associate { it.id to (s.categories.single { c -> c.id == it.categoryId }.name to it.name) }
        assertEquals("Прочее" to "Кафе", subOf[s.expenses[0].subcategoryId])
        assertEquals("Еда" to "Кафе", subOf[s.expenses[1].subcategoryId])
    }

    @Test
    fun localKeysBecomeNewIds() {
        val s = FullBackupRestore.toSnapshot(base)
        assertEquals(listOf(1L, 2L), s.expenses.map { it.id })
        assertEquals(s.expenses[1].id, s.debtPayments.single().expenseId)
        assertEquals(s.debts.single().id, s.debtPayments.single().debtId)
    }

    @Test
    fun debtFromDeletedCategoryGoesToOther() {
        val s = FullBackupRestore.toSnapshot(base)
        assertEquals(s.categories.single { it.name == "Прочее" }.id, s.debts.single().categoryId)
        assertNull(s.debts.single().subcategoryId)
    }

    @Test
    fun brokenLinksAreDamage() {
        assertDamaged(base.copy(expenses = base.expenses + base.expenses[0].copy(key = 30, category = "Нет")))
        assertDamaged(base.copy(expenses = base.expenses + base.expenses[0].copy(key = 30, subcategory = "Нет")))
        assertDamaged(base.copy(expenses = base.expenses + base.expenses[0]))
        assertDamaged(base.copy(debtPayments = listOf(BackupDebtPayment(debt = 5, expense = 99, part = "principal"))))
        assertDamaged(base.copy(expenseItems = listOf(BackupExpenseItem(expense = 99, name = "Хлеб"))))
        assertDamaged(base.copy(reconciliations = listOf(BackupReconciliation(1, 0, null, 0, income = 7))))
        assertDamaged(base.copy(categories = base.categories + BackupCategory("Еда", "🍕")))
    }
}
