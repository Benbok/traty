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
 * Покупки частями: завести покупку с первым платежом, добавить доплату, закрыть или отменить.
 * Каждый платёж записывается обычной тратой с источником [ExpenseSource.DEBT]: так он попадает в траты,
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
        val id = dao.insert(debt.copy(id = 0, title = normalizeName(debt.title), createdAt = System.currentTimeMillis()))
        if (firstPaymentKopecks != null && firstPaymentKopecks > 0) pay(id, firstPaymentKopecks, day, zone, today, first = true)
        id
    }

    /**
     * Платёж по покупке. У кредита из суммы сначала гасятся проценты ближайшего платежа по графику,
     * остальное идёт в тело; проценты — отдельной тратой в «Долги и кредиты».
     * Когда остаток доходит до нуля, покупка закрывается.
     * @return false, если покупки нет или она уже закрыта.
     */
    suspend fun addPayment(
        debtId: Long,
        amountKopecks: Long,
        day: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(),
    ): Boolean = db.withTransaction { pay(debtId, amountKopecks, day, zone, today, first = false) }

    suspend fun getActive(): List<Debt> = dao.getActive()

    fun observeAll() = dao.observeAll()

    fun observePayments() = dao.observePayments()

    suspend fun status(debtId: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now()): DebtStatus? {
        val debt = dao.getById(debtId) ?: return null
        return DebtProgress.of(debt, paidParts(debtId, zone), today)
    }

    /** Отменить покупку (вернули товар): платежи остаются тратами, пока пользователь их не удалит. */
    suspend fun cancel(debtId: Long) {
        val debt = dao.getById(debtId) ?: return
        dao.update(debt.copy(state = DebtState.CANCELLED))
    }

    private suspend fun pay(debtId: Long, amountKopecks: Long, day: LocalDate, zone: ZoneId, today: LocalDate, first: Boolean): Boolean {
        if (amountKopecks <= 0) return false
        val debt = dao.getById(debtId)?.takeIf { it.state == DebtState.ACTIVE } ?: return false
        val before = DebtProgress.of(debt, paidParts(debtId, zone), today)
        val next = before.next
        val interest = if (debt.debtKind == DebtKind.LOAN && next != null) minOf(amountKopecks, next.interestKopecks) else 0L
        val principal = amountKopecks - interest
        val timestamp = entryTimestamp(day, zone, today)

        if (principal > 0) {
            val note = DebtTexts.paymentNote(debt, PaymentPart.PRINCIPAL, next?.number, next?.count, first)
            insert(debt, debt.categoryId, debt.subcategoryId, principal, note, timestamp, PaymentPart.PRINCIPAL)
        }
        if (interest > 0) {
            val (catId, subId) = interestCategory() ?: (debt.categoryId to debt.subcategoryId)
            val note = DebtTexts.paymentNote(debt, PaymentPart.INTEREST, next?.number, next?.count, first)
            insert(debt, catId, subId, interest, note, timestamp, PaymentPart.INTEREST)
        }
        if (DebtProgress.of(debt, paidParts(debtId, zone), today).isPaidOff) dao.update(debt.copy(state = DebtState.CLOSED))
        return true
    }

    private suspend fun insert(debt: Debt, categoryId: Long, subcategoryId: Long?, amount: Long, note: String, timestamp: Long, part: String) {
        val expenseId = db.expenses().insert(
            Expense(
                amountKopecks = amount,
                currency = debt.currency,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                place = debt.place,
                note = note,
                timestamp = timestamp,
                source = ExpenseSource.DEBT,
            )
        )
        dao.insertPayment(DebtPayment(debtId = debt.id, expenseId = expenseId, part = part))
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
