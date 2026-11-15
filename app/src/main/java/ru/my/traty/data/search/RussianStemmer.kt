package ru.my.traty.data.search

/**
 * Основа русского слова без окончания (алгоритм Snowball для русского языка, упрощённый перенос).
 * Нужна, чтобы «кошке», «кошки» и «кошкой» искались как одно слово: все дают «кошк».
 * Слово ожидается строчными буквами, с «е» вместо «ё» (см. [SearchText.fold]).
 * Слова не на кириллице (латиница, цифры) возвращаются как есть.
 */
internal object RussianStemmer {

    private const val VOWELS = "аеиоуыэюя"

    private val perfectiveGerund1 = listOf("в", "вши", "вшись")
    private val perfectiveGerund2 = listOf("ив", "ивши", "ившись", "ыв", "ывши", "ывшись")
    private val adjective = listOf(
        "ее", "ие", "ые", "ое", "ими", "ыми", "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом",
        "его", "ого", "ему", "ому", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею",
    )
    private val participle1 = listOf("ем", "нн", "вш", "ющ", "щ")
    private val participle2 = listOf("ивш", "ывш", "ующ")
    private val reflexive = listOf("ся", "сь")
    private val verb1 = listOf("ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно")
    private val verb2 = listOf(
        "ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен",
        "ило", "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю",
    )
    private val noun = listOf(
        "а", "ев", "ов", "ие", "ье", "е", "иями", "ями", "ами", "еи", "ии", "и", "ией", "ей", "ой", "ий", "й",
        "иям", "ям", "ием", "ем", "ам", "ом", "о", "у", "ах", "иях", "ях", "ы", "ь", "ию", "ью", "ю", "ия", "ья", "я",
    )
    private val superlative = listOf("ейше", "ейш")
    private val derivational = listOf("ость", "ост")

    fun stem(word: String): String {
        if (word.none { it in 'а'..'я' }) return word
        val rv = word.indexOfFirst { it in VOWELS }.let { if (it < 0) return word else it + 1 }
        val r2 = regionAfter(word, regionAfter(word, 0))
        val w = StringBuilder(word)

        // Шаг 1: деепричастие, иначе возвратная частица + прилагательное / глагол / существительное
        if (!removeGrouped(w, rv, perfectiveGerund1, perfectiveGerund2)) {
            removeLongest(w, rv, reflexive)
            if (removeLongest(w, rv, adjective)) {
                removeGrouped(w, rv, participle1, participle2)
            } else if (!removeGrouped(w, rv, verb1, verb2)) {
                removeLongest(w, rv, noun)
            }
        }
        // Шаг 2
        if (w.length > rv && w.endsWith("и")) w.setLength(w.length - 1)
        // Шаг 3: словообразовательный суффикс только в R2
        removeLongest(w, r2, derivational)
        // Шаг 4
        when {
            w.length - 2 >= rv && w.endsWith("нн") -> w.setLength(w.length - 1)
            removeLongest(w, rv, superlative) -> if (w.length - 2 >= rv && w.endsWith("нн")) w.setLength(w.length - 1)
            w.length > rv && w.endsWith("ь") -> w.setLength(w.length - 1)
        }
        return w.toString()
    }

    /** Начало области R1 по Snowball: после первой согласной, которая идёт за гласной. */
    private fun regionAfter(word: String, from: Int): Int {
        var i = from
        while (i < word.length && word[i] !in VOWELS) i++
        while (i < word.length && word[i] in VOWELS) i++
        return if (i < word.length) i + 1 else word.length
    }

    /** Убирает самое длинное окончание из [endings], если оно целиком в области от [region]. */
    private fun removeLongest(w: StringBuilder, region: Int, endings: List<String>): Boolean {
        val e = endings.filter { w.length - it.length >= region && w.endsWith(it) }.maxByOrNull { it.length } ?: return false
        w.setLength(w.length - e.length)
        return true
    }

    /** Окончания группы 1 убираются только после «а» или «я», окончания группы 2 — после любой буквы. */
    private fun removeGrouped(w: StringBuilder, region: Int, group1: List<String>, group2: List<String>): Boolean {
        val g1 = group1.filter {
            val start = w.length - it.length
            start - 1 >= region && w.endsWith(it) && (w[start - 1] == 'а' || w[start - 1] == 'я')
        }
        val g2 = group2.filter { w.length - it.length >= region && w.endsWith(it) }
        val e = (g1 + g2).maxByOrNull { it.length } ?: return false
        w.setLength(w.length - e.length)
        return true
    }
}
