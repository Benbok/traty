package ru.my.traty.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import java.time.LocalDate

@Serializable
data class ParsedExpense(
    val amount: Double = 0.0,
    val category: String = DefaultCategories.OTHER,
    /** Эмодзи — модель заполняет только для новой категории, которой нет в списке */
    val emoji: String? = null,
    val subcategory: String? = null,
    val place: String? = null,
    val note: String? = null,
    /** YYYY-MM-DD или null (= сегодня) */
    val date: String? = null,
)

@Serializable
private data class ParseResult(val items: List<ParsedExpense> = emptyList())

object ParsePrompt {
    fun system(categories: List<CategoryWithSubs>, today: LocalDate): String = buildString {
        appendLine("Ты разбираешь короткие фразы о тратах на русском языке и отвечаешь строго в формате JSON.")
        appendLine("Сегодня: $today (${today.dayOfWeek}).")
        appendLine("Доступные категории (категория: подкатегории):")
        categories.forEach { c -> appendLine("- ${c.category.name}: ${c.subNames().joinToString(", ")}") }
        appendLine()
        appendLine(
            """Формат ответа: {"items":[{"amount":число в рублях,"category":"название из списка или новая категория","emoji":"один эмодзи для новой категории или null","subcategory":"из списка или null","place":"магазин/место или null","note":"что куплено, кратко, или null","date":"YYYY-MM-DD или null"}]}"""
        )
        appendLine("Правила:")
        appendLine("- Числа словами переводи в цифры: «двести пятьдесят» → 250, «полторы тысячи» → 1500, «тыща», «косарь» → 1000, «пятихатка» → 500.")
        appendLine("- Если в фразе несколько трат — верни несколько элементов.")
        appendLine("- «вчера», «позавчера», «в понедельник» переводи в дату относительно сегодняшней; если дата не названа — null.")
        appendLine("- Категорию по возможности бери из списка (название пиши точно как в списке, emoji = null).")
        appendLine("- Если ни одна категория явно не подходит — предложи новую: короткое общее название (1–2 слова, с заглавной буквы, во множественном числе, как в списке) и подходящий emoji. Не создавай новую категорию, если подходит существующая.")
        appendLine("- Если непонятно, на что потрачено, — «${DefaultCategories.OTHER}».")
        appendLine("- Никакого текста, кроме JSON.")
    }
}

object ExpenseParsing {
    suspend fun parse(client: LlmClient, text: String, categories: List<CategoryWithSubs>): List<ParsedExpense> {
        val raw = client.complete(ParsePrompt.system(categories, LocalDate.now()), text, jsonMode = true)
        return decode(raw)
    }

    /** Модели иногда оборачивают JSON в ```json ... ``` или возвращают голый массив — обрабатываем оба случая. */
    internal fun decode(raw: String): List<ParsedExpense> {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arrStart = cleaned.indexOf('[')
        val objStart = cleaned.indexOf('{')
        return if (arrStart != -1 && (objStart == -1 || arrStart < objStart)) {
            val arr = cleaned.substring(arrStart, cleaned.lastIndexOf(']') + 1)
            lenientJson.decodeFromString(ListSerializer(ParsedExpense.serializer()), arr)
        } else {
            val obj = cleaned.substring(objStart, cleaned.lastIndexOf('}') + 1)
            lenientJson.decodeFromString(ParseResult.serializer(), obj).items
        }.filter { it.amount > 0 }
    }
}

/**
 * Запасной разбор без интернета: «кофе 250» → 250 ₽, категория по ключевым словам.
 * Работает, если ИИ недоступен или выбран режим «Без ИИ».
 */
object OfflineParser {
    private val numberRe = Regex("""(\d+(?:[.,]\d{1,2})?)""")

    fun parse(text: String, categories: List<CategoryWithSubs>): List<ParsedExpense> {
        val m = numberRe.find(text) ?: return emptyList()
        val amount = m.value.replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val note = text.removeRange(m.range).replace(Regex("""(руб(лей|ля|ль)?|₽|р\.)"""), "")
            .trim(' ', ',', '.').ifBlank { null }
        val (cat, sub) = guessCategory(note.orEmpty(), categories)
        return listOf(ParsedExpense(amount = amount, category = cat, subcategory = sub, note = note))
    }

    private fun guessCategory(text: String, categories: List<CategoryWithSubs>): Pair<String, String?> {
        val words = text.lowercase().split(Regex("""\s+""")).filter { it.length >= 3 }
        fun hits(label: String): Boolean {
            val stem = label.lowercase().take(4)
            return words.any { it.startsWith(stem) || stem.startsWith(it.take(4)) }
        }
        for (c in categories) {
            val name = c.category.name
            c.subNames().firstOrNull { hits(it) }?.let { return name to it }
            if (hits(name)) return name to null
        }
        return DefaultCategories.OTHER to null
    }
}
