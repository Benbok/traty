package ru.my.traty.assistant

import ru.my.traty.data.isUserEditable
import ru.my.traty.ui.formatRub

enum class ExpenseAction { DELETE, MOVE }

/** Удаление траты или перенос в другую категорию по просьбе пользователя; выполняется только по кнопке. */
data class ExpenseChange(
    val id: Long,
    val title: String,
    val action: ExpenseAction,
    val amountKopecks: Long,
    /** Что произойдёт, одной строкой: показывается под названием. */
    val summary: String,
    /** Где трата лежала до изменения — чтобы можно было вернуть. */
    val fromCategoryId: Long,
    val fromSubcategoryId: Long?,
    /** Куда переносим; для удаления null. */
    val toCategoryId: Long? = null,
    val toSubcategoryId: Long? = null,
    val preselected: Boolean = true,
)

/**
 * «Удали последнюю трату», «удали траты в Пятёрочке за октябрь», «перенеси траты в Пятёрочке в Продукты».
 * Приложение находит траты и предлагает строки с отметками; ИИ ничего не меняет сам.
 */
internal object AssistantExpenseChanges {
    /** Больше строк в карточке не показываем: за один раз такой пакет сложно проверить глазами. */
    const val MAX_CHANGES = 30

    fun action(intent: String): ExpenseAction? = when (intent) {
        Intents.DELETE_EXPENSE -> ExpenseAction.DELETE
        Intents.MOVE_EXPENSE -> ExpenseAction.MOVE
        else -> null
    }

    private class Target(val categoryId: Long, val subcategoryId: Long?, val title: String)

    fun propose(q: AssistantQuery, ctx: AssistantContext, action: ExpenseAction): AssistantData {
        fun reply(text: String) = AssistantData(header(ctx) + text)
        val spending = ctx.expenses.filter { it.expense.isUserEditable }
        val filter = Filter.of(q, spending, ctx.categories)
        if (filter.unknownNames.isNotEmpty() && filter.names.isEmpty() && filter.terms.isEmpty()) {
            return reply("Категорий или слов «${filter.unknownNames.joinToString("», «")}» в записях нет, ничего не нашлось.")
        }
        val target = if (action == ExpenseAction.MOVE) targetOf(q.target, ctx) else null
        if (action == ExpenseAction.MOVE && target == null) {
            val known = ctx.categories.joinToString(", ") { it.category.name }
            return reply(
                if (q.target.isNullOrBlank()) "Не сказано, в какую категорию переносить. Есть категории: $known."
                else "Категории «${q.target}» нет. Есть категории: $known.",
            )
        }

        val dated = q.from != null || q.to != null
        val (from, to) = range(q.from, q.to, QuestionDates.ALL_TIME, ctx.today)
        val found = spending
            .filter { r -> day(r.expense.timestamp, ctx.zone).let { d -> !d.isBefore(from) && !d.isAfter(to) } && filter.matches(r) }
            .filter { r -> target == null || r.expense.categoryId != target.categoryId || r.expense.subcategoryId != target.subcategoryId }
            .sortedByDescending { it.expense.timestamp }
        val limit = q.limit?.coerceIn(1, MAX_CHANGES)
        // Ничего не уточнили — это «последняя трата», а не вся история
        val picked = when {
            limit != null -> found.take(limit)
            !filter.isEmpty || dated -> found
            else -> found.take(1)
        }
        if (picked.isEmpty()) return reply("Подходящих трат не нашлось, менять нечего.")
        val shown = picked.take(MAX_CHANGES)

        val changes = shown.map { r ->
            val where = listOfNotNull(r.categoryName, r.subcategoryName).joinToString(" · ")
            ExpenseChange(
                id = r.expense.id,
                title = "${date(day(r.expense.timestamp, ctx.zone))} · " + (r.expense.place ?: r.expense.note ?: r.categoryName),
                action = action,
                amountKopecks = r.expense.amountKopecks,
                summary = if (target == null) "$where · исчезнет из ленты и статистики" else "$where → ${target.title}",
                fromCategoryId = r.expense.categoryId,
                fromSubcategoryId = r.expense.subcategoryId,
                toCategoryId = target?.categoryId,
                toSubcategoryId = target?.subcategoryId,
            )
        }
        val facts = buildString {
            append(header(ctx))
            appendLine(
                if (target == null) "Предложено удалить траты (${shown.size}) на ${formatRub(shown.sumOf { it.expense.amountKopecks })}:"
                else "Предложено перенести траты (${shown.size}) в «${target.title}»:",
            )
            shown.forEach { appendLine("- ${describe(it, ctx.zone)}") }
            if (picked.size > shown.size) appendLine("Подходящих трат больше (${picked.size}), показаны только первые ${shown.size}: остальные можно изменить следующей просьбой.")
            appendLine("Пока ничего не изменено: изменения выполнятся только после нажатия кнопки под ответом, и их можно будет вернуть.")
        }
        return AssistantData(facts, expenseChanges = changes)
    }

    /** Категория или подкатегория, названная пользователем; подкатегория тянет за собой свою категорию. */
    private fun targetOf(name: String?, ctx: AssistantContext): Target? {
        val wanted = name?.let(::fold)?.takeIf { it.isNotEmpty() } ?: return null
        val all = LinkedHashMap<String, Target>()
        ctx.categories.forEach { c ->
            all[fold(c.category.name)] = Target(c.category.id, null, c.category.name)
            c.subs.forEach { s -> all.putIfAbsent(fold(s.name), Target(c.category.id, s.id, "${c.category.name} · ${s.name}")) }
        }
        return all[wanted] ?: closest(wanted, all.keys)?.let { all[it] }
    }
}
