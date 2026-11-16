package ru.my.traty.assistant

import java.math.BigDecimal

/**
 * Тексты ответа, которые собирает само приложение, а не ИИ:
 * - шаблон для простых числовых вопросов: это те же строки, что посчитал [AssistantEngine], без второго обращения к ИИ;
 * - проверка текста ИИ: каждое число в ответе должно быть среди посчитанных приложением;
 * - список «Что ушло в ИИ»: оба запроса, а не только второй.
 */
object AssistantAnswers {
    /** Вопросы, где ответ — несколько готовых строк с цифрами; пересказывать их ИИ незачем. */
    private val TEMPLATE_INTENTS = setOf(Intents.TOTAL, Intents.INCOME, Intents.AVERAGE, Intents.BALANCE)

    /** null — для этого вопроса ответ формулирует ИИ (разбивка, сравнение, советы, команды). */
    fun template(intent: String, data: AssistantData): String? {
        if (intent !in TEMPLATE_INTENTS || data.direct != null) return null
        val lines = AssistantFallback.bodyLines(data).filterNot { it.startsWith("- ") || it.startsWith("Больше всего") }
        return lines.joinToString("\n").ifBlank { null }
    }

    private val numberRe = Regex("""\d{1,3}(?:[   ]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?""")

    /** Числа текста в одном виде: «1 500» и «1500» — одно число, «250,50» и «250.5» тоже. */
    internal fun numbers(text: String): Set<String> = numberRe.findAll(text).mapNotNull { m ->
        m.value.filter { !it.isWhitespace() && it != ' ' && it != ' ' }.replace(',', '.')
            .toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()
    }.toSet()

    /** Дни месяца, количество, год — такие числа ИИ вправе назвать, даже если их нет в расчётах как отдельное число. */
    private fun harmless(n: String): Boolean {
        val v = n.toBigDecimalOrNull() ?: return true
        return v.scale() <= 0 && (v <= BigDecimal(31) || v in BigDecimal(2000)..BigDecimal(2100))
    }

    /** Числа из [answer], которых нет в [facts]: ИИ их придумал или досчитал сам. */
    fun unknownNumbers(answer: String, facts: String): List<String> {
        val known = numbers(facts)
        return numbers(answer).filter { it !in known && !harmless(it) }.sorted()
    }

    /** Текст ИИ, если в нём только посчитанные числа; иначе сами расчёты приложения. */
    fun verified(answer: String, data: AssistantData): String =
        if (unknownNumbers(answer, data.facts).isEmpty()) answer else AssistantFallback.unchecked(data)

    /** Что ушло в ИИ за вопрос: первый запрос (вопрос, названия категорий) и, если был, второй (цифры). */
    fun sent(stepOneUser: String, categoryNames: List<String>, stepTwoFacts: String?): String = buildString {
        appendLine("Шаг 1. Из вашего вопроса ИИ делает запрос к данным. Ушло:")
        appendLine(stepOneUser)
        if (categoryNames.isNotEmpty()) appendLine("И названия ваших категорий: ${categoryNames.joinToString(", ")}.")
        appendLine("Суммы, траты и заметки на этом шаге не отправляются.")
        if (stepTwoFacts != null) {
            appendLine()
            appendLine("Шаг 2. ИИ формулирует ответ по цифрам, которые посчитало приложение. Ушло:")
            append(stepTwoFacts.trim())
        }
    }.trim()
}
