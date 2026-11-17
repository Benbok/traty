package ru.my.traty.ai.receipt

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Текст чека после распознавания: что из него отправлять в ИИ и какой итог напечатан в самом чеке.
 * Всё здесь считается приложением, без ИИ.
 */
object ReceiptText {
    /** Больше этого текста в ИИ не уходит: длинный чек всё равно помещается, а случайная простыня — нет. */
    const val MAX_CHARS = 8_000

    // Служебные строки кассы и данные о людях: ИИ для разбора товаров они не нужны
    private val serviceLine = Regex(
        "^\\s*(кассир|продавец|оператор|фн|фд|фп|фпд|рн\\s*ккт|зн\\s*ккт|ккт|инн|смена|сайт\\s*фнс|" +
            "эл\\.?\\s*адр|адрес\\s*эл|e-?mail|тел\\.?|телефон|покупатель|клиент|карта\\s*лояльности|qr)(?![а-яёa-z]).*",
        RegexOption.IGNORE_CASE,
    )

    /** Убирает служебные строки (кассир, номера ФН/ФД, ИНН, почта покупателя) и пустые строки, обрезает по [MAX_CHARS]. */
    fun forAi(ocrText: String): String = clean(ocrText).take(MAX_CHARS)

    /** Очистка без обрезания: несколько частей проверяются на общий размер до отправки. */
    fun clean(ocrText: String): String =
        ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !serviceLine.matches(it) }
            .joinToString("\n")

    // «ИТОГ», «ИТОГО», «ИТОГО К ОПЛАТЕ», «К ОПЛАТЕ», «ВСЕГО К ОПЛАТЕ»; «ПОДИТОГ» и «ИТОГО СКИДКА» не подходят
    private val totalLabel = Regex(
        "(?<![а-яё])(итого?(?!\\s*(скидк|ндс|бонус|баллов))|(всего\\s+)?к\\s+оплате)(?![а-яё])",
        RegexOption.IGNORE_CASE,
    )

    // Сумма: «1 234,56», «1234.56», «=1234.56», «1234». Пробелы внутри — разделители тысяч.
    private val amount = Regex("(?<![\\d.,])(\\d{1,3}(?:[ \\u00A0]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?(?![\\d])")

    /**
     * Итог, напечатанный в чеке, в копейках: последняя строка с «ИТОГ»/«К ОПЛАТЕ» и суммой.
     * Если сумма на следующей строке (касса печатает итог в две строки), берётся она. null — итог не найден.
     */
    fun printedTotal(ocrText: String): Long? {
        val lines = ocrText.lines().map { it.trim() }
        var found: Long? = null
        lines.forEachIndexed { i, line ->
            val label = totalLabel.find(line) ?: return@forEachIndexed
            val tail = line.substring(label.range.last + 1)
            val value = lastAmount(tail) ?: lines.getOrNull(i + 1)?.let { next -> lastAmount(next).takeIf { !totalLabel.containsMatchIn(next) } }
            if (value != null && value > 0) found = value
        }
        return found
    }

    /** Последняя сумма в строке, в копейках. */
    internal fun lastAmount(text: String): Long? {
        val m = amount.findAll(text).lastOrNull() ?: return null
        val whole = m.groupValues[1].replace(" ", "").replace(" ", "")
        val frac = m.groupValues[2]
        val rub = BigDecimal(if (frac.isEmpty()) whole else "$whole.$frac")
        return rub.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}

