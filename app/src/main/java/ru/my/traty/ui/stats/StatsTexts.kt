package ru.my.traty.ui.stats

import ru.my.traty.ui.formatRub
import kotlin.math.roundToInt

// Тексты экрана «Статистика», вынесены из StatsScreen.kt без изменений

/** «Прошлый период: 12 000 ₽ (+8%)»; null — сравнивать не с чем. */
internal fun deltaDescriptionText(span: StatsSpan, sum: Long, prevSum: Long): String? {
    if (prevSum <= 0) return null
    val label = when (span) {
        StatsSpan.PERIOD -> "Прошлый период"
        StatsSpan.DAY -> "Предыдущий день"
        StatsSpan.WEEK -> "Прошлая неделя"
        StatsSpan.DATES -> "Предыдущие такие же даты"
    }
    return "$label: ${formatRub(prevSum)} (${deltaText(sum, prevSum)})"
}

/** 1 покупка, 3 покупки, 5 покупок. */
internal fun purchases(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    val word = when {
        m100 in 11..14 -> "покупок"
        m10 == 1 -> "покупка"
        m10 in 2..4 -> "покупки"
        else -> "покупок"
    }
    return "$n $word"
}

internal fun deltaText(cur: Long, prev: Long): String {
    if (prev == 0L) return "—"
    val pct = ((cur - prev) * 100.0 / prev).roundToInt()
    return if (pct >= 0) "+$pct%" else "$pct%"
}

/** 1 товар, 3 товара, 5 товаров. */
internal fun productsWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "товаров"
        m10 == 1 -> "товар"
        m10 in 2..4 -> "товара"
        else -> "товаров"
    }
}
