package ru.my.traty.data.items

/** Как позиции сходятся с суммой траты. */
enum class ItemsBalanceState {
    /** Ни у одной позиции нет цены: сверять нечего. */
    NO_PRICES,

    /** Цены позиций в сумме равны сумме траты. */
    MATCH,

    /** Цены позиций меньше суммы траты: остаток приходится на позиции без цены или не назван. */
    REST,

    /** Цены позиций больше суммы траты: где-то ошибка в цене или в сумме. */
    OVER,
}

/**
 * Сверка позиций с суммой траты. [pricedKopecks] — сумма названных цен, [unpriced] — позиций без цены,
 * [differenceKopecks] — сумма траты минус цены позиций (больше нуля — остаток, меньше нуля — перебор).
 */
data class ItemsBalance(
    val totalKopecks: Long,
    val pricedKopecks: Long,
    val unpriced: Int,
    val differenceKopecks: Long,
) {
    val state: ItemsBalanceState
        get() = when {
            pricedKopecks == 0L && unpriced > 0 -> ItemsBalanceState.NO_PRICES
            differenceKopecks == 0L -> ItemsBalanceState.MATCH
            differenceKopecks > 0 -> ItemsBalanceState.REST
            else -> ItemsBalanceState.OVER
        }

    /** Цены не сходятся с суммой, и списать разницу не на что: нужен взгляд человека. */
    val needsAttention: Boolean
        get() = state == ItemsBalanceState.OVER || (state == ItemsBalanceState.REST && unpriced == 0)
}

/** Считает приложение, а не ИИ: сумма цен позиций против суммы траты. */
object ItemsCheck {
    /** [prices] — цены позиций в копейках, null — у позиции цены нет. */
    fun balance(totalKopecks: Long, prices: List<Long?>): ItemsBalance {
        val priced = prices.sumOf { it ?: 0L }
        return ItemsBalance(
            totalKopecks = totalKopecks,
            pricedKopecks = priced,
            unpriced = prices.count { it == null },
            differenceKopecks = totalKopecks - priced,
        )
    }

    /** Сумма траты по позициям, если у всех есть цена; иначе null (сумму нужно назвать). */
    fun totalOf(prices: List<Long?>): Long? =
        if (prices.isEmpty() || prices.any { it == null }) null else prices.sumOf { it ?: 0L }
}
