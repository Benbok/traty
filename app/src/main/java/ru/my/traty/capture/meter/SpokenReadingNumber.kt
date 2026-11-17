package ru.my.traty.capture.meter

import ru.my.traty.data.apartments.ReadingValues

/** Строгий разбор показания без денежных сокращений, округления и поиска «первого числа». */
internal object SpokenReadingNumber {
    private val units = listOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
        .mapIndexed { i, word -> word to i }.toMap() + mapOf("нуль" to 0, "одна" to 1, "одну" to 1, "две" to 2)
    private val teens = listOf("десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать")
        .mapIndexed { i, word -> word to i + 10 }.toMap()
    private val tens = listOf("двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
        .mapIndexed { i, word -> word to (i + 2) * 10 }.toMap()
    private val hundreds = listOf("сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот")
        .mapIndexed { i, word -> word to (i + 1) * 100 }.toMap()
    private val scales = mapOf("тысяча" to 1_000L, "тысячи" to 1_000L, "тысяч" to 1_000L,
        "миллион" to 1_000_000L, "миллиона" to 1_000_000L, "миллионов" to 1_000_000L)

    fun parse(text: String): Long? {
        val clean = text.lowercase().replace('ё', 'е').trim()
        ReadingValues.parseMilli(clean)?.let { return it }
        val parts = clean.split(Regex("""\s*(?:запятая|точка|[.,])\s*"""))
        if (parts.size !in 1..2) return null
        val whole = integer(parts[0]) ?: return null
        val fraction = if (parts.size == 2) {
            val ts = tokens(parts[1])
            val digits = parts[1].trim().takeIf { it.matches(Regex("""\d+""")) }
                ?: digitSequence(ts) ?: integer(parts[1])?.toString() ?: return null
            if (digits.length !in 1..3) return null
            digits
        } else null
        return ReadingValues.parseMilli(whole.toString() + (fraction?.let { "." + it } ?: ""))
    }

    private fun tokens(text: String) = text.trim().split(Regex("""[\s\u00a0\u202f]+""")).filter { it.isNotEmpty() }

    private fun digitSequence(ts: List<String>): String? {
        if (ts.isEmpty()) return null
        return ts.map { token ->
            units[token]?.toString() ?: token.takeIf { it.matches(Regex("""\d""")) } ?: return null
        }.joinToString("")
    }

    private fun integer(text: String): Long? {
        val ts = tokens(text)
        if (ts.isEmpty()) return null
        digitSequence(ts)?.let { return it.toLongOrNull() }
        if (ts.all { it.all(Char::isDigit) }) {
            if (ts.size == 1 || (ts.first().length in 1..3 && ts.drop(1).all { it.length == 3 })) {
                return ts.joinToString("").toLongOrNull()
            }
            return null
        }
        var total = 0L
        var lastScale = Long.MAX_VALUE
        var groupStart = 0
        try {
            ts.forEachIndexed { index, token ->
                val scale = scales[token]
                if (scale != null) {
                    if (scale >= lastScale) return null
                    val group = if (index == groupStart) 1L else group(ts.subList(groupStart, index)) ?: return null
                    if (group !in 1..999) return null
                    total = Math.addExact(total, Math.multiplyExact(group, scale))
                    lastScale = scale
                    groupStart = index + 1
                }
            }
            val tail = if (groupStart == ts.size) 0L else group(ts.drop(groupStart)) ?: return null
            if (lastScale != Long.MAX_VALUE && tail >= lastScale) return null
            return Math.addExact(total, tail)
        } catch (_: ArithmeticException) {
            return null
        }
    }

    private fun group(ts: List<String>): Long? {
        if (ts.size == 1 && ts[0].all(Char::isDigit)) return ts[0].toLongOrNull()
        var value = 0L
        var lastRank = 4
        ts.forEach { token ->
            val number = hundreds[token] ?: tens[token] ?: teens[token] ?: units[token] ?: return null
            val rank = when { number >= 100 -> 3; number >= 10 -> 2; else -> 1 }
            if (rank >= lastRank || (number == 0 && ts.size > 1)) return null
            value += number
            lastRank = if (number in 10..19) 0 else rank
        }
        return value
    }
}
