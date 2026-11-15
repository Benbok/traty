package ru.my.traty.data.search

/**
 * Одно и то же название, написанное по-разному: «Ozon» и «Озон», «WB», «ВБ» и «Wildberries».
 * Голосом говорят по-русски, а ИИ записывает площадку латиницей (см. ai/EntryParsing.kt),
 * поэтому поиск считает такие слова одним.
 *
 * Слова здесь строчные, с «е» вместо «ё», по одному слову без пробелов.
 * Русские написания проверяются и по основе: «озоне», «вайлдберриза» тоже подходят.
 */
internal object SearchAliases {

    private val GROUPS: List<Set<String>> = listOf(
        setOf("ozon", "озон"),
        setOf("wildberries", "wb", "вб", "вайлдберриз", "вайлдбериз", "валдберриз", "валберис", "вайлдберис", "вилдберриз"),
        setOf("aliexpress", "али", "алиэкспресс", "алиэкспрес"),
        setOf("megamarket", "мегамаркет", "сбермегамаркет"),
        setOf("yandex", "яндекс"),
        setOf("market", "маркет"),
        setOf("lamoda", "ламода"),
        setOf("avito", "авито"),
        setOf("joom", "джум"),
        setOf("shein", "шеин"),
        setOf("kazanexpress", "казаньэкспресс"),
        setOf("magnit", "магнит"),
        setOf("samokat", "самокат"),
        setOf("lavka", "лавка"),
        setOf("kuper", "купер"),
        setOf("ikea", "икеа"),
        setOf("leroy", "леруа"),
        setOf("merlin", "мерлен"),
        setOf("dns", "днс"),
        setOf("mvideo", "мвидео"),
    )

    /** Слово (или основа русского слова) → номер группы. */
    private val INDEX: Map<String, Int> = buildMap {
        GROUPS.forEachIndexed { i, g ->
            g.forEach { w ->
                put(w, i)
                RussianStemmer.stem(w).takeIf { it.length >= 3 }?.let { put(it, i) }
            }
        }
    }

    /** Номер группы написаний для слова или null, если слово не из списка. */
    fun groupOf(word: String): Int? =
        INDEX[word] ?: RussianStemmer.stem(word).takeIf { it.length >= 3 }?.let { INDEX[it] }
}
