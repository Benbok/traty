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
    /** Чек, распознанный с фотографии. */
    const val PHOTO = "photo"
    const val PURCHASE_CONTRIBUTION = "purchase_contribution"
    const val PURCHASE_SETTLEMENT = "purchase_settlement"
}

/** Откуда пришло пополнение. */
object IncomeOrigin {
    const val MANUAL = "manual"
    const val TEXT = "text"
    const val VOICE = "voice"
    const val RECONCILE = "reconcile"
    const val PURCHASE_REFUND = "purchase_refund"
    const val PURCHASE_CANCELLATION_REFUND = "purchase_cancellation_refund"
}

val Expense.isPurchaseGoalEntry: Boolean
    get() = source == ExpenseSource.PURCHASE_CONTRIBUTION || source == ExpenseSource.PURCHASE_SETTLEMENT

/** Записи сверки и накоплений изменяются через соответствующую операцию. */
val Expense.isUserEditable: Boolean get() = source != ExpenseSource.RECONCILE && !isPurchaseGoalEntry
val Income.isUserEditable: Boolean get() = origin != IncomeOrigin.RECONCILE && origin != IncomeOrigin.PURCHASE_REFUND &&
        origin != IncomeOrigin.PURCHASE_CANCELLATION_REFUND
