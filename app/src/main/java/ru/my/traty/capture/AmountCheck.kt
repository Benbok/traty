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
        val plain = plainNumbers(rawText)
        return entries.filter { entry ->
            if (entry.amount <= 0) return@filter false
            val known = named + pricedByAi(entry, plain, named, rawText)
            !supported(entry.amount, known) && !itemPricesAddUp(entry, known)
        }
    }

    /**
     * Сумма совпадает с суммой цен товаров от ИИ, и каждая такая цена есть во фразе ([known]: дословно, с единицей
     * или копейки двумя числами). Перебор сумм частей ([supported]) тут не нужен, и он не упирается
     * в предел в [MAX_SUBSET_NUMBERS] чисел, когда в длинном списке покупок цен много. Одна неподтверждённая
     * цена (ИИ что-то выдумал) — и сумма остаётся под вопросом.
     */
    private fun itemPricesAddUp(entry: ParsedEntry, known: List<Double>): Boolean {
        val prices = entry.items.orEmpty().mapNotNull { it.price }
        if (prices.size < 2 || prices.size != entry.items.orEmpty().size) return false
        if (!prices.all { p -> known.any { close(it, p) } }) return false
        return close(prices.sum(), entry.amount)
    }

    private val plainNumberRe = Regex("""(?<![\d.,])\d{1,7}(?:[.,]\d{1,2})?(?![\d])""")

    /** Все числа цифрами, вместе с теми, что стоят перед единицей («99г», «2 шт»). */
    private fun plainNumbers(text: String): List<Double> =
        plainNumberRe.findAll(text).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()

    /**
     * Распознавание речи иногда пишет цену с единицей: «горошек 99г». Такое число считаем деньгами только тогда,
     * когда ИИ сам назвал его ценой товара в этой трате, а во фразе оно стоит дословно. «900 г» у гречки
     * (такой цены у товаров нет) остаётся весом.
     */
    private fun pricedByAi(entry: ParsedEntry, plain: List<Double>, named: List<Double>, text: String): List<Double> {
        val prices = entry.items.orEmpty().mapNotNull { it.price }
        if (prices.isEmpty()) return emptyList()
        val withUnit = plain.filter { n -> prices.any { close(it, n) } && named.none { close(it, n) } }
        return withUnit + prices.filter { splitIntoTwoNumbers(it, text) }
    }

    /**
     * Цена с копейками, которую распознавание речи записало не как «231,29»: двумя числами подряд («231, 29», «129 99»)
     * или слитно («14998» → 149,98). Рубли и две цифры копеек должны стоять во фразе рядом, через пробел, запятую,
     * точку или тире, или слитно: только такая цена от ИИ подтверждается, выдуманная — нет.
     * Слитная запись принимается только для цен от 100 ₽ (минимум пять цифр): «1250» — это тысяча двести
     * пятьдесят, а не 12,50.
     */
    private fun splitIntoTwoNumbers(price: Double, text: String): Boolean =
        separatedKopecks(price, text) || gluedKopecks(price, text)

    /** «231, 29», «129 99», «83—90»: рубли и копейки двумя числами через разделитель — чтение однозначное. */
    private fun separatedKopecks(price: Double, text: String): Boolean {
        val total = Math.round(price * 100)
        val kopecks = total % 100
        if (kopecks == 0L) return false
        val rubles = total / 100
        val twoDigits = "%02d".format(kopecks)
        return Regex("""(?<![\d.,])$rubles[\s,.\-–—]{1,3}$twoDigits(?!\d)""").containsMatchIn(text)
    }

    /** «14998» → 149,98: слитная запись — чтение получается угадыванием, одинаковое число могло быть и целыми рублями. */
    private fun gluedKopecks(price: Double, text: String): Boolean {
        val total = Math.round(price * 100)
        val kopecks = total % 100
        if (kopecks == 0L) return false
        val glued = "${total / 100}${"%02d".format(kopecks)}"
        return glued.length >= 5 && Regex("""(?<![\d.,])$glued(?!\d)""").containsMatchIn(text)
    }

    /**
     * Цены товаров от ИИ, которые во фразе не стоят «как есть» и были угаданы: слитно («11490» → 114,90) или с прилипшей
     * единицей («139л» → 139). ИИ обычно угадывает верно, но сохранять такое без проверки человеком нельзя.
     * Цены через разделитель («231, 29», «83—90») сюда не попадают.
     */
    fun guessedPrices(entries: List<ParsedEntry>, rawText: String): List<Double> {
        val named = namedAmounts(rawText)
        val plain = plainNumbers(rawText)
        return entries.flatMap { e -> e.items.orEmpty().mapNotNull { it.price } }.distinct().filter { p ->
            named.none { close(it, p) } && !separatedKopecks(p, rawText) &&
                (gluedKopecks(p, rawText) || plain.any { close(it, p) })
        }
    }

    /** Пояснение для окна проверки про угаданные цены; null — угадывать ничего не пришлось. */
    fun guessedPricesNotice(entries: List<ParsedEntry>, rawText: String): String? {
        val guessed = guessedPrices(entries, rawText)
        if (guessed.isEmpty()) return null
        val list = guessed.joinToString(", ") { "${ShoppingListFormat.rub(it)} ₽" }
        return "Цены по записи речи пришлось угадывать ($list). Проверьте, что они верные."
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
