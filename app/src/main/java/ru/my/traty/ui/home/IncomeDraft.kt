package ru.my.traty.ui.home

import ru.my.traty.data.Income
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong

/** Строка черновика пополнения: поля диалога подтверждения, всё текстом. */
data class IncomeDraftItem(
    val amountText: String,
    val source: String,
    val note: String,
    val dateText: String,
)

data class IncomeDraft(val items: List<IncomeDraftItem>, val rawText: String, val origin: String)

// Неразрывный пробел (U+00A0) и узкий неразрывный пробел (U+202F) — из кодов, чтобы не прятать их в исходнике
private val NBSP: Char = 0x00A0.toChar()
private val NNBSP: Char = 0x202F.toChar()
private val spaceLike = Regex("[\\s$NBSP$NNBSP]")

/**
 * Черновик → запись [Income] или null, если сумма некорректна/неположительна.
 * Сегодня — текущее время, другая дата — полдень; неразобранная дата = сегодня.
 */
fun IncomeDraftItem.toIncome(origin: String, zone: ZoneId, today: LocalDate = LocalDate.now()): Income? {
    val amount = amountText.replace(spaceLike, "").replace(',', '.').toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount <= 0) return null
    val kopecks = (amount * 100).roundToLong()
    if (kopecks <= 0) return null
    val day = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: today
    val time = if (day == today) LocalTime.now() else LocalTime.NOON
    return Income(
        amountKopecks = kopecks,
        source = source,
        note = note.trim().ifBlank { null },
        timestamp = day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
        origin = origin,
    )
}
