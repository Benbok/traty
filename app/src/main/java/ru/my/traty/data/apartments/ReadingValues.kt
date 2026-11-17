package ru.my.traty.data.apartments

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Значения приборов в тысячных единицы. Округление ввода запрещено. */
object ReadingValues {
    fun parseMilli(text: String): Long? {
        val clean = text.trim().replace(',', '.')
        if (!Regex("\\d+(?:\\.\\d{1,3})?").matches(clean)) return null
        return runCatching { BigDecimal(clean).movePointRight(3).longValueExact() }.getOrNull()
            ?.takeIf { it >= 0 }
    }
    fun format(value: Long): String = BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString().replace('.', ',')
}

/** Календарная дата кодируется полуночью UTC, не меняется при смене пояса телефона. */
object ReadingDates {
    fun encode(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun decode(value: Long): LocalDate = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate()
    fun normalized(value: Long): Boolean = encode(decode(value)) == value
}

enum class ResourceType(val title: String, val unit: String) {
    COLD_WATER("Холодная вода", "м³"), HOT_WATER("Горячая вода", "м³"),
    ELECTRICITY("Электричество", "кВт·ч"), GAS("Газ", "м³");
}
