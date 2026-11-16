package ru.my.traty.ai

/** Сумма, найденная во фразе, и где она стоит в тексте (чтобы вырезать её из заметки). */
data class SpokenAmount(val value: Double, val range: IntRange)

/**
 * Сумма из фразы для разбора без ИИ: цифрами («731», «1 500», «2,5 тысячи») или словами
 * («семьсот тридцать один», «полторы тысячи», «две сто» = 2100). Цифры важнее слов: берётся первое число цифрами.
 */
object SpokenAmountParser {
    // пробел, NBSP (U+00A0), узкий NBSP (U+202F)
    private const val SEP = "[   ]"

    /**
     * Копейки: «83,90», «83.9» или как пишет распознавание речи «восемьдесят три девяносто» — «83—90».
     * У тире ровно две цифры вплотную: «100-200» (диапазон), «2-3 пачки» и «300 — дорого» копейками не считаются.
     */
    private const val KOPECKS = """(?:[.,]\d{1,2}|[-–—]\d{2}(?!\d))"""
    private val digitsRe = Regex("""\d{1,3}(?:$SEP\d{3})+$KOPECKS?|\d+$KOPECKS?""")
    private val kopecksDash = Regex("""[-–—]""")
    private val wordRe = Regex("""[а-яёА-ЯЁ]+""")
    private val thousandRe = Regex("""^(тысяч\p{L}*|тыщ\p{L}*|косар\p{L}*|тыс|к)$""")

    private val units = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4,
        "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15,
        "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
    )
    private val tens = mapOf(
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
        "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90,
    )
    private val hundreds = mapOf(
        "сто" to 100, "двести" to 200, "триста" to 300, "четыреста" to 400, "пятьсот" to 500,
        "шестьсот" to 600, "семьсот" to 700, "восемьсот" to 800, "девятьсот" to 900,
    )
    private val slang = mapOf("пятихатка" to 500, "пятихатку" to 500, "сотка" to 100, "сотку" to 100)

    /** «тысяча», «тыща», «косарь» в любой форме: «два косаря» → 2000, «тыщу двести» → 1200. */
    private fun isThousandWord(w: String) = w.startsWith("тысяч") || w.startsWith("тыщ") || w.startsWith("косар")

    fun find(text: String): SpokenAmount? = findDigits(text) ?: findWords(text)

    private fun findDigits(text: String): SpokenAmount? {
        val m = digitsRe.find(text) ?: return null
        var value = m.value.replace(Regex(SEP), "").replace(kopecksDash, ".").replace(',', '.').toDoubleOrNull() ?: return null
        var end = m.range.last
        // «2,5 тысячи», «3 тыс», «5к». Буква «к» — тысячи только вплотную к числу: «такси 300 к маме» — это 300
        val next = wordRe.find(text, m.range.last + 1)
        val between = next?.let { text.substring(m.range.last + 1, it.range.first) }
        val word = next?.value?.lowercase()
        val glued = word != "к" || between?.isEmpty() == true
        if (word != null && between != null && between.isBlank() && glued && thousandRe.matches(word)) {
            value *= 1000
            end = next.range.last
        }
        return SpokenAmount(value, m.range.first..end)
    }

    /** Из нескольких чисел словами берётся самое большое: «две пачки корма пятьсот» → 500. */
    private fun findWords(text: String): SpokenAmount? {
        val words = wordRe.findAll(text).toList()
        val found = mutableListOf<SpokenAmount>()
        var start = 0
        while (start < words.size) {
            val a = parseFrom(words, start)
            if (a == null) {
                start++
            } else {
                found += a
                start = words.indexOfFirst { it.range.first > a.range.last }.takeIf { it >= 0 } ?: words.size
            }
        }
        return found.maxByOrNull { it.value }
    }

    /** Читает число словами, начиная с [start]; null — там числа нет. */
    private fun parseFrom(words: List<MatchResult>, start: Int): SpokenAmount? {
        val first = words[start].value.lowercase()
        slang[first]?.let { return SpokenAmount(it.toDouble(), words[start].range) }
        if (first == "полторы" || first == "полтора") {
            val next = words.getOrNull(start + 1)
            if (next != null && thousandRe.matches(next.value.lowercase())) {
                return SpokenAmount(1500.0, words[start].range.first..next.range.last)
            }
            return null
        }
        var total = 0
        var small = 0 // текущая группа до тысячи
        var hasHundreds = false
        var hasTens = false
        var hasUnits = false
        var last = -1
        var i = start
        while (i < words.size) {
            val w = words[i].value.lowercase()
            val h = hundreds[w]
            val t = tens[w]
            val u = units[w]
            when {
                h != null -> {
                    if (hasHundreds) break
                    if (small > 0) {
                        // «две сто» → 2100: число перед сотнями — тысячи
                        total += small * 1000
                        small = 0
                        hasTens = false
                        hasUnits = false
                    }
                    small += h
                    hasHundreds = true
                }
                t != null -> {
                    if (hasTens || hasUnits) break
                    small += t
                    hasTens = true
                }
                u != null -> {
                    if (hasUnits) break
                    if (hasTens && u >= 10) break
                    small += u
                    hasUnits = true
                }
                isThousandWord(w) -> {
                    total += (if (small == 0) 1 else small) * 1000
                    small = 0
                    hasHundreds = false
                    hasTens = false
                    hasUnits = false
                }
                else -> break
            }
            last = i
            i++
        }
        if (last < 0) return null
        val value = total + small
        if (value <= 0) return null
        return SpokenAmount(value.toDouble(), words[start].range.first..words[last].range.last)
    }
}
