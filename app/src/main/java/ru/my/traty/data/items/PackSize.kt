package ru.my.traty.data.items

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Фасовка одной штуки товара: «200 мл», «0,5 л», «500 г», «1 кг».
 * [milli] — число в тысячных, как у количества: 200 мл = 200 000, 0,5 л = 500.
 * [unit] — «мл», «л», «г» или «кг».
 */
data class Pack(val milli: Long, val unit: String) {
    /** Сколько это в граммах или миллилитрах (в тысячных) и что это: вес или объём. */
    fun base(): Measure? = ItemMeasure.base(milli, unit)

    /** «200 мл», «0,5 л». */
    fun text(): String = "${ItemMeasure.number(milli)} $unit"
}

/** Разбор фасовки из текста: из названия товара в чеке («Коктейль молочный 2% 200мл») или из фразы. */
object PackSize {
    // «200мл», «0,5 л», «1.5L», «500 гр», «1 кг», «6х200мл». После единицы не должно идти буквы: «1 лимон» — не литр
    private val token = Regex(
        """(?<![\d.,]|[^\P{L}xхXХ])(\d+(?:[.,]\d+)?)\s*(мл|ml|л|l|литр\p{L}*|кг|kg|килограмм\p{L}*|гр|г|g|грамм\p{L}*)\.?(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE,
    )

    /** Фасовка в тексте, если она там одна. Две разные («2 л ... 500 г») — непонятно, какая, null. */
    fun find(text: String?): Pack? {
        if (text.isNullOrBlank()) return null
        val found = token.findAll(text).mapNotNull { pack(it) }.distinct().toList()
        return found.singleOrNull()
    }

    /** Разбор поля «Фасовка», которое человек ввёл сам: «200 мл», «0,5л», «500 г». */
    fun parse(text: String?): Pack? {
        val m = token.find(text?.trim().orEmpty()) ?: return null
        return pack(m)
    }

    /** Название без фасовки: «Коктейль молочный 200мл» → «Коктейль молочный». Если остаётся пусто — исходное. */
    fun strip(name: String): String {
        if (find(name) == null) return name
        val cut = token.replace(name, " ").replace(Regex("""\s+"""), " ").trim(' ', ',', '.', ';', ':', '-', '—', '/')
        return cut.ifBlank { name }
    }

    /** Есть ли в тексте хоть одна фасовка (для «один товар с весом» в голосовой фразе). */
    fun mentioned(text: String?): Boolean = !text.isNullOrBlank() && token.containsMatchIn(text)

    private fun pack(m: MatchResult): Pack? {
        val n = m.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
        val unit = unitOf(m.groupValues[2]) ?: return null
        val milli = n.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toLong()
        if (milli <= 0 || milli > MAX_MILLI) return null
        return Pack(milli, unit)
    }

    internal fun unitOf(raw: String): String? {
        val u = raw.lowercase().trimEnd('.')
        return when {
            u == "мл" || u == "ml" -> "мл"
            u == "л" || u == "l" || u.startsWith("литр") -> "л"
            u == "кг" || u == "kg" || u.startsWith("килограмм") -> "кг"
            u == "г" || u == "гр" || u == "g" || u.startsWith("грамм") -> "г"
            else -> null
        }
    }

    /** 100 000 единиц: больше в одной упаковке не бывает, скорее это цена или код. */
    private val MAX_MILLI = BigDecimal(100_000).movePointRight(3).toLong()
}
