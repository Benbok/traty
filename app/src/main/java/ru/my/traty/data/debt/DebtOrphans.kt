package ru.my.traty.data.debt

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Покупка, которую завели из траты, живёт, пока у неё есть платежи. Если удалили единственную трату
 * («аванс за телевизор»), покупка тоже уходит: иначе следующая такая же фраза приняла бы её за доплату.
 *
 * Кредит, взятый без оплаты в день покупки, остаётся и без платежей: его первый платёж по графику позже
 * дня, когда покупку завели.
 */
object DebtOrphans {
    fun isOrphan(debt: Debt, paymentsLeft: Int, zone: ZoneId): Boolean {
        if (paymentsLeft > 0 || debt.state != DebtState.ACTIVE) return false
        val created = Instant.ofEpochMilli(debt.createdAt).atZone(zone).toLocalDate()
        val firstDue = debt.firstDueEpochDay?.let(LocalDate::ofEpochDay)
        return firstDue == null || !firstDue.isAfter(created)
    }
}
