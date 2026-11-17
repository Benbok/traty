package ru.my.traty.backup

import ru.my.traty.data.AppDatabase
import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.Reconciliation
import ru.my.traty.data.Recurring
import ru.my.traty.data.Rule
import ru.my.traty.data.Subcategory
import ru.my.traty.data.Template
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtPayment
import ru.my.traty.data.debt.PaymentPart
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.weekly.WeeklyTask
import ru.my.traty.data.weekly.WeeklyTaskType

/**
 * Заполняет базу записями всех таблиц со связями. Id намеренно «рваные» (сначала мусорная категория,
 * потом удалённая): после восстановления id будут другими, а связи должны совпасть.
 */
internal suspend fun fillAllTables(db: AppDatabase) {
    val cats = db.categories()
    val gone = cats.insert(Category(name = "Временная", emoji = "🗑"))
    val food = cats.insert(Category(name = "Еда", emoji = "🍔", color = 0x7FFF0000, sortOrder = 1, isImpulsive = true))
    val other = cats.insert(Category(name = "Прочее", emoji = "📦", sortOrder = 9))
    val cafe = cats.insertSub(Subcategory(categoryId = food, name = "Кафе", sortOrder = 0))
    cats.insertSub(Subcategory(categoryId = food, name = "Магазин", sortOrder = 1))
    cats.insertSub(Subcategory(categoryId = other, name = "Кафе", sortOrder = 0))

    val e = db.expenses()
    val coffee = e.insert(
        Expense(amountKopecks = 25_000, categoryId = food, subcategoryId = cafe, place = "=Кофейня", note = "латте\n«с собой»",
            timestamp = 1_000, rawText = "кофе 250", source = ExpenseSource.VOICE)
    )
    val shop = e.insert(Expense(amountKopecks = 4_500_000, categoryId = other, place = "Эльдорадо", timestamp = 2_000))
    val advance = e.insert(Expense(amountKopecks = 2_000_000, categoryId = other, timestamp = 3_000, source = ExpenseSource.DEBT))
    val fix = e.insert(Expense(amountKopecks = 1_234, categoryId = other, timestamp = 4_000, source = ExpenseSource.RECONCILE))

    db.expenseItems().insertAll(
        listOf(
            ExpenseItem(expenseId = shop, position = 0, name = "Телефон", priceKopecks = 4_000_000, quantityMilli = 1000, unit = "шт"),
            ExpenseItem(expenseId = shop, position = 1, name = "Чехол", priceKopecks = 500_000),
        )
    )
    val salary = db.incomes().insert(Income(amountKopecks = 10_000_000, source = "Зарплата", timestamp = 500, origin = IncomeOrigin.VOICE))
    db.incomes().insert(Income(amountKopecks = 100, source = "Прочее", note = "-минус", timestamp = 600))
    db.reconciliations().insert(Reconciliation(timestamp = 100, actualKopecks = 0, expectedKopecks = null, differenceKopecks = 0, expenseId = null, incomeId = salary, isOpening = true))
    db.reconciliations().insert(Reconciliation(timestamp = 4_000, actualKopecks = 50, expectedKopecks = 1_284, differenceKopecks = -1_234, expenseId = fix, incomeId = null, isOpening = false))

    db.rules().insert(Rule(pattern = "кофейня", categoryId = food, subcategoryId = cafe, hits = 7))
    db.rules().insert(Rule(pattern = "эльдорадо", categoryId = other, hits = 2))
    db.templates().insert(Template(title = "Обед", amountKopecks = 40_000, categoryId = food, subcategoryId = cafe, place = "Столовая"))
    db.recurring().insert(
        Recurring(title = "Кино", amountKopecks = 39_900, categoryId = other, subcategoryId = null, periodUnit = "month",
            nextEpochDay = 20_000, detectKey = "кино|39900", amountVaries = true)
    )
    db.budgets().insert(Budget(categoryId = food, subcategoryId = cafe, month = 202611, amountKopecks = 300_000))
    db.budgets().insert(Budget(categoryId = food, month = 202611, amountKopecks = 1_000_000, repeats = false))
    db.monthBudgets().insert(MonthBudget(month = 202611, amountKopecks = 5_000_000))
    db.weeklyTasks().insert(WeeklyTask(weekStart = 20_400, type = WeeklyTaskType.CATEGORY_LIMIT.name, categoryId = food, title = "Кафе до 2000", target = 200_000))
    // У задания нет внешнего ключа на категорию: категорию удалят, задание останется
    db.weeklyTasks().insert(WeeklyTask(weekStart = 20_400, type = WeeklyTaskType.REVIEW_SUBSCRIPTIONS.name, categoryId = gone, title = "Подписки", target = 1, checked = true))
    val fridge = db.debts().insert(
        Debt(title = "Холодильник", place = "М.Видео", categoryId = other, kind = DebtKind.INSTALLMENT.key, totalKopecks = 6_000_000,
            paymentsCount = 3, firstDueEpochDay = 20_410, createdAt = 3_000)
    )
    db.debts().insertPayment(DebtPayment(debtId = fridge, expenseId = advance, part = PaymentPart.PRINCIPAL))
    db.pendingPhrases().insert(PendingPhrase(text = "такси 400", createdAt = 5_000))
    db.pendingPhrases().insert(PendingPhrase(text = "хлеб 60", createdAt = 6_000, status = PendingStatus.PARSED, resultJson = "{}", error = "проверьте"))
    cats.delete(cats.getAllWithSubs().single { it.category.id == gone }.category)
}
