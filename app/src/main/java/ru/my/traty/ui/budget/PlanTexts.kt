package ru.my.traty.ui.budget

import ru.my.traty.data.budget.PlanProgress
import ru.my.traty.data.budget.PlanStatus
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import ru.my.traty.ui.formatRub

// Тексты и склонения карточки «План трат» (вынесены из PlanViews.kt).

/** Подпись под полосой плана: что осталось, сколько можно тратить в день, перерасход или прогноз. */
fun planStatusText(p: PlanProgress, period: BillingPeriod, today: LocalDate = LocalDate.now()): String {
    val future = today.isBefore(period.startDate)
    val past = today.isAfter(period.endDate)
    return when {
        future -> "План на будущий период"
        p.status == PlanStatus.OVER -> "Перерасход ${formatRub(-p.remaining)}"
        past -> if (p.remaining == 0L) "План выбран ровно" else "Уложились, осталось ${formatRub(p.remaining)}"
        p.status == PlanStatus.AT_RISK && p.forecast != null ->
            "По темпу к концу ≈ ${formatRub(p.forecast)}, больше плана на ${formatRub(p.forecast - p.planned)}"
        p.remaining == 0L -> "План выбран, тратить больше не стоит"
        p.perDay != null -> "Осталось ${formatRub(p.remaining)} · ${formatRub(p.perDay)} в день"
        else -> "Осталось ${formatRub(p.remaining)}"
    }
}

/** Сводка в свёрнутой карточке: «Все 4 категории по плану», «2 из 5 категорий требуют внимания». */
internal fun planDetailsSummary(count: Int, troubled: Int): String = when {
    count <= 0 -> "Категорий в плане нет"
    troubled <= 0 -> if (count == 1) "Категория идёт по плану" else "Все $count ${categoriesCountWord(count)} по плану"
    count == 1 -> "Категория требует внимания"
    troubled == 1 -> "1 из $count ${categoriesNounGenitive(count)} требует внимания"
    else -> "$troubled из $count ${categoriesNounGenitive(count)} требуют внимания"
}

/** Подпись кнопки разворота. */
internal fun planDetailsAction(expanded: Boolean): String = if (expanded) "Свернуть" else "Подробнее"

/** «из 1 категории», «из 5 категорий» (родительный падеж после «из»). */
internal fun categoriesNounGenitive(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "категории" else "категорий"

/** В компактной карточке (Траты) — не больше стольких категорий. */
const val COMPACT_ROWS = 3

/** «1 день», «2 дня», «5 дней». */
internal fun daysWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "дней"
        m10 == 1 -> "день"
        m10 in 2..4 -> "дня"
        else -> "дней"
    }
}

/** «ещё 1 категория», «ещё 2 категории», «ещё 5 категорий». */
internal fun categoriesCountWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "категорий"
        m10 == 1 -> "категория"
        m10 in 2..4 -> "категории"
        else -> "категорий"
    }
}

/** «в 1 категории», «в 2 категориях». */
internal fun categoriesWord(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "категории" else "категориях"

/** Строка под категорией в Статистике: «План 5 000 ₽ · осталось 1 200 ₽». */
fun planLineText(p: PlanProgress): String =
    if (p.remaining >= 0) "План ${formatRub(p.planned)} · осталось ${formatRub(p.remaining)}"
    else "План ${formatRub(p.planned)} · перерасход ${formatRub(-p.remaining)}"
