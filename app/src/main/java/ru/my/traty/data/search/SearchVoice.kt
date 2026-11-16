package ru.my.traty.data.search

/**
 * Голосовой ввод в поиск: из распознанной фразы остаётся то, что нужно искать. Работает без ИИ.
 * Распознавание добавляет точку в конце и кавычки («Шоколад, масло.»), а люди часто начинают с команды
 * («найди шоколад»): команду и знаки по краям убираем, слова запроса остаются как сказаны.
 */
object SearchVoice {
    private val COMMANDS = setOf("найди", "найти", "найдите", "ищи", "ищите", "поиск", "покажи", "покажите")
    private val EDGE = charArrayOf(' ', ',', '.', ';', ':', '!', '?', '…', '-', '—', '–', '«', '»', '"', '“', '”')

    fun clean(spoken: String): String {
        val words = spoken.trim(*EDGE).split(Regex("""\s+""")).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.first().trim(*EDGE).lowercase() in COMMANDS) words.removeAt(0)
        return words.joinToString(" ").trim(*EDGE)
    }
}
