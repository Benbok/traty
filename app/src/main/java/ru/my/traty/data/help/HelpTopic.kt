package ru.my.traty.data.help

/**
 * Одна тема справки. Тексты для людей, без технических слов.
 * [id] не меняется: на него ссылаются «Что нового» и ссылки из других экранов.
 */
data class HelpTopic(
    val id: String,
    val emoji: String,
    val title: String,
    /** Одна строка под названием в списке. */
    val summary: String,
    /** Абзацы темы. Строка, начинающаяся с «• », показывается пунктом списка. */
    val body: List<String>,
    /** Где это найти в приложении, например «Настройки → Подписки». */
    val where: String? = null,
)
