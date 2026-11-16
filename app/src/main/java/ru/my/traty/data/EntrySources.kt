package ru.my.traty.data

/** Откуда пришла трата. */
object ExpenseSource {
    const val VOICE = "voice"
    const val TEXT = "text"
    const val MANUAL = "manual"
    const val IMPORT = "import"
    const val RECONCILE = "reconcile"
    /** Отмечено «списалось» у регулярного платежа. */
    const val RECURRING = "recurring"
    /** Платёж по покупке частями (аванс, доплата, рассрочка, кредит). */
    const val DEBT = "debt"
}

/** Откуда пришло пополнение. */
object IncomeOrigin {
    const val MANUAL = "manual"
    const val TEXT = "text"
    const val VOICE = "voice"
    const val RECONCILE = "reconcile"
}
