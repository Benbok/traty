package ru.my.traty.data.items

import ru.my.traty.data.search.RussianStemmer
import ru.my.traty.data.search.SearchText

/**
 * Названия товаров: «рыбу», «Рыба» и «рыбы» — один товар, «чехол для телефона» и «Чехол для телефонов» тоже,
 * «Коктейль 200 мл» и «коктейль 450мл» — тоже (фасовка в ключ не входит, её считает [ItemMeasure]).
 * Сравнение без регистра, «ё», знаков препинания и окончаний (основа слова, как в поиске).
 */
object ItemNames {
    private val split = Regex("""[^\p{L}\p{N}]+""")

    /** Ключ для подсчёта по товарам. Пустая строка — названия нет. */
    fun key(name: String): String =
        SearchText.fold(PackSize.strip(name)).split(split).filter { it.isNotEmpty() }.joinToString(" ") { RussianStemmer.stem(it) }

    private val percentage = Regex("""\d+(?:[.,]\d+)?\s*%""")
    private val processing = Regex("""\b(?:ультрапастеризованн|пастеризованн|стерилизованн)\p{L}*\b""", RegexOption.IGNORE_CASE)

    /** Для рейтинга объединяем фасовки и технологические уточнения одного вида товара. */
    fun familyName(name: String): String {
        val label = processing.replace(percentage.replace(PackSize.strip(name), " "), " ")
        return normalize(label) ?: name
    }

    fun familyKey(name: String): String =
        key(familyName(name)).split(' ').filter { it != "с" }.sorted().joinToString(" ").ifBlank { key(name) }

    /** Как сохранить название: без лишних пробелов и с заглавной буквы. null — названия нет. */
    fun normalize(raw: String?): String? {
        val s = raw?.replace(Regex("""\s+"""), " ")?.trim(' ', ',', '.', ';', ':', '-', '—')?.trim() ?: return null
        if (s.isEmpty()) return null
        return s.take(MAX_NAME).trim().replaceFirstChar { it.uppercaseChar() }
    }

    const val MAX_NAME = 80
}
