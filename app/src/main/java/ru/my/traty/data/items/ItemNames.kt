package ru.my.traty.data.items

import ru.my.traty.data.search.RussianStemmer
import ru.my.traty.data.search.SearchText

/**
 * Названия товаров: «рыбу», «Рыба» и «рыбы» — один товар, «чехол для телефона» и «Чехол для телефонов» тоже.
 * Сравнение без регистра, «ё», знаков препинания и окончаний (основа слова, как в поиске).
 */
object ItemNames {
    private val split = Regex("""[^\p{L}\p{N}]+""")

    /** Ключ для подсчёта по товарам. Пустая строка — названия нет. */
    fun key(name: String): String =
        SearchText.fold(name).split(split).filter { it.isNotEmpty() }.joinToString(" ") { RussianStemmer.stem(it) }

    /** Как сохранить название: без лишних пробелов и с заглавной буквы. null — названия нет. */
    fun normalize(raw: String?): String? {
        val s = raw?.replace(Regex("""\s+"""), " ")?.trim(' ', ',', '.', ';', ':', '-', '—')?.trim() ?: return null
        if (s.isEmpty()) return null
        return s.take(MAX_NAME).trim().replaceFirstChar { it.uppercaseChar() }
    }

    const val MAX_NAME = 80
}
