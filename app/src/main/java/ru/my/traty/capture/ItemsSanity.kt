package ru.my.traty.capture

import ru.my.traty.ai.EntryType
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.ParsedItem
import ru.my.traty.ai.ShoppingListFormat
import ru.my.traty.data.items.ItemsBalanceState
import ru.my.traty.data.items.ItemsCheck
import kotlin.math.roundToLong

/**
 * Сверка цен товаров с суммой траты. Складывает приложение: ИИ мог ошибиться в цене или в итоге.
 * Цены больше суммы — трата не сохраняется молча, человек увидит окно проверки с пояснением.
 */
object ItemsSanity {
    fun problems(entries: List<ParsedEntry>): List<String> = entries
        .filter { it.entryType == EntryType.EXPENSE && it.amount > 0 && (it.items?.size ?: 0) >= 2 }
        .mapNotNull { e -> problem(e.amount, e.items.orEmpty()) }
        .distinct()

    /**
     * Перебор (цены больше суммы) — точно ошибка. Остаток не мешает: «на 2500, молоко 90, хлеб 50» —
     * остальное просто не перечислили, его видно на экране траты.
     */
    internal fun problem(amount: Double, items: List<ParsedItem>): String? {
        val b = ItemsCheck.balance(kopecks(amount), items.map { it.price?.let(::kopecks) })
        if (b.state != ItemsBalanceState.OVER) return null
        val priced = ShoppingListFormat.rub(b.pricedKopecks / 100.0)
        return "Цены товаров вместе $priced ₽, это больше суммы траты ${ShoppingListFormat.rub(amount)} ₽. Проверьте цены и сумму."
    }

    private fun kopecks(rub: Double): Long = (rub * 100).roundToLong()
}
