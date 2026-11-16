package ru.my.traty.ui.stats

import ru.my.traty.data.Recurring
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtOverview
import ru.my.traty.data.debt.DebtOverviewLine
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.formatShortDate
import ru.my.traty.ui.pluralRu
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Подписи карточки «Долги по взносам». Чистые функции, проверяются тестом. */
internal object DebtOverviewTexts {
    fun kind(debt: Debt): String = when (debt.debtKind) {
        DebtKind.INSTALLMENT -> "Рассрочка"
        DebtKind.LOAN -> "Кредит"
        DebtKind.PARTIAL -> "Частями"
    }

    fun subtitle(o: DebtOverview): String {
        val n = o.lines.size.toLong()
        return "$n ${pluralRu(n, "покупка", "покупки", "покупок")} · считается на сегодня"
    }

    /** Под общей суммой: сколько уже внесено и чьей цены не знаем. */
    fun paidNote(o: DebtOverview): String {
        val paid = "Уже внесено ${formatRub(o.paidKopecks)}"
        val n = o.unknownPriceCount.toLong()
        if (n == 0L) return paid
        return "$paid. У $n ${pluralRu(n, "покупки", "покупок", "покупок")} не указана цена, " +
            "${if (n == 1L) "её" else "их"} остаток сюда не вошёл"
    }

    fun overpayment(o: DebtOverview): String? {
        if (o.plannedOverpaymentKopecks <= 0) return null
        val paid = if (o.paidInterestKopecks > 0) ", из них уже уплачено ${formatRub(o.paidInterestKopecks)}" else ""
        return "Переплата банку по кредитам: ${formatRub(o.plannedOverpaymentKopecks)} за весь срок$paid"
    }

    /** Строка о ближайшем платеже покупки. */
    fun next(line: DebtOverviewLine, today: LocalDate): String {
        val overdueCount = line.summary.overdueCount.toLong()
        if (overdueCount > 1) {
            return "Просрочено $overdueCount ${pluralRu(overdueCount, "платёж", "платежа", "платежей")} " +
                "на ${formatRub(line.summary.overdueKopecks)}"
        }
        val next = line.next ?: return "Без графика: доплачивайте, когда удобно"
        val amount = formatRub(next.totalKopecks)
        val days = ChronoUnit.DAYS.between(today, next.date)
        return when {
            next.overdue -> "Просрочен платёж от ${formatShortDate(next.date)}: $amount"
            days == 0L -> "Сегодня платёж $amount"
            days == 1L -> "Завтра платёж $amount"
            else -> "Следующий платёж ${formatShortDate(next.date)}: $amount"
        }
    }

    /** «Осталось 50 000 ₽ из 60 000 ₽» или «цена не указана». */
    fun left(line: DebtOverviewLine): String {
        val left = line.leftToPayKopecks ?: return "Цена не указана · внесено ${formatRub(line.summary.paidKopecks)}"
        val total = line.summary.totalKopecks
        val interest = if (line.debt.debtKind == DebtKind.LOAN && line.summary.plannedOverpaymentKopecks > 0) " с процентами" else ""
        return if (total != null) "Осталось ${formatRub(left)}$interest из ${formatRub(total)}" else "Осталось ${formatRub(left)}"
    }

    fun duplicate(line: DebtOverviewLine): String? {
        val sub = line.duplicate ?: return null
        return "«${line.debt.title}» есть и в подписках как «${sub.title}». В «Уже обещано» плана этот платёж " +
            "считается дважды. Подписку можно убрать в «Подписках»."
    }

    fun subscriptionOnly(items: List<Recurring>): String? {
        if (items.isEmpty()) return null
        val names = items.take(3).joinToString(", ") { "«${it.title}»" } + if (items.size > 3) " и другие" else ""
        val n = items.size.toLong()
        return if (n == 1L) {
            "Платёж $names в «Долгах и кредитах» заведён только подпиской, поэтому его остаток здесь не виден."
        } else {
            "Ещё $n ${pluralRu(n, "платёж", "платежа", "платежей")} в «Долгах и кредитах» заведены только подписками " +
                "($names), поэтому их остаток здесь не виден."
        }
    }

    fun empty(closedCount: Int): String =
        if (closedCount > 0) "Открытых рассрочек и кредитов нет: всё выплачено."
        else "Рассрочки и кредиты появятся здесь, когда вы запишете голосом, например, «телефон в рассрочку на 6 месяцев» или «аванс за холодильник 20 000»."
}
