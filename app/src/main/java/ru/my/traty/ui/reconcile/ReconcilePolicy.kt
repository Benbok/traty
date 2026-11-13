package ru.my.traty.ui.reconcile

import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate

/** Чистые правила: когда предлагать сверку и каким днём датировать запись расхождения. */
object ReconcilePolicy {
    /**
     * [referenceDate] - дата последней сверки, а если сверок не было - самой ранней записи
     * (`null` - записей нет, предлагать нечего). Карточка скрыта, пока [snoozeUntil] не раньше [today].
     */
    fun shouldPrompt(today: LocalDate, startDay: Int, referenceDate: LocalDate?, snoozeUntil: LocalDate?): Boolean {
        referenceDate ?: return false
        if (snoozeUntil != null && !snoozeUntil.isBefore(today)) return false
        return periodIsOver(today, startDay, referenceDate)
    }

    /** Последний день закончившегося периода, если сверка делается уже в новом; иначе [today]. */
    fun entryDate(
        today: LocalDate,
        startDay: Int,
        lastReconciledDate: LocalDate?,
        firstRecordDate: LocalDate?,
    ): LocalDate {
        val reference = lastReconciledDate ?: firstRecordDate ?: return today
        return if (periodIsOver(today, startDay, reference)) {
            BillingPeriod.containing(today, startDay).previous(startDay).endDate
        } else {
            today
        }
    }

    /**
     * Сумма из строки калькулятора в копейках: пробелы (в т.ч. неразрывные) и «₽» игнорируются,
     * запятая = точка. Пустое или неразборчивое значение - `null`.
     */
    fun parseKopecks(text: String): Long? {
        val cleaned = text.filter { !it.isWhitespace() && it != '₽' }.replace(',', '.')
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.setScale(0, java.math.RoundingMode.HALF_UP)
            ?.toLong()
    }

    /** Непустая строка, которая не разбирается как сумма (например, буква «О» вместо нуля). */
    fun isInvalidAmount(text: String): Boolean = text.isNotBlank() && parseKopecks(text) == null

    fun hasInvalidLine(amountTexts: List<String>): Boolean = amountTexts.any(::isInvalidAmount)

    private fun periodIsOver(today: LocalDate, startDay: Int, reference: LocalDate): Boolean =
        BillingPeriod.containing(today, startDay).startDate
            .isAfter(BillingPeriod.containing(reference, startDay).startDate)
}
