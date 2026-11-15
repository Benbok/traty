package ru.my.traty.ai

import java.time.LocalDate

/** Один товар из списка покупок. [price] — цена в рублях, если её назвали. */
data class ListedItem(val name: String, val price: Double? = null)

/**
 * Список покупок из одной фразы: «в продуктовом на 2500 купил рыбу, консерву, хлеб» — это одна трата,
 * а товары идут в её заметку. Позже, когда появится фото чека, те же позиции лягут
 * в отдельную таблицу, связанную с тратой; пока они живут только в заметке.
 *
 * [total] — общая сумма, названная во фразе. [pricesCoverPhrase] — все числа фразы оказались ценами товаров,
 * значит, итог можно посчитать сложением (считает приложение, а не ИИ).
 */
data class ShoppingList(val items: List<ListedItem>, val total: Double?, val pricesCoverPhrase: Boolean = false) {
    /** Сумма для траты: названный итог, иначе сумма цен, если все числа фразы — цены товаров. */
    val amount: Double?
        get() = total ?: if (pricesCoverPhrase) items.sumOf { it.price ?: 0.0 } else null

    val hasPrices: Boolean get() = items.any { it.price != null }

    fun note(): String = ShoppingListFormat.note(items)
}

object ShoppingListFormat {
    /** «рыбу 500 ₽, консерву, хлеб 80 ₽» — товары через запятую, как сказаны, с ценой после товара. */
    fun note(items: List<ListedItem>): String =
        items.joinToString(", ") { i -> i.price?.let { "${i.name} ${rub(it)} ₽" } ?: i.name }

    /** 500 → «500», 1500 → «1 500», 99.5 → «99,50» (неразрывный пробел между тысячами). */
    fun rub(value: Double): String {
        val kop = Math.round(value * 100)
        val whole = (kop / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val rest = kop % 100
        return if (rest == 0L) whole else "$whole,${rest.toString().padStart(2, '0')}"
    }
}

/**
 * Общая сумма покупки во фразе: «всего 2500», «итого 2500», «чек на 2500», «на 2500 купил…».
 * «на 6 месяцев», «на Озоне» суммой не считаются.
 */
object SpokenTotal {
    private val strong = Regex(
        """(?<![\p{L}])(?:всего|итого|в\s+сумме|на\s+сумму|общ\p{L}*\s+сумм\p{L}*|чек\s+на|за\s+вс[её]|вместе)(?![\p{L}])[\s:—-]*(?:(?:на|вышло|получилось|вышел)\s+)*""",
        RegexOption.IGNORE_CASE,
    )
    private val naBefore = Regex("""(?<![\p{L}])на\s+$""", RegexOption.IGNORE_CASE)
    private val verbAfter = Regex(
        """^\s*(?:(?:руб\p{L}*|р\.|₽)\s*)?(?:купил\p{L}*|взял\p{L}*|набрал\p{L}*|закупил\p{L}*|накупил\p{L}*|затарил\p{L}*)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    /** Сумма вместе со словом перед ней: диапазон годится, чтобы вырезать «на 2500» из заметки. */
    fun find(text: String): SpokenAmount? {
        strong.findAll(text).toList().asReversed().forEach { m ->
            amountAt(text, m.range.last + 1)?.let { return SpokenAmount(it.value, m.range.first..it.range.last) }
        }
        // «в продуктовом на 2500 купил рыбу, консерву»: первое число после «на», а за ним «купил» или других чисел нет
        val money = MoneyAmounts.all(text)
        val first = money.firstOrNull() ?: return null
        val na = naBefore.find(text.substring(0, first.range.first)) ?: return null
        val alone = money.size == 1
        if (!alone && !verbAfter.containsMatchIn(text.substring(first.range.last + 1))) return null
        return SpokenAmount(first.value, na.range.first..first.range.last)
    }

    /** Число, которое стоит сразу с позиции [from] (пробелы пропускаются). */
    private fun amountAt(text: String, from: Int): SpokenAmount? {
        val start = (from until text.length).firstOrNull { !text[it].isWhitespace() } ?: return null
        // Окно в несколько слов: «двадцать пять тысяч триста» — четыре слова
        val words = Regex("""\S+""").findAll(text, start).take(5).toList()
        val end = words.lastOrNull()?.range?.last ?: return null
        val a = MoneyAmounts.all(text.substring(start, end + 1)).firstOrNull() ?: return null
        if (a.range.first != 0) return null
        return SpokenAmount(a.value, (a.range.first + start)..(a.range.last + start))
    }
}

/** Все денежные суммы во фразе по порядку. «2 пачки», «3 кг» — это количество, а не деньги. */
internal object MoneyAmounts {
    private val unitAfter = Regex(
        """^\s*(?:шт\p{L}*|штук\p{L}*|пач\p{L}*|бутыл\p{L}*|банк\p{L}*|банок|кг|кило\p{L}*|грамм\p{L}*|гр?|л|литр\p{L}*|упаков\p{L}*|пакет\p{L}*|кус\p{L}*|коробк\p{L}*|коробок|десят\p{L}*|месяц\p{L}*|недел\p{L}*|год\p{L}*|лет|дн\p{L}*|процент\p{L}*|%|раз\p{L}*)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    fun all(text: String): List<SpokenAmount> {
        val out = mutableListOf<SpokenAmount>()
        val masked = StringBuilder(text)
        while (true) {
            val a = SpokenAmountParser.find(masked.toString()) ?: break
            for (i in a.range) masked.setCharAt(i, ' ')
            val after = text.substring(a.range.last + 1)
            if (unitAfter.containsMatchIn(after) || smallWordBeforeItem(text, a, after)) continue
            out += a
        }
        return out.sortedBy { it.range.first }
    }

    private val nextWord = Regex("""^\s+(\p{L}+)""")
    private val currencyWord = Regex("""руб\p{L}*|р|тысяч\p{L}*|тыщ\p{L}*""")

    /** «одну рыбу», «две булки» — количество словами перед товаром, а не цена. */
    private fun smallWordBeforeItem(text: String, a: SpokenAmount, after: String): Boolean {
        if (text[a.range.first].isDigit() || a.value >= 20) return false
        val next = nextWord.find(after)?.groupValues?.get(1)?.lowercase() ?: return false
        return !currencyWord.matches(next)
    }
}

/**
 * Список товаров через запятую: «в продуктовом на 2500 купил рыбу, консерву, ещё что-то» →
 * товары «рыбу», «консерву», итог 2500. Нужно хотя бы два товара; без запятых списка нет
 * (тогда заметка остаётся как есть).
 */
object ShoppingListParser {
    private val separators = Regex("""\s*[,;\n]\s*""")
    private val andSplit = Regex("""\s+и\s+""", RegexOption.IGNORE_CASE)
    private val currency = Regex("""(?<![\p{L}])(?:руб(?:лей|ля|ль)?|р\.)(?![\p{L}])|₽""", RegexOption.IGNORE_CASE)
    private val fillers = setOf(
        "в", "во", "на", "за", "по", "из", "и", "а", "ещё", "еще", "также", "плюс", "это", "вот", "там", "ну", "типа",
        "купил", "купила", "купили", "взял", "взяла", "взяли", "набрал", "набрала", "закупил", "закупила",
        "накупил", "накупила", "потратил", "потратила", "брал", "брала",
    )
    private val vague = setOf("что-то", "чтото", "что-нибудь", "всякое", "всякого", "разное", "прочее", "так далее", "т.д", "т. д", "и т.д")

    /** [places] — названия мест из ответа ИИ, их нужно убрать из товаров («в Пятёрочке»). */
    fun find(text: String, places: List<String> = emptyList()): ShoppingList? {
        if (!separators.containsMatchIn(text)) return null
        val total = SpokenTotal.find(text)
        val cuts = listOfNotNull(
            total?.range,
            ShopTypes.find(text)?.range,
            Marketplaces.find(text)?.range,
            // нужен только диапазон «вчера» / «в среду», сам день здесь не важен
            SpokenDateParser.find(text, LocalDate.of(2000, 1, 10))?.range,
        ) + places.flatMap { placeRanges(text, it) }
        val masked = mask(text, cuts)
        val pieces = masked.split(separators).let { p -> if (p.size >= 2) p.flatMap { it.split(andSplit) } else p }
        val items = pieces.mapNotNull(::item)
        if (items.size < 2) return null
        val money = MoneyAmounts.all(masked).size
        val covered = items.all { it.price != null } && items.size == money
        return ShoppingList(items, total?.value, pricesCoverPhrase = covered)
    }

    private fun item(piece: String): ListedItem? {
        val money = MoneyAmounts.all(piece)
        val price = money.lastOrNull()?.value
        var name = mask(piece, money.map { it.range }).replace(currency, " ")
        name = tidy(name) ?: return null
        if (name.lowercase() in vague) return null
        return ListedItem(name, price)
    }

    /** Убирает служебные слова по краям: «в купил рыбу за» → «рыбу». Слова в середине не трогает. */
    fun tidy(text: String): String? {
        val words = text.replace(Regex("""\s+"""), " ").trim(' ', ',', '.', ';', ':', '-', '—').split(' ').toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in fillers) words.removeAt(0)
        while (words.isNotEmpty() && words.last().lowercase() in fillers) words.removeAt(words.size - 1)
        return words.joinToString(" ").trim(' ', ',', '.', ';', ':', '-', '—').ifBlank { null }
    }

    private fun mask(text: String, ranges: List<IntRange>): String {
        val sb = StringBuilder(text)
        ranges.forEach { r -> for (i in r) if (i in sb.indices) sb.setCharAt(i, ' ') }
        return sb.toString()
    }

    /** «Пятёрочка» находит «в пятерочке»: основа без окончания, «е» и «ё» не различаются. */
    private fun placeRanges(text: String, place: String): List<IntRange> {
        val p = place.trim()
        if (p.length < 3) return emptyList()
        val stem = p.dropLast(if (p.length >= 6) 2 else if (p.length >= 4) 1 else 0)
        val pattern = stem.map { c ->
            when (c.lowercaseChar()) {
                'е', 'ё' -> "[её]"
                else -> Regex.escape(c.toString())
            }
        }.joinToString("")
        val re = Regex("""(?<![\p{L}])(?:(?:в|во|из|на)\s+)?$pattern\p{L}*(?![\p{L}])""", RegexOption.IGNORE_CASE)
        return re.findAll(text).map { it.range }.toList()
    }
}
