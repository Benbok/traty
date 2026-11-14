package ru.my.traty.ui

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Поле ввода суммы, общее для всех экранов: в состоянии хранится «сырой» текст
 * вида `20000,5` (только цифры и одна запятая), а показывается он как `20 000,5 руб`.
 *
 * Логика без Compose, чтобы её можно было проверить обычными тестами;
 * сам VisualTransformation и поле лежат в `designsystem/components/TratyAmountField.kt`.
 */
object AmountInput {
    /** Суффикс после числа. Неразрывный пробел, чтобы «руб» не отрывался от суммы. */
    const val SUFFIX: String = "\u00A0руб"

    /** Разделитель тысяч: неразрывный пробел, как в [formatRub]. */
    const val GROUP_SEPARATOR: Char = '\u00A0'
    private const val NBSP: Char = GROUP_SEPARATOR
    private const val NNBSP: Char = '\u202F'

    /** До 999 999 999 руб: больше в приложении для трат не бывает, а Long в копейках не переполнится. */
    const val MAX_INT_DIGITS = 9
    const val MAX_FRACTION_DIGITS = 2

    data class Edit(val text: String, val cursor: Int)

    /**
     * Приводит набранное к сырому виду: точка и запятая становятся запятой, лишние
     * разделители, пробелы, «руб»/«₽» и прочие символы отбрасываются, копеек не больше двух,
     * ведущие нули убираются («007» → «7», «00,5» → «0,5»).
     *
     * [cursor] — позиция курсора в [input]; в ответе она пересчитана так, чтобы курсор
     * остался после того же символа, что и до чистки (не прыгал в конец).
     */
    fun sanitize(input: String, cursor: Int = input.length): Edit {
        val out = StringBuilder()
        val origin = ArrayList<Int>(input.length) // индекс исходного символа для каждого оставленного
        var hasSeparator = false
        var intDigits = 0
        var fracDigits = 0
        input.forEachIndexed { i, c ->
            when {
                c in '0'..'9' && !hasSeparator && intDigits < MAX_INT_DIGITS -> {
                    out.append(c); origin.add(i); intDigits++
                }
                c in '0'..'9' && hasSeparator && fracDigits < MAX_FRACTION_DIGITS -> {
                    out.append(c); origin.add(i); fracDigits++
                }
                (c == ',' || c == '.') && !hasSeparator -> {
                    out.append(','); origin.add(i); hasSeparator = true
                }
            }
        }
        // Ведущие нули: оставляем один, если за ним сразу запятая или больше ничего нет
        val intLen = out.indexOf(",").let { if (it < 0) out.length else it }
        var lead = 0
        while (lead < intLen - 1 && out[lead] == '0') lead++
        val text = out.substring(lead)
        val kept = origin.subList(lead, origin.size)
        val c = cursor.coerceIn(0, input.length)
        return Edit(text, kept.count { it < c })
    }

    /** Копейки → сырой текст: 2000000 → «20000», 2000050 → «20000,5», 5 → «0,05». */
    fun fromKopecks(kopecks: Long): String {
        if (kopecks == 0L) return "0"
        return BigDecimal.valueOf(kopecks).movePointLeft(2).stripTrailingZeros()
            .toPlainString().replace('.', ',')
    }

    /**
     * Сумма из текста поля в копейках. Понимает и сырой, и показанный вид:
     * пробелы (в т.ч. неразрывные), «руб» и «₽» игнорируются, запятая = точка.
     * Пустое или неразборчивое значение — `null`.
     */
    fun parseKopecks(text: String): Long? {
        val cleaned = text.replace("руб", "")
            .filter { !it.isWhitespace() && it != NBSP && it != NNBSP && it != '₽' }
            .replace(',', '.')
        if (cleaned.isEmpty() || cleaned == ".") return null
        return cleaned.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.toLong()
    }

    /** Положительная сумма в копейках или `null` (для кнопок «Сохранить»). */
    fun parsePositiveKopecks(text: String): Long? = parseKopecks(text)?.takeIf { it > 0 }

    /**
     * Показанный вид и соответствие позиций курсора.
     * [toDisplay] длиной `raw.length + 1`, [toRaw] длиной `text.length + 1`.
     */
    class Formatted(val text: String, val numberLength: Int, val toDisplay: IntArray, val toRaw: IntArray)

    /**
     * «20000,5» → «20 000,5 руб». Точка тоже показывается запятой, поэтому старые значения
     * вида «250.5» выглядят правильно ещё до первой правки. Пустой текст остаётся пустым,
     * чтобы подпись поля вела себя как обычно.
     */
    fun format(raw: String): Formatted {
        if (raw.isEmpty()) return Formatted("", 0, intArrayOf(0), intArrayOf(0))
        val sepIndex = raw.indexOfFirst { it == ',' || it == '.' }
        val intLen = if (sepIndex < 0) raw.length else sepIndex
        val out = StringBuilder()
        val toDisplay = IntArray(raw.length + 1)
        val toRawList = ArrayList<Int>(raw.length + 8)
        raw.forEachIndexed { i, c ->
            if (i in 1 until intLen && (intLen - i) % 3 == 0) {
                out.append(NBSP); toRawList.add(i) // курсор на пробеле = перед следующей цифрой
            }
            toDisplay[i] = out.length
            out.append(if (i == sepIndex) ',' else c)
            toRawList.add(i)
        }
        val numberLength = out.length
        toDisplay[raw.length] = numberLength
        out.append(SUFFIX)
        repeat(out.length - numberLength + 1) { toRawList.add(raw.length) } // суффикс и конец → конец числа
        return Formatted(out.toString(), numberLength, toDisplay, toRawList.toIntArray())
    }
}
