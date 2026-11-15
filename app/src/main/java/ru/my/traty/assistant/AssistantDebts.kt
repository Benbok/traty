package ru.my.traty.assistant

import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtProgress
import ru.my.traty.data.debt.DebtState
import ru.my.traty.data.debt.DebtStatus
import ru.my.traty.data.debt.PaidPart
import ru.my.traty.ui.formatRub

/** Покупка частями вместе с её платежами — для ответов ассистента. */
data class DebtWithPaid(val debt: Debt, val paid: List<PaidPart>)

/**
 * Ответ на «сколько я должен», «когда следующий платёж по рассрочке», «какая переплата по кредиту».
 * Цифры считает приложение ([DebtProgress]); ИИ получает готовые факты и только формулирует ответ.
 * В ИИ уходят названия покупок, суммы и даты, без заметок и истории трат.
 */
internal object AssistantDebts {
    fun answer(q: AssistantQuery, ctx: AssistantContext): AssistantData {
        val wanted = q.place?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val active = ctx.debts.filter { it.debt.state == DebtState.ACTIVE }
            .filter { wanted == null || matches(it.debt, wanted) }
        val rows = active.map { it to DebtProgress.of(it.debt, it.paid, ctx.today) }
        val owed = rows.sumOf { it.second.remainingKopecks ?: 0L }
        val facts = buildString {
            append(header(ctx))
            if (rows.isEmpty()) {
                appendLine(if (wanted == null) "Открытых покупок частями, рассрочек и кредитов нет." else "Открытой покупки «${q.place}» нет.")
                return@buildString
            }
            appendLine("Открытые покупки частями, рассрочки и кредиты (${rows.size}):")
            rows.forEach { (d, s) -> appendLine("- " + line(d.debt, s)) }
            appendLine("Всего осталось заплатить (без будущих процентов): ${formatRub(owed)}.")
            val next = rows.mapNotNull { (d, s) -> s.next?.let { d.debt to it } }.minByOrNull { it.second.date }
            next?.let { (debt, n) -> appendLine("Ближайший платёж: «${debt.title}» ${date(n.date)}, ${formatRub(n.totalKopecks)}${if (n.overdue) " (просрочен)" else ""}.") }
            if (rows.any { it.first.debt.totalKopecks == null }) appendLine("У некоторых покупок цена не указана, остаток по ним неизвестен.")
        }
        val caption = if (wanted == null) "осталось заплатить по покупкам частями" else "осталось заплатить"
        return AssistantData(facts, formatRub(owed), caption)
    }

    /** «телевизор», «за телевизор на озоне», «Ozon»: совпадение по названию покупки или магазину. */
    internal fun matches(debt: Debt, wanted: String): Boolean =
        listOfNotNull(debt.title, debt.place).map { it.lowercase().replace('ё', 'е') }.filter { it.isNotBlank() }
            .any { name -> name.contains(wanted.replace('ё', 'е')) || wanted.replace('ё', 'е').contains(name) }

    private fun line(debt: Debt, s: DebtStatus): String {
        val kind = when (debt.debtKind) {
            DebtKind.INSTALLMENT -> "рассрочка"
            DebtKind.LOAN -> "кредит ${rate(debt.rateBasisPoints)}"
            DebtKind.PARTIAL -> "оплата частями"
        }
        val where = debt.place?.let { ", $it" }.orEmpty()
        val total = debt.totalKopecks?.let { " из ${formatRub(it)}" }.orEmpty()
        val left = s.remainingKopecks?.let { ", осталось ${formatRub(it)}" } ?: ", цена не указана"
        val next = s.next?.let { n ->
            ", следующий платёж ${n.number} из ${n.count}: ${date(n.date)}, ${formatRub(n.totalKopecks)}" + if (n.overdue) " (просрочен)" else ""
        }.orEmpty()
        val overpay = if (s.plannedOverpaymentKopecks > 0) ", переплата по графику ${formatRub(s.plannedOverpaymentKopecks)}, уже уплачено процентов ${formatRub(s.paidInterestKopecks)}" else ""
        return "«${debt.title}»$where ($kind): оплачено ${formatRub(s.paidPrincipalKopecks)}$total$left$next$overpay"
    }

    private fun rate(bp: Int): String = if (bp <= 0) "без процентов" else "под ${bp / 100}${if (bp % 100 != 0) "," + (bp % 100).toString().padStart(2, '0').trimEnd('0') else ""} % годовых"
}
