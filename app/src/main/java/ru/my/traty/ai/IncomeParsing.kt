package ru.my.traty.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ru.my.traty.data.IncomeSources
import java.time.LocalDate

@Serializable
data class ParsedIncome(
    val amount: Double = 0.0,
    val source: String = IncomeSources.DEFAULT,
    val note: String? = null,
    /** YYYY-MM-DD или null (= сегодня) */
    val date: String? = null,
)

@Serializable
private data class IncomeParseResult(val items: List<ParsedIncome> = emptyList())

object IncomePrompt {
    fun system(today: LocalDate): String = buildString {
        appendLine("Ты разбираешь короткие фразы о пополнениях счёта (доходах) на русском языке и отвечаешь строго в формате JSON.")
        appendLine("Сегодня: $today (${today.dayOfWeek}).")
        appendLine("Доступные источники: ${IncomeSources.ALL.joinToString(", ") { it.first }}.")
        appendLine()
        appendLine(
            """Формат ответа: {"items":[{"amount":число в рублях,"source":"из списка","note":"кратко или null","date":"YYYY-MM-DD или null"}]}"""
        )
        appendLine("Правила:")
        appendLine("- Числа словами переводи в цифры: «двести пятьдесят» → 250, «полторы тысячи» → 1500, «тыща», «косарь» → 1000, «пятихатка» → 500.")
        appendLine("- Если в фразе несколько пополнений — верни несколько элементов.")
        appendLine("- «вчера», «позавчера», «в понедельник» переводи в дату относительно сегодняшней; если дата не названа — null.")
        appendLine("- source должен быть строго одним из названий списка, написанным точно как в списке.")
        appendLine("- Если непонятно, откуда деньги, — «${IncomeSources.DEFAULT}».")
        appendLine("- Никакого текста, кроме JSON.")
    }
}

object IncomeParsing {
    suspend fun parse(client: LlmClient, text: String): List<ParsedIncome> {
        val raw = client.complete(IncomePrompt.system(LocalDate.now()), text, jsonMode = true)
        return decode(raw)
    }

    /** Как [ExpenseParsing.decode]: JSON в ```-блоке или голый массив; источник приводится к списку. */
    internal fun decode(raw: String): List<ParsedIncome> {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arrStart = cleaned.indexOf('[')
        val objStart = cleaned.indexOf('{')
        return if (arrStart != -1 && (objStart == -1 || arrStart < objStart)) {
            val arr = cleaned.substring(arrStart, cleaned.lastIndexOf(']') + 1)
            lenientJson.decodeFromString(ListSerializer(ParsedIncome.serializer()), arr)
        } else {
            val obj = cleaned.substring(objStart, cleaned.lastIndexOf('}') + 1)
            lenientJson.decodeFromString(IncomeParseResult.serializer(), obj).items
        }.filter { it.amount > 0 }
            .map { it.copy(source = IncomeSources.normalize(it.source)) }
    }
}

/** Запасной разбор без интернета: «аванс 30 000» → 30000 ₽, источник по ключевым словам. */
object OfflineIncomeParser {
    private const val SEP = "[   ]"

    // «80 000» (разделитель тысяч — пробел/NBSP/NNBSP между 3-значными группами) либо обычное число;
    // дробная часть через «,» или «.». Берётся только первое число в тексте.
    private val numberRe = Regex("""\d{1,3}(?:$SEP\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")
    private val currencyRe = Regex("""(руб(лей|ля|ль)?|₽|р\.)""")

    fun parse(text: String): List<ParsedIncome> {
        val m = numberRe.find(text) ?: return emptyList()
        val amount = m.value.replace(Regex(SEP), "").replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val note = text.removeRange(m.range).replace(currencyRe, "")
            .trim(' ', ',', '.').ifBlank { null }
        return listOf(ParsedIncome(amount = amount, source = guessSource(text), note = note))
    }

    private fun guessSource(text: String): String {
        val t = text.lowercase()
        return when {
            "зарплат" in t -> "Зарплата"
            "аванс" in t -> "Аванс"
            "дежур" in t -> "Дежурства"
            "возврат" in t || "вернул" in t -> "Возврат"
            "подработ" in t -> "Подработка"
            else -> IncomeSources.DEFAULT
        }
    }
}
