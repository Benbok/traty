package ru.my.traty.data.search

import java.util.Locale

/**
 * Поиск словами, как говорит человек: «лекарство кошке» находит заметку
 * «лекарство для кошки Чуни от блох». Работает без сети и без ИИ.
 *
 * Правила:
 * - регистр и «ё» не важны;
 * - предлоги и союзы в запросе («для», «от», «с», «и»…) пропускаются;
 * - порядок слов не важен, но каждое слово запроса должно найтись в тексте записи;
 * - слово находится, если оно входит в слово текста («супер» → «Супермаркет», «бензи» → «Бензин»)
 *   (слово из одной-двух букв — только в начале слова) или у них одна основа («кошке» → «кошки», «лекарства» → «лекарство»).
 */
object SearchText {

    private val RU: Locale = Locale.forLanguageTag("ru")
    private val SPLIT = Regex("""[^\p{L}\p{N}]+""")

    /** Служебные слова, по которым искать бессмысленно. */
    private val STOP_WORDS = setOf(
        "в", "во", "на", "из", "у", "за", "по", "до", "от", "ото", "со", "с", "к", "ко", "о", "об", "обо",
        "для", "при", "про", "без", "под", "над", "через", "и", "а", "или", "но", "же", "ли",
        // «траты на холодильник»: слово «траты» само по себе ничего не уточняет
        "трата", "траты", "трат", "тратам", "тратах", "потратил", "потратила", "потратили",
    )

    /** Строчные буквы, «ё» → «е». */
    fun fold(s: String): String = s.lowercase(RU).replace('ё', 'е')

    /** Значимые слова запроса. Если в запросе одни предлоги («с»), ищем по ним, чтобы поиск не показывал всё. */
    fun queryWords(query: String): List<String> {
        val all = words(query)
        return all.filter { it !in STOP_WORDS }.ifEmpty { all }
    }

    /** Все слова запроса нашлись среди слов [texts] (null пропускаются). Пустой запрос подходит всему. */
    fun matches(query: String, texts: List<String?>): Boolean = matchesWords(queryWords(query), texts)

    /** То же, что [matches], но слова запроса уже разобраны (чтобы не делить запрос заново для каждой записи). */
    fun matchesWords(queryWords: List<String>, texts: List<String?>): Boolean {
        if (queryWords.isEmpty()) return true
        val hay = texts.filterNotNull().flatMap(::words)
        if (hay.isEmpty()) return false
        val hayStems = hay.map(RussianStemmer::stem)
        return queryWords.all { q -> wordFound(q, hay, hayStems) }
    }

    /** Одно слово запроса есть в тексте. */
    fun wordFound(q: String, hay: List<String>, hayStems: List<String> = hay.map(RussianStemmer::stem)): Boolean {
        // Короткое слово («с», «зо») ищем только в начале слов, иначе «с» нашлось бы в «такси»
        if (hay.any { if (q.length < 3) it.startsWith(q) else it.contains(q) }) return true
        val qs = RussianStemmer.stem(q)
        // Совпадение основ: короткие основы («к», «ко») дали бы ложные находки
        return qs.length >= 3 && hayStems.any { it == qs }
    }

    private fun words(s: String): List<String> = fold(s).split(SPLIT).filter { it.isNotEmpty() }
}
