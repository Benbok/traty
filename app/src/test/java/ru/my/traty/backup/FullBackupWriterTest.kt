package ru.my.traty.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.AppSettings
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.Income
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.Reconciliation
import ru.my.traty.data.Rule
import ru.my.traty.data.Subcategory
import ru.my.traty.data.backup.DbSnapshot
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtPayment
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.portable
import ru.my.traty.data.weekly.WeeklyTask

/** Круговой обмен без базы: снимок → файл → снимок → тот же файл. Id старого телефона нарочно не по порядку. */
class FullBackupWriterTest {
    private val snapshot = DbSnapshot(
        categories = listOf(Category(7, "Еда", "🍔", sortOrder = 1), Category(3, "Прочее", "📦")),
        subcategories = listOf(Subcategory(40, 7, "Кафе"), Subcategory(41, 3, "Кафе")),
        expenses = listOf(
            Expense(100, 25_000, categoryId = 7, subcategoryId = 40, place = "=SUM(A1)", note = "\"кавычки\"", timestamp = 1, source = "voice"),
            Expense(55, 4_500_000, categoryId = 3, subcategoryId = 41, timestamp = 2, source = "debt"),
        ),
        expenseItems = listOf(ExpenseItem(9, 55, 0, "Телефон", 4_000_000, 1000, "шт", packMilli = 200_000, packUnit = "г")),
        incomes = listOf(Income(12, 100_000, source = "Зарплата", timestamp = 0, origin = "voice")),
        reconciliations = listOf(Reconciliation(1, 3, 0, 10, -10, expenseId = 100, incomeId = 12, isOpening = false)),
        rules = listOf(Rule(5, "кофейня", 7, 40, 3)),
        weeklyTasks = listOf(WeeklyTask(2, 20_000, "CATEGORY_LIMIT", categoryId = 999, title = "Старая", target = 1)),
        debts = listOf(Debt(77, "Телефон", categoryId = 3, subcategoryId = 41, kind = "loan", rateBasisPoints = 2000)),
        debtPayments = listOf(DebtPayment(1, 77, 55, "principal")),
        pendingPhrases = listOf(PendingPhrase(4, "такси 400", 9, "parsed", "{\"rows\":[]}", "ошибка")),
    )

    private fun file(s: DbSnapshot, settings: AppSettings = AppSettings()) =
        FullBackupWriter.build(s, settings.portable(), createdAt = 5, appVersion = "1.0", schemaVersion = 15)

    @Test
    fun roundTripKeepsLinks() {
        val first = file(snapshot)
        val restored = FullBackupRestore.toSnapshot(first)
        assertEquals(first, file(restored))
        assertEquals(restored.expenses[1].id, restored.debtPayments.single().expenseId)
        assertEquals(restored.expenses[1].id, restored.expenseItems.single().expenseId)
        // Фасовка переносится вместе с позицией
        assertEquals(200_000L, restored.expenseItems.single().packMilli)
        assertEquals("г", restored.expenseItems.single().packUnit)
        assertEquals(restored.expenses[0].id, restored.reconciliations.single().expenseId)
        assertEquals(restored.incomes[0].id, restored.reconciliations.single().incomeId)
    }

    @Test
    fun linksAreNamesAndLocalKeys() {
        val f = file(snapshot)
        assertEquals("Кафе", f.expenses[1].subcategory)
        assertEquals("Прочее", f.expenses[1].category)
        assertEquals(BackupDebtPayment(debt = 1, expense = 2, part = "principal"), f.debtPayments.single())
        assertEquals(BackupRule("кофейня", "Еда", "Кафе", 3), f.rules.single())
        // Задание удалённой категории: имени нет, задание остаётся
        assertNull(f.weeklyTasks.single().category)
    }

    @Test
    fun textIsWrittenAsIs() {
        val text = FullBackupWriter.encode(file(snapshot))
        // Без апострофа CSV-защиты: JSON не открывается в Excel как формулы
        assertEquals("=SUM(A1)", FullBackupReader.decode(text.toByteArray(), 15).expenses[0].place)
        assertFalse(text.contains("'=SUM"))
    }

    @Test
    fun noSecretsInFile() {
        val text = FullBackupWriter.encode(
            file(snapshot, AppSettings(deepseekKey = "sk-SECRET"))
        )
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("Key\""))
        assertFalse(text.contains("FolderId"))
    }
}
