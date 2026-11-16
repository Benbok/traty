package ru.my.traty.capture

import ru.my.traty.ai.MoneyAmounts
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.ShoppingListFormat
import kotlin.math.abs

/**
 * Сверка суммы от ИИ с тем, что сказано во фразе. ИИ мог перепутать число с количеством («2 билета 3000»),
 * сократить «две сто» или придумать сумму; приложение само ищет числа во фразе ([MoneyAmounts]).
 * Сумма подходит, если это одно из названных чисел или сумма нескольких из них (список покупок с ценами).
 * Не подошла — трата не сохраняется молча, человек увидит окно проверки с пояснением.
 */
object AmountCheck {
    private const val EPS = 0.005

    /** Сколько названных чисел перебирать для сумм подмножеств: 2^10 вариантов — мгновенно. */
    private const val MAX_SUBSET_NUMBERS = 10

    /** Записи, сумму которых во фразе подтвердить не удалось. Записи без суммы (покупка в кредит) не проверяются. */
    fun mismatches(entries: List<ParsedEntry>, rawText: String): List<ParsedEntry> {
        val named = namedAmounts(rawText)
        return entries.filter { it.amount > 0 && !supported(it.amount, named) }
    }

    /** Названные суммы. К тому, что находит [MoneyAmounts], добавлены «две с половиной тысячи», «миллион», «две сотни». */
    private fun namedAmounts(text: String): List<Double> = MoneyAmounts.all(text).map { it.value } + extras(text)

    private val small = mapOf(
        "один" to 1.0, "одна" to 1.0, "два" to 2.0, "две" to 2.0, "три" to 3.0, "четыре" to 4.0,
        "пять" to 5.0, "шесть" to 6.0, "семь" to 7.0, "восемь" to 8.0, "девять" to 9.0, "десять" to 10.0,
    )
    private val halfRe = Regex("""(?<![\p{L}\d])(\d+|\p{L}+)\s+с\s+половиной\s+(тысяч\p{L}*|тыщ\p{L}*|миллион\p{L}*)""", RegexOption.IGNORE_CASE)
    private val millionRe = Regex("""(?<![\p{L}\d])(?:(\d+|\p{L}+)\s+)?миллион\p{L}*""", RegexOption.IGNORE_CASE)
    private val hundredsRe = Regex("""(?<![\p{L}\d])(\d+|\p{L}+)\s+сотн\p{L}*""", RegexOption.IGNORE_CASE)

    private fun count(word: String): Double? = word.toDoubleOrNull() ?: small[word.lowercase()]

    private fun extras(text: String): List<Double> {
        val out = mutableListOf<Double>()
        halfRe.findAll(text).forEach { m ->
            val n = count(m.groupValues[1]) ?: return@forEach
            val unit = if (m.groupValues[2].lowercase().startsWith("миллион")) 1_000_000.0 else 1_000.0
            out += (n + 0.5) * unit
        }
        millionRe.findAll(text).forEach { m ->
            val w = m.groupValues[1].lowercase()
            out += when {
                w.isEmpty() -> 1_000_000.0
                w.startsWith("полтора") || w.startsWith("полторы") -> 1_500_000.0
                else -> (count(w) ?: 1.0) * 1_000_000.0
            }
        }
        hundredsRe.findAll(text).forEach { m -> count(m.groupValues[1])?.let { out += it * 100 } }
        return out
    }

    /** Пояснение для окна проверки; null — всё сошлось. */
    fun notice(mismatched: List<ParsedEntry>, rawText: String): String? {
        val first = mismatched.firstOrNull() ?: return null
        val named = namedAmounts(rawText).distinct()
        val said = if (named.isEmpty()) "во фразе не нашлось суммы" else
            "во фразе названо: " + named.joinToString(", ") { "${ShoppingListFormat.rub(it)} ₽" }
        val head = if (mismatched.size == 1) "Сумма ${ShoppingListFormat.rub(first.amount)} ₽" else "Некоторые суммы"
        return "$head не сходится с фразой ($said). Проверьте сумму."
    }

    private fun supported(amount: Double, named: List<Double>): Boolean {
        if (named.isEmpty()) return false
        if (named.any { close(it, amount) }) return true
        if (close(named.sum(), amount)) return true
        if (named.size > MAX_SUBSET_NUMBERS) return false
        // Сумма части названных чисел: «рыба 500, хлеб 80, и ещё сок 120» — трата на рыбу и хлеб
        for (mask in 1 until (1 shl named.size)) {
            var sum = 0.0
            for (i in named.indices) if (mask and (1 shl i) != 0) sum += named[i]
            if (close(sum, amount)) return true
        }
        return false
    }

    private fun close(a: Double, b: Double) = abs(a - b) < EPS
}
