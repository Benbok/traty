package ru.my.traty.ui.charts.model

import java.time.LocalDate

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
 * Точка на графике динамики баланса.
 */
data class BalancePoint(
    val label: String,
    val date: LocalDate,
    /** Изменение баланса в этот день/интервал (дебет − кредит). */
    val deltaKopecks: Long,
    /** Накопительный баланс на конец дня/интервала. */
    val cumulativeBalanceKopecks: Long,
)

/**
 * Элемент столбчатого графика сравнения дебета и кредита.
 */
data class DebitCreditBar(
    val label: String,
    val fullDateLabel: String,
    val date: LocalDate,
    val debitKopecks: Long,
    val creditKopecks: Long,
) {
    val netKopecks: Long get() = debitKopecks - creditKopecks
}

/**
 * Доля категории в общем кредите (тратах).
 */
data class CategoryCreditShare(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val totalKopecks: Long,
    val percent: Float,
)
