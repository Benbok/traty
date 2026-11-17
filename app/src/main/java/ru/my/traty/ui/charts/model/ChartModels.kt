package ru.my.traty.ui.charts.model

import ru.my.traty.data.StoreTotal

/**
 * Периоды агрегации данных для графиков.
 */
enum class ChartPeriod(val title: String) {
    MONTH("Месяц"),
    QUARTER("3 мес"),
    HALF_YEAR("6 мес"),
    YEAR("Год"),
    ALL("Всё время"),
    CUSTOM("Даты"),
}

/**
 * Сводные финансовые метрики (KPI) за выбранный период.
 */
data class ChartSummary(
    /** Сумма доходов (дебет / поступления) в копейках. */
    val totalDebitKopecks: Long,
    /** Сумма расходов (кредит / списания) в копейках. */
    val totalCreditKopecks: Long,
    /** Чистое сальдо / баланс (Дебет − Кредит) в копейках. */
    val netBalanceKopecks: Long,
    /** Норма сбережений (% от дохода, если доход > 0). */
    val savingsRatePercent: Int?,
    /** Средний расход в день в копейках. */
    val dailyAvgCreditKopecks: Long,
    /** Количество операций доходов. */
    val incomeCount: Int,
    /** Количество операций расходов. */
    val expenseCount: Int,
)

/**
 * Доля категории в общем кредите (тратах).
 */
data class CategoryCreditShare(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val totalKopecks: Long,
    val percent: Float,
    /** Разбивка категории по магазинам (по убыванию суммы, «Без магазина» последним). */
    val stores: List<StoreTotal> = emptyList(),
)

