package ru.my.traty.ai

import kotlinx.serialization.Serializable

/**
 * Условия покупки частями из фразы. ИИ (или простой разбор) только достаёт слова и числа,
 * график, остаток и переплату считает приложение.
 *
 * [kind]: [PurchaseTerms.PARTIAL] (аванс, предоплата), [INSTALLMENT] (рассрочка, «Долями», «Сплит»),
 * [LOAN] (кредит), [TOPUP] (доплата за уже начатую покупку).
 * [total] — полная цена в рублях, [count] — число платежей, [rate] — % годовых,
 * [payment] — платёж по договору в рублях, [period] — "month", "week" или "2weeks".
 */
@Serializable
data class PurchaseTerms(
    val kind: String? = null,
    val total: Double? = null,
    val count: Int? = null,
    val rate: Double? = null,
    val payment: Double? = null,
    val period: String? = null,
) {
    /** Вид, приведённый к одному из известных; неизвестный читаем как аванс. */
    val normalizedKind: String
        get() = when (kind?.trim()?.lowercase()) {
            INSTALLMENT, "рассрочка" -> INSTALLMENT
            LOAN, "credit", "кредит" -> LOAN
            TOPUP, "доплата" -> TOPUP
            else -> PARTIAL
        }

    companion object {
        const val PARTIAL = "partial"
        const val INSTALLMENT = "installment"
        const val LOAN = "loan"
        const val TOPUP = "topup"
    }
}

/**
 * Условия покупки частями без ИИ: «в рассрочку на 6 месяцев», «в кредит под 20 процентов на год»,
 * «Долями», «всего 60 тысяч», «доплатил за холодильник». null — во фразе про покупку частями ничего нет.
 */
object PurchaseTermsParser {
    private val loanRe = Regex("""(?<![\p{L}])(в\s+кредит|кредит\p{L}*)""", RegexOption.IGNORE_CASE)
    private val installmentRe = Regex("""(?<![\p{L}])(рассрочк\p{L}*|частями|долями|сплит\p{L}*)""", RegexOption.IGNORE_CASE)
    private val bnplRe = Regex("""(?<![\p{L}])долями(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val advanceRe = Regex("""(?<![\p{L}])(аванс\p{L}*|предоплат\p{L}*)""", RegexOption.IGNORE_CASE)
    private val downPaymentRe = Regex("""(?<![\p{L}])(первый\s+взнос|первоначальн\p{L}*)""", RegexOption.IGNORE_CASE)
    private val topupRe = Regex("""(?<![\p{L}])(доплат\p{L}*|пополнени\p{L}*|пополнил\p{L}*|ещё\s+за|еще\s+за|плюс)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    private val countRe = Regex(
        """(?<![\p{L}])на\s+(\d{1,3}|[\p{L}]+(?:\s+[\p{L}]+)?)\s+(месяц\p{L}*|мес|недел\p{L}*|платеж\p{L}*|платёж\p{L}*|част\p{L}*|год\p{L}*|лет)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )
    private val yearRe = Regex("""(?<![\p{L}])на\s+(год|полгода|полтора\s+года)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val rateRe = Regex("""(?<![\p{L}])под\s+(\d{1,2}(?:[.,]\d{1,2})?|[\p{L}]+(?:\s+[\p{L}]+)?)\s*(%|процент\p{L}*)""", RegexOption.IGNORE_CASE)
    private val totalRe = Regex("""(?<![\p{L}])(всего|стоит|стоил\p{L}*|стоимость\p{L}*|ценой|цена|общая\s+сумма)\s+(.{1,40})""", RegexOption.IGNORE_CASE)

    private val smallNumbers = mapOf(
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8,
        "девять" to 9, "десять" to 10, "двенадцать" to 12, "восемнадцать" to 18, "двадцать четыре" to 24,
        "тридцать шесть" to 36, "сорок восемь" to 48,
    )

    fun find(text: String): PurchaseTerms? {
        val kind = when {
            loanRe.containsMatchIn(text) -> PurchaseTerms.LOAN
            installmentRe.containsMatchIn(text) -> PurchaseTerms.INSTALLMENT
            // «пришёл аванс» — зарплата, а «аванс за холодильник» — оплата покупки
            advanceRe.containsMatchIn(text) && PurchasePayment.isPurchasePayment(text) -> PurchaseTerms.PARTIAL
            downPaymentRe.containsMatchIn(text) -> PurchaseTerms.PARTIAL
            topupRe.containsMatchIn(text) && PurchasePayment.isPurchasePayment(text) -> PurchaseTerms.TOPUP
            else -> return null
        }
        val (count, period) = count(text)
        val bnpl = bnplRe.containsMatchIn(text)
        return PurchaseTerms(
            kind = kind,
            total = total(text),
            count = count ?: if (bnpl) 4 else null,
            rate = if (kind == PurchaseTerms.LOAN) rate(text) else null,
            period = period ?: if (bnpl) "2weeks" else null,
        )
    }

    private fun count(text: String): Pair<Int?, String?> {
        yearRe.find(text)?.let { m ->
            val v = m.groupValues[1].lowercase()
            return (if (v == "год") 12 else if (v == "полгода") 6 else 18) to "month"
        }
        val m = countRe.find(text) ?: return null to null
        val n = number(m.groupValues[1]) ?: return null to null
        val unit = m.groupValues[2].lowercase()
        return when {
            unit.startsWith("недел") -> n to "week"
            unit.startsWith("год") || unit == "лет" -> n * 12 to "month"
            unit.startsWith("месяц") || unit == "мес" -> n to "month"
            else -> n to null
        }
    }

    private fun rate(text: String): Double? {
        val m = rateRe.find(text) ?: return null
        val raw = m.groupValues[1]
        return raw.replace(',', '.').toDoubleOrNull() ?: SpokenAmountParser.find(raw)?.value
    }

    private fun total(text: String): Double? {
        val m = totalRe.find(text) ?: return null
        return SpokenAmountParser.find(m.groupValues[2])?.value?.takeIf { it > 0 }
    }

    private fun number(raw: String): Int? =
        raw.toIntOrNull() ?: smallNumbers[raw.trim().lowercase()] ?: smallNumbers[raw.trim().lowercase().substringAfterLast(' ')]
}
