package ru.my.traty.assistant

/**
 * Команды для покупок частями: «удали эти платежи, они ошибочные», «я выплатил рассрочку за телефон»,
 * «вернул телевизор, отмени покупку». Распознаются правилами поверх ответа ИИ, как команды плана и подписок:
 * так кнопка подтверждения появляется всегда, а «эти» берутся из прошлого ответа.
 */
internal object DebtCommands {
    private val DEBT_INTENTS = setOf(Intents.DEBTS, Intents.DELETE_DEBT, Intents.CLOSE_DEBT, Intents.CANCEL_DEBT)

    private val debtWordRe = Regex("""(?<![\p{L}])(долг\S*|кредит\S*|рассрочк\S*|частями|долями)""")
    private val paymentWordRe = Regex("""платеж\S*|покупк\S*""")
    private val deleteRe = Regex("""(?<![\p{L}])(удали|удалить|удалите|удаляй|убери|убрать|уберите|сотри|стереть|выкинь|почисти)(?![\p{L}])""")
    private val closeRe = Regex("""(?<![\p{L}])(закрой|закрыть|закройте|закрыл\S*|погаси|погасить|погасил\S*|выплатил\S*|расплатил\S*|рассчитал\S*)(?![\p{L}])""")
    /** «когда закрою кредит», «можно ли погасить досрочно» — это вопросы, а не команды. */
    private val questionRe = Regex("""(?<![\p{L}])(когда|сколько|можно\s+ли|какой|какая|какие|если)(?![\p{L}])""")
    private val cancelRe = Regex("""(?<![\p{L}])(вернул\S*|возврат\S*|сдал\S*\s+обратно)|(отмен\S*)\s+(эту\s+|эти\s+)?(покупк|рассрочк|кредит)""")

    /** «я вернул кредит» — выплатил, а не вернул покупку в магазин: это закрытие, а не отмена. */
    private val repaidRe = Regex("""(?<![\p{L}])вернул\S*\s+(?:(?:весь|все|всё|полностью|уже|досрочно)\s+)*(?:кредит|долг|рассрочк|займ|заем)""")

    /** Другие объекты для «убери все…»: без слов про долги это не команда для покупок частями. */
    private val otherSubjectRe = Regex("""(?<![\p{L}])(?:лимит\S*|бюджет\S*|план\S*|цел[ьиею]\S*|копилк\S*|категори\S*|сбережен\S*)""")

    /** «эти», «их», «все» — действие про то, что было в прошлом ответе. */
    private val backRe = Regex("""(?<![\p{L}])(эти|этих|эту|этот|это|их|его|ее|они|все|всё|обе|оба)(?![\p{L}])""")

    fun refersBack(text: String): Boolean = backRe.containsMatchIn(text)

    /** null — это не команда для покупок частями. [text] — вопрос в нижнем регистре, ё → е. */
    fun detect(text: String, q: AssistantQuery, previous: AssistantQuery?): AssistantQuery? {
        if ("подписк" in text) return null
        // «убери все лимиты» после вопроса про долги: «все» относится к лимитам, а не к долгам
        if (otherSubjectRe.containsMatchIn(text) && !debtWordRe.containsMatchIn(text)) return null
        val afterDebts = previous?.intent in DEBT_INTENTS
        val aboutDebts = debtWordRe.containsMatchIn(text) || (afterDebts && paymentWordRe.containsMatchIn(text)) ||
            (afterDebts && refersBack(text)) || q.intent in DEBT_INTENTS
        if (!aboutDebts) return null
        val intent = when {
            deleteRe.containsMatchIn(text) -> Intents.DELETE_DEBT
            questionRe.containsMatchIn(text) -> return null
            repaidRe.containsMatchIn(text) -> Intents.CLOSE_DEBT
            cancelRe.containsMatchIn(text) -> Intents.CANCEL_DEBT
            closeRe.containsMatchIn(text) -> Intents.CLOSE_DEBT
            q.intent in setOf(Intents.DELETE_DEBT, Intents.CLOSE_DEBT, Intents.CANCEL_DEBT) -> q.intent
            else -> return null
        }
        val back = refersBack(text)
        // ИИ иногда кладёт в place само слово «эти» — это не название покупки
        val place = q.place?.takeUnless { p ->
            p.trim().lowercase().replace('ё', 'е').split(Regex("""\s+""")).all { backRe.matches(it) || paymentWordRe.matches(it) }
        }
            ?: if (afterDebts && back) previous?.place else null
        return q.copy(intent = intent, place = place, all = q.all || back)
    }
}
