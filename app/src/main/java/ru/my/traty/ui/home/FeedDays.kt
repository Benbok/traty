package ru.my.traty.ui.home

import ru.my.traty.ui.formatDateWithWeekday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Заголовок дня над первой записью этого дня: дата и сколько потрачено за день (копейки). */
data class DayHeader(val date: LocalDate, val expenseTotalKopecks: Long)

/** Строка ленты для расчёта заголовков: без Room-сущностей, чтобы считалось обычным тестом. */
data class DayEntry(val id: String, val timestamp: Long, val expenseKopecks: Long)

/**
 * Для ленты, отсортированной от новых к старым, возвращает заголовки дней:
 * ключ — id первой записи дня, значение — день и сумма трат за него.
 */
fun dayHeaders(entries: List<DayEntry>, zone: ZoneId): Map<String, DayHeader> {
    val result = LinkedHashMap<String, DayHeader>()
    var firstId: String? = null
    var day: LocalDate? = null
    var total = 0L
    fun flush() {
        val id = firstId
        val d = day
        if (id != null && d != null) result[id] = DayHeader(d, total)
    }
    for (e in entries) {
        val d = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
        if (d != day) {
            flush()
            firstId = e.id
            day = d
            total = 0L
        }
        total += e.expenseKopecks
    }
    flush()
    return result
}

/** «Сегодня», «Вчера» или «13 ноября, пт». */
fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> formatDateWithWeekday(date).replaceFirstChar { it.uppercase() }
}
