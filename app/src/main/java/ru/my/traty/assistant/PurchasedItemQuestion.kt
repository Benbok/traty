package ru.my.traty.assistant

import ru.my.traty.data.search.SearchText
import java.time.LocalDate

/** Название товара и место в явном вопросе о количестве купленного. */
internal object PurchasedItemQuestion {
    private const val PRONOUN = "(?:я|мы|ты|вы|он|она|они)"
    private const val BOUGHT = """(?:купил|покупал|взял|брал)(?:а|и|о)?(?!\p{L})"""
    private val itemFirst = Regex("""^сколько\s+(.+?)\s+(?:$PRONOUN\s+)?$BOUGHT(.*)$""")
    private val verbFirst = Regex("""^сколько\s+(?:$PRONOUN\s+)?$BOUGHT\s+(.+)$""")
    private const val MONTH = QuestionDates.MONTH
    private const val START_DATE =
        """(?:с|от)\s+(?:начала\s+(?:месяца|периода)|(?:начала\s+)?$MONTH|\d{1,2}(?=\s+(?:$MONTH|(?:по|до)\s)))(?=\s|[,.!?]|$)"""
    private val scopeStart = Regex(
        """\s+(?=(?:за|в|во|у|вчера|сегодня|позавчера)(?=\s|[,.!?]|$)|$START_DATE)""",
    )
    private val location = Regex("""^(?:в|во|у)\s+(\p{L}.*)$""")
    private val fillerAndUnits = Regex(
        """^(?:(?:э+|эм+|ну|штук\S*|шт|литр\S*|л|килограмм\S*|кило|кг|грамм\S*|г|пач\S*|упаков\S*|бутыл\S*|банок)\s+)+""",
    )
    private val generic = Regex("""^(?:раз|всего|денег|рублей|покупок|товаров|позиций|в среднем|вчера|сегодня|позавчера)(?:\s|$)""")
    private val punctuation = " \t\n,.!?:;«»\""

    private fun parts(question: String): Pair<String, String>? {
        val text = fold(question)
        val after = verbFirst.matchEntire(text)
        val match = after ?: itemFirst.matchEntire(text) ?: return null
        val split = scopeStart.split(match.groupValues[1], limit = 2)
        val afterVerb = match.groupValues.getOrNull(2).orEmpty().trim()
        return split.first() to (split.getOrNull(1) ?: afterVerb)
    }

    fun titleBeforeScope(phrase: String): String =
        scopeStart.split(phrase, limit = 2).first().trim { it in punctuation }

    fun itemIn(question: String): String? {
        val phrase = parts(question)?.first ?: return null
        val item = phrase.replace(fillerAndUnits, "").trim { it in punctuation }
        return item.takeIf { it.any(Char::isLetter) && !generic.containsMatchIn(it) }
    }

    fun placeIn(question: String, modelPlace: String?, today: LocalDate, startDay: Int): String? {
        val scope = parts(question)?.second ?: return null
        val clause = scopeStart.split(scope, limit = 2).first().trim { it in punctuation }
        val place = location.matchEntire(clause)?.groupValues?.get(1) ?: return null
        if (QuestionDates.find(clause, today, startDay).isNotEmpty()) return null
        return modelPlace?.takeIf { it.isNotBlank() && SearchText.matches(it, listOf(place)) } ?: place
    }
}
