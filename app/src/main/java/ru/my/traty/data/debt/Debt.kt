package ru.my.traty.data.debt

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.my.traty.data.Expense
import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.data.recurring.RecurrencePeriod

/** Вид покупки частями. Хранится в базе строкой [key]. */
enum class DebtKind(val key: String) {
    /** Аванс и доплаты без графика: «аванс за холодильник 20 000», потом «доплатил 5000». */
    PARTIAL("partial"),

    /** Рассрочка без процентов: N равных платежей по графику. */
    INSTALLMENT("installment"),

    /** Кредит: ставка, срок, аннуитетный платёж, переплата. */
    LOAN("loan");

    companion object {
        fun of(key: String?): DebtKind = entries.firstOrNull { it.key == key } ?: PARTIAL
    }
}

/** Направление долга. Пока есть только «Я должен»; «Мне должны» добавит этап 6. */
object DebtDirection {
    const val I_OWE = "i_owe"
    const val OWED_TO_ME = "owed_to_me"
}

object DebtState {
    const val ACTIVE = "active"

    /** Всё оплачено. */
    const val CLOSED = "closed"

    /** Покупку отменили или вернули; её платежи остаются в истории, пока их не удалят. */
    const val CANCELLED = "cancelled"
}

/**
 * Покупка частями: аванс с доплатами, рассрочка или кредит. Каждый платёж — обычная трата
 * (в день оплаты, в категории покупки), связанная с покупкой через [DebtPayment].
 * Остаток, график и переплата не хранятся, а считаются ([DebtSchedule], [DebtProgress]).
 *
 * Суммы в копейках. [totalKopecks] — цена покупки (тело кредита); null — пользователь её пока не назвал.
 * [rateBasisPoints] — годовая ставка в сотых долях процента: 20 % = 2000, 0 — без процентов.
 * [paymentsCount] — число платежей по графику; null — графика нет (частичная оплата).
 * [paymentKopecks] — платёж по договору, если пользователь его назвал: тогда он важнее расчётного.
 * [firstDueEpochDay] — дата первого платежа по графику (epoch day).
 */
@Entity(tableName = "debts", indices = [Index("state")])
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val place: String? = null,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val kind: String = DebtKind.PARTIAL.key,
    val direction: String = DebtDirection.I_OWE,
    val currency: String = "RUB",
    val totalKopecks: Long? = null,
    val rateBasisPoints: Int = 0,
    val paymentsCount: Int? = null,
    val periodUnit: String = PeriodUnit.MONTH.key,
    val periodEvery: Int = 1,
    val firstDueEpochDay: Long? = null,
    val paymentKopecks: Long? = null,
    val state: String = DebtState.ACTIVE,
    val createdAt: Long = 0,
) {
    val debtKind: DebtKind get() = DebtKind.of(kind)
    val period: RecurrencePeriod get() = RecurrencePeriod(PeriodUnit.of(periodUnit), periodEvery.coerceAtLeast(1))
}

/** Из чего состоит платёж: тело покупки или проценты банку. Хранится строкой. */
object PaymentPart {
    const val PRINCIPAL = "principal"
    const val INTEREST = "interest"
}

/**
 * Связь траты с покупкой. Удаление траты удаляет связь, и остаток по покупке сразу пересчитывается.
 */
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(entity = Debt::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Expense::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("debtId"), Index(value = ["expenseId"], unique = true)],
)
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val expenseId: Long,
    val part: String = PaymentPart.PRINCIPAL,
)

/** Платёж по покупке, как его видит расчёт: день, часть и сумма. */
data class PaidPart(
    val epochDay: Long,
    val part: String,
    val amountKopecks: Long,
)
