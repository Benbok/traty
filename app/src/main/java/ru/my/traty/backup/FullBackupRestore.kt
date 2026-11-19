package ru.my.traty.backup

import ru.my.traty.data.Category
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.Income
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.Reconciliation
import ru.my.traty.data.Recurring
import ru.my.traty.data.Rule
import ru.my.traty.data.Subcategory
import ru.my.traty.data.Template
import ru.my.traty.data.backup.DbSnapshot
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.MonthBudget
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtPayment
import ru.my.traty.data.items.ExpenseItem
import ru.my.traty.data.purchases.PurchaseContribution
import ru.my.traty.data.purchases.PurchaseGoal
import ru.my.traty.data.sameName
import ru.my.traty.data.weekly.WeeklyTask
import java.time.LocalDate

/**
 * Файл копии → записи для пустой базы, с новыми id по порядку (1, 2, 3…). Чистая функция.
 * Связи восстанавливаются по названиям и локальным ключам. Ссылка на то, чего в файле нет, —
 * признак повреждённой копии: восстановление отклоняется целиком, до записи в базу.
 */
object FullBackupRestore {

    fun toSnapshot(b: FullBackup): DbSnapshot {
        val cats = b.categories.mapIndexed { i, c ->
            Category(i + 1L, c.name, c.emoji, c.color, c.sortOrder, c.isArchived, c.isImpulsive)
        }
        if (cats.map { it.name }.toSet().size != cats.size) damaged("две категории с одинаковым названием")
        fun catIdOrNull(name: String): Long? =
            cats.firstOrNull { it.name == name }?.id ?: cats.singleOrNull { sameName(it.name, name) }?.id
        fun catId(name: String): Long = catIdOrNull(name) ?: damaged("нет категории «$name»")

        val subs = b.subcategories.mapIndexed { i, s -> Subcategory(i + 1L, catId(s.category), s.name, s.sortOrder) }
        if (subs.map { it.categoryId to it.name }.toSet().size != subs.size) damaged("две одинаковые подкатегории")
        fun subId(categoryId: Long, name: String?): Long? {
            if (name == null) return null
            val own = subs.filter { it.categoryId == categoryId }
            return own.firstOrNull { it.name == name }?.id ?: own.singleOrNull { sameName(it.name, name) }?.id
                ?: damaged("нет подкатегории «$name»")
        }

        val expenseId = keys(b.expenses.map { it.key }, "траты")
        val incomeId = keys(b.incomes.map { it.key }, "пополнения")
        val debtId = keys(b.debts.map { it.key }, "покупки частями")
        fun expense(key: Long): Long = expenseId[key] ?: damaged("ссылка на трату, которой нет")
        fun income(key: Long): Long = incomeId[key] ?: damaged("ссылка на пополнение, которого нет")
        val fallbackCat = catIdOrNull(DefaultCategories.OTHER) ?: cats.first().id

        val rules = b.rules.map { r -> catId(r.category).let { c -> Rule(0, r.pattern, c, subId(c, r.subcategory), r.hits) } }
        if (rules.map { it.pattern }.toSet().size != rules.size) damaged("два одинаковых правила")
        val payments = b.debtPayments.map { p ->
            DebtPayment(0, debtId[p.debt] ?: damaged("платёж без покупки"), expense(p.expense), p.part)
        }
        if (payments.map { it.expenseId }.toSet().size != payments.size) damaged("одна трата в двух платежах")

        val (purchaseGoals, purchaseContributions) = restorePurchases(b, expenseId)
        val housing = ApartmentBackupCodec.checked(b.housing)
        return DbSnapshot(
            housing = housing,
            categories = cats,
            subcategories = subs,
            expenses = b.expenses.map { e ->
                val c = catId(e.category)
                Expense(
                    expense(e.key), e.amountKopecks, e.currency, c, subId(c, e.subcategory), e.place, e.note,
                    e.timestamp, e.rawText, e.source,
                )
            },
            expenseItems = b.expenseItems.map {
                ExpenseItem(0, expense(it.expense), it.position, it.name, it.priceKopecks, it.quantityMilli, it.unit, it.packMilli, it.packUnit)
            },
            incomes = b.incomes.map {
                Income(income(it.key), it.amountKopecks, it.currency, it.source, it.note, it.timestamp, it.origin)
            },
            reconciliations = b.reconciliations.map {
                Reconciliation(
                    0, it.timestamp, it.actualKopecks, it.expectedKopecks, it.differenceKopecks,
                    it.expense?.let(::expense), it.income?.let(::income), it.isOpening,
                )
            },
            templates = b.templates.map { t ->
                catId(t.category).let { c -> Template(0, t.title, t.amountKopecks, c, subId(c, t.subcategory), t.place, t.sortOrder) }
            },
            rules = rules,
            recurring = b.recurring.map { r ->
                val c = catId(r.category)
                Recurring(
                    0, r.title, r.amountKopecks, r.currency, c, subId(c, r.subcategory), r.place, r.periodUnit,
                    r.periodEvery, r.nextEpochDay, r.remindDaysBefore, r.state, r.autoDetected, r.detectKey, r.amountVaries,
                )
            },
            budgets = b.budgets.map { x ->
                catId(x.category).let { c -> Budget(0, c, subId(c, x.subcategory), x.month, x.amountKopecks, x.repeats) }
            },
            monthBudgets = b.monthBudgets.map { MonthBudget(0, it.month, it.amountKopecks, it.repeats) },
            weeklyTasks = b.weeklyTasks.map {
                WeeklyTask(
                    0, it.weekStart, it.type, it.category?.let(::catIdOrNull), it.title, it.target, it.usual,
                    it.usualKopecks, it.custom, it.active, it.checked, it.createdAt,
                )
            },
            debts = b.debts.map { d ->
                // У покупки нет внешнего ключа на категорию: удалённая категория — не повреждение, покупка идёт в «Прочее»
                val known = d.category?.let(::catIdOrNull)
                val c = known ?: fallbackCat
                Debt(
                    debtId.getValue(d.key), d.title, d.place, c, if (known != null) subId(c, d.subcategory) else null,
                    d.kind, d.direction, d.currency, d.totalKopecks, d.rateBasisPoints, d.paymentsCount, d.periodUnit,
                    d.periodEvery, d.firstDueEpochDay, d.paymentKopecks, d.state, d.createdAt,
                )
            },
            debtPayments = payments,
            pendingPhrases = b.pendingPhrases.map { PendingPhrase(0, it.text, it.createdAt, PendingStatus.WAITING) },
            purchaseGoals = purchaseGoals,
            purchaseContributions = purchaseContributions,
        )
    }

    private fun restorePurchases(
        backup: FullBackup,
        expenseIds: Map<Long, Long>,
    ): Pair<List<PurchaseGoal>, List<PurchaseContribution>> {
        val goalIds = keys(backup.purchaseGoals.map { it.key }, "накопления на покупки")
        val validDays = LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()
        val goals = backup.purchaseGoals.map { goal ->
            if (goal.title.isBlank() || goal.targetKopecks <= 0 ||
                goal.dueEpochDay !in validDays || (goal.actualPriceKopecks ?: 1L) <= 0
            ) damaged("некорректная цель покупки")
            PurchaseGoal(goalIds.getValue(goal.key), goal.title, goal.targetKopecks, goal.dueEpochDay, goal.actualPriceKopecks, goal.hidden)
        }

        val linkedExpenses = backup.expenses.filter { it.source == ExpenseSource.PURCHASE_CONTRIBUTION }
            .associateBy { it.key }
        val links = backup.purchaseContributions
        if (links.size != linkedExpenses.size || links.map { it.expense }.toSet() != linkedExpenses.keys) {
            damaged("неверные связи взносов с покупками")
        }
        val totals = mutableMapOf<Long, Long>()
        val contributions = links.map { link ->
            val goalId = goalIds[link.goal] ?: damaged("взнос без цели покупки")
            val expense = linkedExpenses.getValue(link.expense)
            val total = totals.getOrDefault(goalId, 0L)
            if (expense.currency != "RUB" || expense.amountKopecks <= 0 || expense.amountKopecks > Long.MAX_VALUE - total) {
                damaged("некорректная сумма взноса")
            }
            totals[goalId] = total + expense.amountKopecks
            PurchaseContribution(expenseId = expenseIds.getValue(link.expense), goalId = goalId)
        }
        return goals to contributions
    }

    /** Локальный ключ файла → новый id. Повтор ключа — повреждение. */
    private fun keys(list: List<Long>, what: String): Map<Long, Long> {
        if (list.toSet().size != list.size) damaged("повторяются записи: $what")
        return list.mapIndexed { i, k -> k to i + 1L }.toMap()
    }

    private fun damaged(why: String): Nothing =
        throw FullBackupException("Файл копии повреждён ($why). Данные на телефоне не изменились.")
}
