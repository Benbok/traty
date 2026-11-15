package ru.my.traty.data.debt

/** Подписи трат-платежей в ленте. Без Android, чтобы проверять тестом. */
object DebtTexts {
    /**
     * Заметка траты-платежа: «Холодильник · платёж 2 из 6», «Холодильник · доплата»,
     * «Ноутбук · проценты, платёж 3 из 12».
     */
    fun paymentNote(debt: Debt, part: String, number: Int?, count: Int?, first: Boolean): String {
        val n = if (number != null && count != null) "платёж $number из $count" else null
        val what = when {
            part == PaymentPart.INTEREST -> listOfNotNull("проценты", n).joinToString(", ")
            n != null -> n
            first -> if (debt.debtKind == DebtKind.PARTIAL) "аванс" else "первый платёж"
            else -> "доплата"
        }
        return "${debt.title} · $what"
    }
}
