package ru.my.traty.data.debt

import androidx.room.withTransaction
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.entryTimestamp
import ru.my.traty.data.normalizeName
import ru.my.traty.data.sameName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Покупки частями: завести покупку с первым платежом, добавить доплату, привязать уже сохранённую трату,
 * отменить. Каждый платёж — обычная трата с источником [ExpenseSource.DEBT]: так он попадает в траты,
 * лимиты и статистику в день оплаты, а поиск подписок его не трогает.
 * Вызывается только после подтверждения пользователя.
 */
class DebtRepository(private val db: AppDatabase) {
    private val dao get() = db.debts()

    /** Новая покупка; [firstPaymentKopecks] > 0 — первый платёж (аванс) в день [day]. @return id покупки. */
    suspend fun start(
        debt: Debt,
        firstPaymentKopecks: Long? = null,
        day: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): Long = db.withTransaction {
        val id = insertDebt(debt)
        if (firstPaymentKopecks != null && firstPaymentKopecks > 0) {
            val stored = checkNotNull(dao.getById(id))
            val expense = newExpense(stored, firstPaymentKopecks, entryTimestamp(day, zone, today))
            link(stored, expense, zone, today, first = true)
        }
        id
    }

    /**
     * Новая покупка из уже сохранённой траты (голосом: «аванс за холодильник 20 000», потом «Да» в вопросе).
     * [expenseId] == null — сегодня ничего не платили (взял в кредит). @return id покупки.
     */
    suspend fun startFromExpense(
        debt: Debt,
        expenseId: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): Long = db.withTransaction {
        val id = insertDebt(debt)
        val expense = expenseId?.let { db.expenses().getByIds(listOf(it)).firstOrNull()?.expense }
        if (expense != null) link(checkNotNull(dao.getById(id)), expense, zone, today, first = true)
        id
    }

    /**
     * Уже сохранённая трата — доплата за покупку («за холодильник Озону плюс 5000», потом «Да»).
     * @return false, если покупки или траты нет, покупка закрыта или трата уже привязана.
     */
    suspend fun attachExpense(
        debtId: Long,
        expenseId: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): Boolean = db.withTransaction {
        val debt = dao.getById(debtId)?.takeIf { it.state == DebtState.ACTIVE } ?: return@withTransaction false
        if (dao.isLinked(expenseId)) return@withTransaction false
        val expense = db.expenses().getByIds(listOf(expenseId)).firstOrNull()?.expense ?: return@withTransaction false
        link(debt, expense, zone, today, first = false)
        true
    }

    /**
     * Платёж по покупке новой тратой. Когда остаток доходит до нуля, покупка закрывается.
     * @return false, если покупки нет или она уже закрыта.
     */
    suspend fun addPayment(
        debtId: Long,
        amountKopecks: Long,
        day: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): Boolean = db.withTransaction {
        if (amountKopecks <= 0) return@withTransaction false
        val debt = dao.getById(debtId)?.takeIf { it.state == DebtState.ACTIVE } ?: return@withTransaction false
        link(debt, newExpense(debt, amountKopecks, entryTimestamp(day, zone, today)), zone, today, first = false)
        true
    }

    suspend fun getActive(): List<Debt> = dao.getActive()

    /** Открытые покупки вместе с платежами: для ответов ИИ-ассистента. */
    suspend fun activeWithPayments(zone: ZoneId = ZoneId.systemDefault()): List<Pair<Debt, List<PaidPart>>> =
        dao.getActive().map { it to paidParts(it.id, zone) }

    fun observeAll() = dao.observeAll()

    fun observeDebt(id: Long) = dao.observeById(id)

    suspend fun getById(id: Long): Debt? = dao.getById(id)

    fun observePayments() = dao.observePayments()

    fun observePayments(debtId: Long) = dao.observePayments(debtId)

    suspend fun getDebtByExpenseId(expenseId: Long): Debt? = dao.getDebtByExpenseId(expenseId)

    fun observeDebtByExpenseId(expenseId: Long) = dao.observeDebtByExpenseId(expenseId)

    suspend fun status(debtId: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now()): DebtStatus? {
        val debt = dao.getById(debtId) ?: return null
        return DebtProgress.of(debt, paidParts(debtId, zone), today)
    }

    /** Закрыть покупку вручную (все платежи внесены). */
    suspend fun close(debtId: Long) {
        val debt = dao.getById(debtId) ?: return
        dao.update(debt.copy(state = DebtState.CLOSED))
    }

    /** Что исчезнет при удалении покупки: для честного текста в диалоге. */
    suspend fun deletionImpact(debtId: Long): DeletionImpact {
        val expenses = db.expenses().getByIds(dao.getPaymentExpenseIds(debtId)).map { it.expense }
        val removed = expenses.filter { it.source == ExpenseSource.DEBT }
        return DeletionImpact(
            removedCount = removed.size,
            removedKopecks = removed.sumOf { it.amountKopecks },
            keptCount = expenses.size - removed.size,
        )
    }

    /**
     * Удалить покупку вместе с автоматически созданными платежами ([ExpenseSource.DEBT]).
     * Траты, которые пользователь создал вручную и позже привязал к покупке, остаются в истории.
     */
    suspend fun delete(debtId: Long) {
        db.withTransaction {
            val expenseIds = dao.getPaymentExpenseIds(debtId)
            val debt = dao.getById(debtId) ?: return@withTransaction
            dao.delete(debt)   // каскадом удаляет debt_payments
            expenseIds.forEach { expId ->
                val expense = db.expenses().getByIds(listOf(expId)).firstOrNull()?.expense ?: return@forEach
                if (expense.source == ExpenseSource.DEBT) db.expenses().deleteById(expId)
            }
        }
    }

    /** Отменить покупку (вернули товар): платежи остаются тратами, пока пользователь их не удалит. */
    suspend fun cancel(debtId: Long) {
        val debt = dao.getById(debtId) ?: return
        dao.update(debt.copy(state = DebtState.CANCELLED))
    }

    private suspend fun insertDebt(debt: Debt): Long =
        dao.insert(debt.copy(id = 0, title = normalizeName(debt.title), createdAt = System.currentTimeMillis()))

    private fun newExpense(debt: Debt, amount: Long, timestamp: Long) = Expense(
        amountKopecks = amount, currency = debt.currency, categoryId = debt.categoryId, subcategoryId = debt.subcategoryId,
        place = debt.place, timestamp = timestamp, source = ExpenseSource.DEBT,
    )

    /**
     * Записывает [expense] платежом по покупке ([Expense.id] == 0 — новая трата, иначе уже сохранённая).
     * У кредита из суммы сначала гасятся проценты ближайшего платежа по графику, они уходят отдельной
     * тратой в «Долги и кредиты». Подпись траты становится «Холодильник · доплата».
     */
    private suspend fun link(debt: Debt, expense: Expense, zone: ZoneId, today: LocalDate, first: Boolean) {
        val next = DebtProgress.of(debt, paidParts(debt.id, zone), today).next
        val amount = expense.amountKopecks
        val interest = if (debt.debtKind == DebtKind.LOAN && next != null) minOf(amount, next.interestKopecks) else 0L
        val principal = amount - interest
        val main = expense.copy(
            amountKopecks = if (principal > 0) principal else amount,
            note = DebtTexts.paymentNote(debt, if (principal > 0) PaymentPart.PRINCIPAL else PaymentPart.INTEREST, next?.number, next?.count, first),
            source = if (expense.id == 0L) ExpenseSource.DEBT else expense.source,
        )
        val mainId = if (main.id == 0L) db.expenses().insert(main) else main.id.also { db.expenses().update(main) }
        dao.insertPayment(DebtPayment(debtId = debt.id, expenseId = mainId, part = if (principal > 0) PaymentPart.PRINCIPAL else PaymentPart.INTEREST))
        if (interest > 0 && principal > 0) {
            val (catId, subId) = interestCategory() ?: (debt.categoryId to debt.subcategoryId)
            val interestId = db.expenses().insert(
                main.copy(
                    id = 0, amountKopecks = interest, categoryId = catId, subcategoryId = subId,
                    note = DebtTexts.paymentNote(debt, PaymentPart.INTEREST, next?.number, next?.count, first),
                )
            )
            dao.insertPayment(DebtPayment(debtId = debt.id, expenseId = interestId, part = PaymentPart.INTEREST))
        }
        if (DebtProgress.of(debt, paidParts(debt.id, zone), today).isPaidOff) dao.update(debt.copy(state = DebtState.CLOSED))
    }

    private suspend fun paidParts(debtId: Long, zone: ZoneId): List<PaidPart> = dao.getPayments(debtId).map {
        PaidPart(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().toEpochDay(), it.part, it.amountKopecks)
    }

    /** «Долги и кредиты · Кредит», если такая категория есть. */
    private suspend fun interestCategory(): Pair<Long, Long?>? {
        val cat = db.categories().getAllWithSubs().firstOrNull { sameName(it.category.name, INTEREST_CATEGORY) } ?: return null
        val sub = cat.subs.firstOrNull { sameName(it.name, INTEREST_SUBCATEGORY) }
        return cat.category.id to sub?.id
    }

    companion object {
        const val INTEREST_CATEGORY = "Долги и кредиты"
        const val INTEREST_SUBCATEGORY = "Кредит"
    }
}

/** Сколько платежей-трат удалится вместе с покупкой, на какую сумму, и сколько трат пользователя останется. */
data class DeletionImpact(val removedCount: Int, val removedKopecks: Long, val keptCount: Int)
