package ru.my.traty.assistant

import ru.my.traty.data.debt.DebtProgress
import ru.my.traty.data.debt.DebtState
import ru.my.traty.ui.formatRub

enum class DebtAction { DELETE, CLOSE, CANCEL }

/** Удаление, закрытие или отмена покупки частями по просьбе пользователя; выполняется только по кнопке. */
data class DebtChange(
    val id: Long,
    val title: String,
    val action: DebtAction,
    /** Сколько осталось заплатить (или цена покупки, если остаток неизвестен). */
    val amountKopecks: Long?,
    /** Что произойдёт, одной строкой: показывается под названием. */
    val summary: String,
    /** Отмечена ли строка сразу: да, если пользователь назвал покупку или сказал «эти», «все». */
    val preselected: Boolean = true,
)

/**
 * «Удали эти платежи, они ошибочные», «я выплатил рассрочку за телефон», «вернул телевизор».
 * Приложение находит покупки и предлагает изменения с отметками; ИИ ничего не меняет сам.
 */
internal object AssistantDebtChanges {
    fun action(intent: String): DebtAction? = when (intent) {
        Intents.DELETE_DEBT -> DebtAction.DELETE
        Intents.CLOSE_DEBT -> DebtAction.CLOSE
        Intents.CANCEL_DEBT -> DebtAction.CANCEL
        else -> null
    }

    fun propose(q: AssistantQuery, ctx: AssistantContext, action: DebtAction): AssistantData {
        val active = ctx.debts.filter { it.debt.state == DebtState.ACTIVE }
        val wanted = q.place?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val named = wanted?.let { w -> active.filter { AssistantDebts.matches(it.debt, w) || stemsMatch(it.debt.title, w) } }
        fun reply(text: String) = AssistantData(header(ctx) + text)
        if (active.isEmpty()) return reply("Открытых покупок частями, рассрочек и кредитов нет, менять нечего.")
        if (named != null && named.isEmpty()) {
            return reply("Покупки «${q.place}» среди открытых нет. Открытые: ${active.joinToString(", ") { "«${it.debt.title}»" }}.")
        }
        val chosen = named ?: active
        // Не назвали покупку и не сказали «эти» / «все», а покупок несколько — предлагаем все, но без отметок
        val preselected = named != null || q.all || chosen.size == 1
        val changes = chosen.map { d ->
            val s = DebtProgress.of(d.debt, d.paid, ctx.today)
            DebtChange(
                id = d.debt.id,
                title = d.debt.title + (d.debt.place?.let { " · $it" }.orEmpty()),
                action = action,
                amountKopecks = s.remainingKopecks ?: d.debt.totalKopecks,
                summary = summary(action, s.remainingKopecks),
                preselected = preselected,
            )
        }
        val facts = buildString {
            append(header(ctx))
            appendLine(
                when (action) {
                    DebtAction.DELETE -> "Предложено удалить покупки частями (${changes.size}):"
                    DebtAction.CLOSE -> "Предложено отметить выплаченными (${changes.size}):"
                    DebtAction.CANCEL -> "Предложено отменить покупки, товар вернули (${changes.size}):"
                },
            )
            changes.forEach { c -> appendLine("- «${c.title}»${c.amountKopecks?.let { ", осталось ${formatRub(it)}" }.orEmpty()}: ${c.summary}") }
            if (!preselected) appendLine("Пользователь не уточнил, какие именно: строки не отмечены, пусть отметит нужные.")
            appendLine("Пока ничего не изменено: изменения выполнятся только после нажатия кнопки под ответом.")
        }
        return AssistantData(facts, debtChanges = changes)
    }

    private fun summary(action: DebtAction, remaining: Long?): String = when (action) {
        DebtAction.DELETE -> "удалится покупка и платежи, внесённые кнопкой «Внести платёж»; траты, записанные вами, останутся"
        DebtAction.CLOSE -> if (remaining != null && remaining > 0) {
            "отметится выплаченной, хотя по записям осталось ${formatRub(remaining)}; напоминаний больше не будет"
        } else {
            "отметится выплаченной, напоминаний больше не будет"
        }
        DebtAction.CANCEL -> "отменится; внесённые платежи останутся тратами, пока вы их не удалите"
    }

    /** «холодильника» → «Холодильник»: совпадение по основе слова. */
    private fun stemsMatch(title: String, wanted: String): Boolean {
        val words = stems(title)
        return stems(wanted).any { w -> w.length >= 4 && words.any { it.startsWith(w) || w.startsWith(it) } }
    }
}
