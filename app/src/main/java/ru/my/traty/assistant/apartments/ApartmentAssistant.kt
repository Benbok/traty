package ru.my.traty.assistant.apartments

import ru.my.traty.data.apartments.*
import ru.my.traty.data.sameName
import java.time.LocalDate
import java.util.Locale

/** Только локальные расчёты. Эти факты можно показать без ИИ или передать для пояснения. */
object ApartmentAssistant {
    fun answer(q: ApartmentQuestion, s: ApartmentSnapshot): String {
        val apartments = if (q.apartment.isNullOrBlank()) s.apartments.filterNot { it.archived }
            else s.apartments.filter { sameName(it.name, q.apartment) }
        if (apartments.isEmpty()) return "Квартира не найдена. Добавьте её в разделе «Квартиры» или уточните название."
        if (q.apartment != null && apartments.size > 1) return "Уточните квартиру: несколько квартир имеют одинаковое название. Переименуйте их в разделе «Квартиры»."
        val resource = q.resource?.let { name -> ResourceType.entries.firstOrNull { it.name == name } }
        if (q.resource != null && resource == null) return "Уточните ресурс: холодная вода, горячая вода, электричество или газ."
        val result = mutableListOf<String>()
        for (a in apartments) {
            result += "Квартира «${a.name}»"
            val candidates = s.meters.filter { it.apartmentId == a.id && (q.meter != null || q.from != null || !it.archived) && (resource == null || it.resource == resource.name) }
            val meters = if (q.meter.isNullOrBlank()) candidates else candidates.filter { sameName(it.name, q.meter) }
            if (q.meter != null && meters.size > 1) return "Уточните счётчик: несколько приборов имеют это название."
            if (meters.isEmpty()) { result += "Подходящие счётчики не найдены."; continue }
            for ((r, group) in meters.groupBy { it.resource }) {
                val unit = ResourceType.valueOf(r).unit
                result += ResourceType.valueOf(r).title
                var series = s.series(group.map { it.id }.toSet())
                if (q.register != null) series = series.filter { it.code.equals(q.register, ignoreCase = true) }
                when (q.kind) {
                    "readings" -> series.forEach { rs ->
                        val meter = group.first { it.id == rs.meterId }
                        val last = rs.points.maxByOrNull { it.measuredAt }
                        result += if (last == null) "${meter.name} ${rs.code}: показаний пока нет." else "${meter.name} ${rs.code}: ${ReadingValues.format(last.valueMilli)} $unit, снято ${ReadingDates.decode(last.measuredAt)}."
                    }
                    "trend" -> series.forEach { rs ->
                        result += "${group.first { it.id == rs.meterId }.name} ${rs.code}"
                        val intervals = Consumption.intervals(rs.points).filter { span ->
                            (q.from == null || ReadingDates.decode(span.from) >= LocalDate.parse(q.from)) &&
                                (q.to == null || ReadingDates.decode(span.to) <= LocalDate.parse(q.to))
                        }
                        result += intervals.map { interval(it, unit) }.ifEmpty { listOf("Нет завершённых интервалов за указанные даты.") }
                    }
                    else -> {
                        if (q.from != null && q.to != null) {
                            fun range(from: String, to: String): ConsumptionInterval? {
                                val f = ReadingDates.encode(LocalDate.parse(from)); val t = ReadingDates.encode(LocalDate.parse(to))
                                val relevant = series.filter { (it.accountingFrom == null || it.accountingFrom <= t) && (it.accountingTo == null || it.accountingTo >= f) }
                                return ConsumptionGroups.forRange(relevant, f, t)
                            }
                            val current = range(q.from, q.to)
                            result += current?.let { interval(it, unit) } ?: "Точный итог за ${q.from} — ${q.to} неизвестен: нет показаний на общих границах или менялся состав приборов."
                            if (q.compareFrom != null && q.compareTo != null) {
                                val previous = range(q.compareFrom, q.compareTo)
                                result += previous?.let { interval(it, unit) } ?: "Для второго периода недостаточно данных."
                                if (current != null && previous != null) result += difference(ConsumptionComparison(previous, current))
                            }
                        } else {
                            val comparison = ConsumptionGroups.lastComparison(series)
                            if (comparison == null) {
                                result += "Недостаточно данных для общего сравнения: нужны три общие даты замеров и известные границы учёта."
                                series.forEach { rs -> result += "${group.first { it.id == rs.meterId }.name} ${rs.code}: " + Consumption.intervals(rs.points).lastOrNull()?.let { interval(it, unit) }.orEmpty() }
                            } else result += listOf(interval(comparison.previous, unit), interval(comparison.current, unit), difference(comparison))
                        }
                    }
                }
            }
        }
        return result.joinToString("\n")
    }
    private fun interval(v: ConsumptionInterval, unit: String) = "${ReadingDates.decode(v.from)} — ${ReadingDates.decode(v.to)}: ${ReadingValues.format(v.milli)} $unit за ${v.days} дн.; ${String.format(Locale.forLanguageTag("ru"), "%.3f", v.daily)} $unit/сутки."
    private fun difference(c: ConsumptionComparison): String = c.dailyChangePercent?.let { "Среднесуточное потребление изменилось на ${String.format(Locale.forLanguageTag("ru"), "%+.1f", it)} %." } ?: "Предыдущий расход равен нулю; процент изменения не рассчитывается."
}
