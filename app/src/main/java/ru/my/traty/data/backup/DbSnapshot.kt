package ru.my.traty.data.backup

import ru.my.traty.data.Category
import ru.my.traty.data.Expense
import ru.my.traty.data.Income
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.Reconciliation
import ru.my.traty.data.Recurring
import ru.my.traty.data.Rule
import ru.my.traty.data.Subcategory
import ru.my.traty.data.Template
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtPayment
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.weekly.WeeklyTask

/**
 * Всё содержимое базы одним значением: что прочитано для полной копии или что будет записано при восстановлении.
 * Обычные Kotlin-классы, без Android: копию можно собрать и разобрать в тесте без телефона.
 */
data class DbSnapshot(
    val housing: ru.my.traty.data.apartments.ApartmentSnapshot = ru.my.traty.data.apartments.ApartmentSnapshot(),
    val categories: List<Category> = emptyList(),
    val subcategories: List<Subcategory> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val expenseItems: List<ExpenseItem> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val reconciliations: List<Reconciliation> = emptyList(),
    val templates: List<Template> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val monthBudgets: List<MonthBudget> = emptyList(),
    val weeklyTasks: List<WeeklyTask> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val debtPayments: List<DebtPayment> = emptyList(),
    val pendingPhrases: List<PendingPhrase> = emptyList(),
)
