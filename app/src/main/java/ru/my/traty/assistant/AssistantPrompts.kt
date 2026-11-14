package ru.my.traty.assistant

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Один прошлый обмен репликами — чтобы понимать «а в сентябре?». */
data class AssistantTurn(val question: String, val queryJson: String)

/** Тексты запросов к ИИ: шаг 1 — вопрос в JSON, шаг 2 — посчитанные цифры в человеческий ответ. */
object AssistantPrompts {
    private val RU: Locale = Locale.forLanguageTag("ru")

    /** Сколько прошлых вопросов передаём для уточнений. */
    const val HISTORY = 2

    fun querySystem(categories: List<CategoryWithSubs>, today: LocalDate, startDay: Int): String {
        val period = BillingPeriod.containing(today, startDay)
        val previous = period.previous(startDay)
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, RU)
        val cats = categories.joinToString("\n") { c ->
            val subs = c.subNames()
            "- ${c.category.name}" + if (subs.isNotEmpty()) " (подкатегории: ${subs.joinToString(", ")})" else ""
        }
        return """
Ты переводишь вопрос пользователя о его личных финансах в JSON-запрос к данным. Сам ничего не считаешь и не отвечаешь.
Сегодня $today, $weekday. Текущий расчётный период: ${period.startDate} — ${period.endDate}. Прошлый: ${previous.startDate} — ${previous.endDate}.

Верни только JSON-объект без пояснений:
{"intent": "...", "categories": [], "place": null, "from": null, "to": null, "compareFrom": null, "compareTo": null, "amount": null, "limit": null}

intent — одно из:
- total — сколько потрачено (всего, на категорию, в месте);
- top — самые крупные траты (limit — сколько, по умолчанию 5);
- breakdown — на что ушли деньги, разбивка по категориям;
- places — где (в каких магазинах, местах) больше всего тратится;
- compare — сравнение двух отрезков («больше или меньше, чем в прошлом месяце», «почему вышло больше»);
- average — сколько в среднем в день или неделю;
- subscriptions — подписки и регулярные платежи;
- income — доходы, поступления, «сколько заработал», «в плюсе ли я»;
- afford — «могу ли я позволить / хватит ли денег на покупку»: amount — сумма в рублях;
- advice — советы: где сэкономить, что не так, как тратить меньше;
- unknown — вопрос не про траты и доходы.

Правила:
- categories — только названия из списка ниже (категория или подкатегория), как они написаны. «Такси» — это подкатегория, бери её.
- Магазин, сервис или место, которого нет среди категорий («Пятёрочка», «Озон», «Яндекс»), пиши в place.
- Даты — ГГГГ-ММ-ДД, включительно. Нет срока в вопросе — from и to null (значит текущий период).
- «в этом месяце» — текущий период; «в прошлом месяце» — прошлый период целиком; «за неделю» — последние 7 дней включая сегодня; «вчера» — вчерашний день; названный месяц — весь этот месяц.
- compare: from/to — основной отрезок, compareFrom/compareTo — с чем сравнить; если сравнивают «с прошлым месяцем» без уточнений — оставь compareFrom и compareTo null.
- Если вопрос уточняет предыдущий («а в сентябре?», «а на такси?») — возьми предыдущий запрос и поменяй только то, что уточнили.

Категории пользователя:
$cats

Примеры:
«сколько я потратил на кафе в этом месяце» → {"intent":"total","categories":["Кафе и рестораны"]}
«топ 3 покупки за неделю» → {"intent":"top","limit":3,"from":"${today.minusDays(6)}","to":"$today"}
«где я больше всего трачу на продукты» → {"intent":"places","categories":["Продукты"]}
«могу ли я купить наушники за 15 тысяч» → {"intent":"afford","amount":15000}
""".trim()
    }

    /** Текст пользователя для шага 1: последние вопросы с их запросами и новый вопрос. */
    fun queryUser(question: String, history: List<AssistantTurn>): String = buildString {
        val recent = history.takeLast(HISTORY)
        if (recent.isNotEmpty()) {
            appendLine("Предыдущие вопросы:")
            recent.forEach { appendLine("«${it.question}» → ${it.queryJson}") }
            appendLine()
        }
        append("Вопрос: $question")
    }

    const val ANSWER_SYSTEM = "Ты дружелюбный личный финансовый помощник в приложении учёта трат. " +
        "Тебе дают вопрос пользователя и данные, которые уже посчитало приложение. Ответь на русском, 2–4 коротких предложения. " +
        "Используй только цифры из данных, ничего не досчитывай и не придумывай. Если данных не хватает — честно скажи, чего не хватает. " +
        "Если просят совет — дай один-два конкретных и выполнимых. Тон спокойный и поддерживающий, без упрёков. " +
        "Без markdown, без списков, без приветствий."

    fun answerUser(question: String, facts: String): String =
        "Вопрос: $question\n\nДанные, посчитанные приложением:\n$facts"

    /** Ответ без обрамляющих пробелов и звёздочек markdown. */
    fun clean(answer: String): String = answer.replace("**", "").replace("__", "").trim()
}
