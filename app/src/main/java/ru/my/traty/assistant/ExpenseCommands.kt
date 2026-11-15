package ru.my.traty.assistant

import ru.my.traty.data.CategoryWithSubs

/**
 * Команды для самих трат: «удали последнюю трату», «удали эти траты», «перенеси траты в Пятёрочке в Продукты».
 * Распознаются правилами поверх ответа ИИ, как команды плана, подписок и покупок частями:
 * кнопка подтверждения появляется всегда, а «эти» берутся из прошлого ответа.
 */
internal object ExpenseCommands {
    private val EXPENSE_INTENTS = setOf(Intents.DELETE_EXPENSE, Intents.MOVE_EXPENSE)

    /** Эти слова значат другую команду (план, подписка), даже если рядом есть слово «траты». */
    private val otherRe = Regex("""(?<![\p{L}])(лимит\S*|план\S*|бюджет\S*|подписк\S*|платеж\S*)""")
    private val questionRe = Regex("""(?<![\p{L}])(как|можно\s+ли|почему|зачем|что\s+будет|если)(?![\p{L}])""")
    private val objectRe = Regex("""(?<![\p{L}])(трат\S*|покупк\S*|запис\S*)""")
    private val deleteRe = Regex("""(?<![\p{L}])(удали|удалить|удалите|убери|убрать|сотри|стереть|выкинь|почисти)(?![\p{L}])""")
    private val moveRe = Regex("""(?<![\p{L}])(перенеси|перенести|перекинь|переложи|переведи|перемести|измени\s+категори\S*|поменяй\s+категори\S*|смени\s+категори\S*)""")
    private val lastRe = Regex("""(?<![\p{L}])(последн(юю|ий|яя|ее|его)|только\s+что)""")
    private val lastNRe = Regex("""(?<![\p{L}\d])(\d{1,2})\s+последн|последн\S*\s+(\d{1,2})(?![\p{L}\d])""")
    private val splitRe = Regex("""\s(?:в|на)\s""")

    /** Прошлые вопросы, фильтры которых можно взять для «эти траты». */
    private val INHERIT_FROM = setOf(
        Intents.TOTAL, Intents.TOP, Intents.BREAKDOWN, Intents.PLACES, Intents.LIST, Intents.COMPARE, Intents.AVERAGE,
        Intents.DELETE_EXPENSE, Intents.MOVE_EXPENSE,
    )

    /** null — это не команда для трат. [text] — вопрос в нижнем регистре, ё → е. */
    fun detect(text: String, q: AssistantQuery, previous: AssistantQuery?, categories: List<CategoryWithSubs>): AssistantQuery? {
        val byAi = q.intent in EXPENSE_INTENTS
        if (otherRe.containsMatchIn(text)) return null
        if (!byAi && questionRe.containsMatchIn(text)) return null
        val about = objectRe.containsMatchIn(text)
        val intent = when {
            byAi -> q.intent
            about && moveRe.containsMatchIn(text) -> Intents.MOVE_EXPENSE
            about && deleteRe.containsMatchIn(text) -> Intents.DELETE_EXPENSE
            else -> return null
        }
        var out = q.copy(intent = intent)
        if (intent == Intents.MOVE_EXPENSE) out = withTarget(text, out, categories) ?: return if (byAi) out else null
        if (q.limit == null) out = out.copy(limit = limitIn(text))
        val back = DebtCommands.refersBack(text)
        if (back && previous != null && previous.intent in INHERIT_FROM && out.categories.isEmpty() && out.place == null && out.from == null) {
            out = out.copy(
                categories = previous.categories.filterNot { fold(it) == fold(out.target.orEmpty()) },
                place = previous.place,
                from = previous.from,
                to = previous.to,
                limit = out.limit ?: previous.limit.takeIf { previous.intent == Intents.TOP },
            )
        }
        return out
    }

    /** Куда переносим: то, что названо после последнего «в» или «на». Остальное — откуда. */
    private fun withTarget(text: String, q: AssistantQuery, categories: List<CategoryWithSubs>): AssistantQuery? {
        val parts = text.split(splitRe)
        val named = q.target?.takeIf { it.isNotBlank() }
            ?: parts.drop(1).lastOrNull()?.let { QuestionRules.categoriesIn(it, categories).firstOrNull() }
            ?: return null
        val from = q.categories.ifEmpty { QuestionRules.categoriesIn(parts.dropLast(1).joinToString(" "), categories) }
        return q.copy(target = named, categories = from.filterNot { fold(it) == fold(named) })
    }

    private fun limitIn(text: String): Int? {
        lastNRe.find(text)?.let { m -> return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }
        return if (lastRe.containsMatchIn(text)) 1 else null
    }
}
