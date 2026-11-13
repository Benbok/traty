package ru.my.traty.data

/** Источники пополнений: название → эмодзи. Порядок = порядок в диалоге и в промпте ИИ. */
object IncomeSources {
    val ALL: List<Pair<String, String>> = listOf(
        "Зарплата" to "💼",
        "Аванс" to "💰",
        "Дежурства" to "🩺",
        "Перевод" to "💳",
        "Подработка" to "🛠️",
        "Возврат" to "↩️",
        "Прочее" to "📦",
    )

    const val DEFAULT = "Прочее"

    /** Каноническое название из списка (без учёта регистра); неизвестное или пустое → [DEFAULT]. */
    fun normalize(name: String?): String =
        ALL.firstOrNull { name != null && sameName(it.first, name) }?.first ?: DEFAULT
}
