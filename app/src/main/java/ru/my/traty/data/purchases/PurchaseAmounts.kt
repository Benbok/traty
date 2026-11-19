package ru.my.traty.data.purchases

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Дата покупки входит в срок. В последний день нужно внести весь остаток. */
fun dailyPurchaseAmount(remainingKopecks: Long, today: LocalDate, due: LocalDate): Long {
    val remaining = remainingKopecks.coerceAtLeast(0)
    val days = (ChronoUnit.DAYS.between(today, due) + 1).coerceAtLeast(1)
    return remaining / days + if (remaining % days == 0L) 0 else 1
}
