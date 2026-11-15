package ru.my.traty.data.debt

import ru.my.traty.data.Places

/**
 * Поиск открытой покупки для доплаты: «за холодильник Озону плюс 5000» → «Холодильник, Ozon».
 * Ищет приложение, а не ИИ: ИИ список покупок не получает.
 *
 * Слова сравниваются с учётом окончаний («холодильник» = «холодильника» = «холодильнику»).
 * Совпадение по названию важнее совпадения по магазину. Только магазин без названия товара
 * подходит, лишь когда открытая покупка в этом магазине одна.
 */
object DebtMatcher {
    private val word = Regex("""\p{L}+""")
    private val stopWords = setOf("за", "на", "для", "по", "в", "во", "и", "или", "плюс", "ещё", "еще", "доплата", "доплатил", "доплатила")

    /** Подходящие покупки, лучшие первыми. Пустой список — это обычная трата. */
    fun find(open: List<Debt>, note: String?, place: String?): List<Debt> {
        val active = open.filter { it.state == DebtState.ACTIVE }
        val words = words(note)
        val placeKey = Places.key(place)
        val scored = active.mapNotNull { debt ->
            val titleHits = words(debt.title).count { t -> words.any { sameWord(it, t) } }
            val samePlace = placeKey.isNotEmpty() && Places.key(debt.place) == placeKey
            when {
                titleHits > 0 -> debt to titleHits * 2 + (if (samePlace) 1 else 0)
                samePlace && words.isEmpty() -> debt to 1
                else -> null
            }
        }
        val onlyPlace = scored.filter { it.second == 1 }
        // По одному магазину без названия товара угадываем, только если выбор однозначный
        val usable = if (scored.size == onlyPlace.size && onlyPlace.size > 1) emptyList() else scored
        return usable.sortedWith(compareByDescending<Pair<Debt, Int>> { it.second }.thenByDescending { it.first.createdAt })
            .map { it.first }
    }

    internal fun words(text: String?): List<String> =
        word.findAll(text.orEmpty().lowercase().replace('ё', 'е')).map { it.value }
            .filter { it.length >= 3 && it !in stopWords }
            .toList()

    /** Общее начало не короче слова без двух последних букв (и не короче 4 букв). */
    internal fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val common = a.zip(b).takeWhile { (x, y) -> x == y }.size
        return common >= maxOf(4, minOf(a.length, b.length) - 2)
    }
}
