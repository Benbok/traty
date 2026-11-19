package ru.my.traty.backup

import kotlinx.serialization.json.Json
import ru.my.traty.data.PortableSettings
import ru.my.traty.data.backup.DbSnapshot
import java.io.Writer

/**
 * Снимок базы → файл полной копии. Чистая функция: id старого телефона превращаются в названия
 * и локальные ключи, секретов в результате нет (их нет ни в [DbSnapshot], ни в [PortableSettings]).
 * Текст пишется без изменений: защита от формул ([CsvBackup.guardFormula]) нужна только для CSV.
 */
object FullBackupWriter {
    internal val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun build(
        s: DbSnapshot,
        settings: PortableSettings,
        createdAt: Long,
        appVersion: String,
        schemaVersion: Int,
    ): FullBackup {
        val catName = s.categories.associate { it.id to it.name }
        val subName = s.subcategories.associate { it.id to it.name }
        val expenseKey = s.expenses.mapIndexed { i, e -> e.id to (i + 1).toLong() }.toMap()
        val incomeKey = s.incomes.mapIndexed { i, e -> e.id to (i + 1).toLong() }.toMap()
        val debtKey = s.debts.mapIndexed { i, d -> d.id to (i + 1).toLong() }.toMap()
        val goalKey = s.purchaseGoals.mapIndexed { i, goal -> goal.id to (i + 1L) }.toMap()
        fun cat(id: Long): String = catName[id] ?: throw FullBackupException("Не удалось сохранить копию: в базе нарушены связи категорий.")
        fun sub(id: Long?): String? = id?.let { subName[it] }

        return FullBackup(
            formatVersion = FULL_BACKUP_FORMAT_VERSION,
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            appVersion = appVersion,
            categories = s.categories.map {
                BackupCategory(it.name, it.emoji, it.color, it.sortOrder, it.isArchived, it.isImpulsive)
            },
            subcategories = s.subcategories.mapNotNull { sc ->
                catName[sc.categoryId]?.let { BackupSubcategory(it, sc.name, sc.sortOrder) }
            },
            expenses = s.expenses.map { e ->
                BackupExpense(
                    key = expenseKey.getValue(e.id), amountKopecks = e.amountKopecks, currency = e.currency,
                    category = cat(e.categoryId), subcategory = sub(e.subcategoryId), place = e.place, note = e.note,
                    timestamp = e.timestamp, rawText = e.rawText, source = e.source,
                )
            },
            expenseItems = s.expenseItems.mapNotNull { i ->
                expenseKey[i.expenseId]?.let { BackupExpenseItem(it, i.position, i.name, i.priceKopecks, i.quantityMilli, i.unit, i.packMilli, i.packUnit) }
            },
            incomes = s.incomes.map { i ->
                BackupIncome(incomeKey.getValue(i.id), i.amountKopecks, i.currency, i.source, i.note, i.timestamp, i.origin)
            },
            reconciliations = s.reconciliations.map { r ->
                BackupReconciliation(
                    r.timestamp, r.actualKopecks, r.expectedKopecks, r.differenceKopecks,
                    expense = r.expenseId?.let { expenseKey[it] }, income = r.incomeId?.let { incomeKey[it] }, isOpening = r.isOpening,
                )
            },
            rules = s.rules.map { BackupRule(it.pattern, cat(it.categoryId), sub(it.subcategoryId), it.hits) },
            templates = s.templates.map {
                BackupTemplate(it.title, it.amountKopecks, cat(it.categoryId), sub(it.subcategoryId), it.place, it.sortOrder)
            },
            recurring = s.recurring.map {
                BackupRecurring(
                    it.title, it.amountKopecks, it.currency, cat(it.categoryId), sub(it.subcategoryId), it.place,
                    it.periodUnit, it.periodEvery, it.nextEpochDay, it.remindDaysBefore, it.state, it.autoDetected,
                    it.detectKey, it.amountVaries,
                )
            },
            budgets = s.budgets.map { BackupBudget(cat(it.categoryId), sub(it.subcategoryId), it.month, it.amountKopecks, it.repeats) },
            monthBudgets = s.monthBudgets.map { BackupMonthBudget(it.month, it.amountKopecks, it.repeats) },
            weeklyTasks = s.weeklyTasks.map {
                BackupWeeklyTask(
                    it.weekStart, it.type, it.categoryId?.let { id -> catName[id] }, it.title, it.target, it.usual,
                    it.usualKopecks, it.custom, it.active, it.checked, it.createdAt,
                )
            },
            debts = s.debts.map { d ->
                val category = catName[d.categoryId]
                BackupDebt(
                    key = debtKey.getValue(d.id), title = d.title, place = d.place, category = category,
                    subcategory = if (category != null) sub(d.subcategoryId) else null, kind = d.kind,
                    direction = d.direction, currency = d.currency, totalKopecks = d.totalKopecks,
                    rateBasisPoints = d.rateBasisPoints, paymentsCount = d.paymentsCount, periodUnit = d.periodUnit,
                    periodEvery = d.periodEvery, firstDueEpochDay = d.firstDueEpochDay, paymentKopecks = d.paymentKopecks,
                    state = d.state, createdAt = d.createdAt,
                )
            },
            debtPayments = s.debtPayments.mapNotNull { p ->
                val debt = debtKey[p.debtId] ?: return@mapNotNull null
                val expense = expenseKey[p.expenseId] ?: return@mapNotNull null
                BackupDebtPayment(debt, expense, p.part)
            },
            pendingPhrases = s.pendingPhrases.map { BackupPendingPhrase(it.text, it.createdAt) },
            purchaseGoals = s.purchaseGoals.map { goal ->
                BackupPurchaseGoal(goalKey.getValue(goal.id), goal.title, goal.targetKopecks, goal.dueEpochDay, goal.actualPriceKopecks, goal.hidden)
            },
            purchaseContributions = s.purchaseContributions.map { contribution ->
                val goal = goalKey[contribution.goalId] ?: brokenPurchaseLink()
                val expense = expenseKey[contribution.expenseId] ?: brokenPurchaseLink()
                BackupPurchaseContribution(goal, expense)
            }.sortedBy { it.expense },
            housing = ApartmentBackupCodec.portable(s.housing),
            settings = settings,
        )
    }

    private fun brokenPurchaseLink(): Nothing =
        throw FullBackupException("Не удалось сохранить копию: в базе нарушены связи взносов с покупками.")

    fun encode(b: FullBackup): String = json.encodeToString(FullBackup.serializer(), b)

    fun write(b: FullBackup, out: Writer) {
        out.write(encode(b))
        out.flush()
    }
}
