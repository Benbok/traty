package ru.my.traty.assistant.apartments

import ru.my.traty.assistant.AssistantQuery
import ru.my.traty.assistant.QuestionDates
import java.time.LocalDate

/** Расчёт ресурсов не перехватывает вопросы о денежных тратах. */
object ApartmentQuestionRules {
    fun resolve(raw: String, ai: AssistantQuery?, previous: ApartmentQuestion?, today: LocalDate): ApartmentQuestion? {
        val text = raw.lowercase().replace('ё', 'е')
        if (Regex("заплат|оплат|начисл|рубл|стоим|цен[ауы]|денег|тариф|потрат|купил|покупк|магазин").containsMatchIn(text)) return null
        val explicit = ai?.intent in ApartmentIntents.ALL
        val follow = previous != null && Regex("^\\s*а\\s+").containsMatchIn(text)
        val relevant = Regex("счетчик|показан|потреблен|кубом|кубов|квт|расход.*(вод|газ|электр)|сравн.*(вод|газ|электр)|сколько.*(вод|газ)|вод.*расход|электричеств|холодн.*вод|горяч.*вод").containsMatchIn(text)
        if (!explicit && !relevant && !follow) return null
        if (follow && !explicit && !relevant && ai?.categories?.isNotEmpty() == true) return null
        val base = ai?.housing ?: if (follow) previous ?: ApartmentQuestion() else ApartmentQuestion()
        val kind = when {
            (text.contains("показан") && !Regex("сравн|измен|динамик|истори").containsMatchIn(text)) || ai?.intent == ApartmentIntents.READINGS -> "readings"
            Regex("динамик|как менял|истори").containsMatchIn(text) || ai?.intent == ApartmentIntents.TREND -> "trend"
            else -> base.kind
        }
        val resource = when {
            Regex("горяч|гвс").containsMatchIn(text) -> "HOT_WATER"
            Regex("холод|хвс").containsMatchIn(text) -> "COLD_WATER"
            Regex("электр|квт").containsMatchIn(text) -> "ELECTRICITY"
            Regex("(?<![а-я])газ").containsMatchIn(text) -> "GAS"
            else -> base.resource
        }
        val spans = QuestionDates.find(raw, today, 1, joinMonths = false)
        val ordered = spans.take(2).sortedBy { it.from }
        val primary = ordered.lastOrNull()
        val comparison = ordered.firstOrNull()?.takeIf { ordered.size == 2 }
        return base.copy(kind = kind, resource = resource,
            from = primary?.from?.toString() ?: ai?.from ?: if (follow) null else base.from,
            to = primary?.to?.toString() ?: ai?.to ?: if (follow) null else base.to,
            compareFrom = comparison?.from?.toString() ?: ai?.compareFrom,
            compareTo = comparison?.to?.toString() ?: ai?.compareTo)
    }
}
