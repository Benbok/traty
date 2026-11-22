package ru.my.traty.assistant

import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.period.BillingPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Один прошлый обмен репликами — чтобы понимать «а в сентябре?».
 * [expenseIds] — траты, которые показал ответ: «удали их» относится ровно к ним. В ИИ они не уходят.
 */
data class AssistantTurn(val question: String, val queryJson: String, val expenseIds: List<Long> = emptyList(), val shownProductKeys: List<String> = emptyList())

/** Тексты запросов к ИИ: шаг 1 — вопрос в JSON, шаг 2 — посчитанные цифры в человеческий ответ. */
object AssistantPrompts {
    private val RU: Locale = Locale.forLanguageTag("ru")

    /** Сколько прошлых вопросов передаём для уточнений. */
    const val HISTORY = 2

    fun querySystem(categories: List<CategoryWithSubs>, today: LocalDate, startDay: Int): String {
        val period = BillingPeriod.containing(today, startDay)
        val previous = period.previous(startDay)
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, RU)
        val october = YearMonth.of(if (today.monthValue >= 10) today.year else today.year - 1, 10)
        val cats = categories.joinToString("\n") { c ->
            val subs = c.subNames()
            "- ${c.category.name}" + if (subs.isNotEmpty()) " (подкатегории: ${subs.joinToString(", ")})" else ""
        }
        return """
Ты переводишь вопрос пользователя о его личных финансах в JSON-запрос к данным. Сам ничего не считаешь и не отвечаешь.
Сегодня $today, $weekday. Текущий расчётный период: ${period.startDate} — ${period.endDate}. Прошлый: ${previous.startDate} — ${previous.endDate}.

Верни только JSON-объект без пояснений:
{"intent": "...", "categories": [], "place": null, "from": null, "to": null, "compareFrom": null, "compareTo": null, "amount": null, "limit": null, "relative": false, "groupBy": null, "all": false, "target": null, "percent": false, "productReport": null}

intent — одно из:
${IntentRegistry.promptLines()}

Правила:
- categories — только названия из списка ниже (категория или подкатегория), как они написаны. «Такси» — это подкатегория, бери её.
- Широкая тема расходов — родительская категория целиком со всеми подкатегориями. Не сужай «сколько потратил на кошку/питомца» до «Груминг»: если есть «Питомцы», выбери её, включая ветеринара, корм, зоомагазины и груминг. Только явно названная услуга («на груминг кошки», «на ветеринара») ограничивает подкатегорию. Не добавляй place, если пользователь не назвал конкретный магазин.
- Не теряй явно названный товар или имя животного: «корм для кошки» — это корм, «на кошку Чуню» — поиск по словам «Чуня», а не все питомцы.
- Сохраняй вкус, сорт, марку и фасовку, если они явно названы в вопросе о товаре. «Шоколадный молочный коктейль» передай как одну фразу в categories.
- Товар или услуга, которых нет среди категорий («шаурма», «бензин», «шоколад», «молоко»), — тоже в categories, как сказал пользователь: приложение поищет их в заметках и в списках товаров внутри покупок. В этом случае сумма будет посчитана только по ценам совпавших позиций.
- Для product_report названия отдельных товаров передавай только в productReport.items. В categories оставляй только явный фильтр по категории. При уточнении используй followUp и оставь неизменяемые поля пустыми.
- Магазин, сервис или место, которого нет среди категорий («Пятёрочка», «Озон», «Яндекс»), пиши в place.
- Даты — ГГГГ-ММ-ДД, включительно. Нет срока в вопросе — from и to null (значит текущий период).
- «в этом месяце» — текущий период; «в прошлом месяце» — прошлый период целиком; «за неделю» — последние 7 дней включая сегодня; «вчера» — вчерашний день; названный месяц — весь этот месяц; «с ноября по декабрь» — с начала первого месяца до конца второго; «за последний год» — последние 12 месяцев; «за последний квартал» — последние 3 месяца; «в прошлом квартале» — прошлый календарный квартал.
- compare: from/to — основной отрезок, compareFrom/compareTo — с чем сравнить; если сравнивают «с прошлым месяцем» без уточнений — оставь compareFrom и compareTo null.
- Просьба составить или показать план трат на будущее («план трат на месяц», «бюджет на месяц», «какие лимиты поставить») — это suggest_plan. Вопрос о выполнении уже заданного плана («как я иду по плану», «сколько осталось по плану», «где перерасход») — это plan. Общий «где сэкономить» — advice.
- Если вопрос уточняет предыдущий («а в сентябре?», «а на такси?») — возьми предыдущий запрос и поменяй только то, что уточнили.
- «Эти», «их», «все», «они» относятся к тому, о чём был предыдущий ответ: «удали эти платежи» после вопроса о рассрочках — это delete_debt, а не отмена подписки.

Категории пользователя:
$cats

Примеры:
«на какие товары я потратил больше в этом месяце, чем в прошлом» → {"intent":"product_report","productReport":{"metric":"spending","compare":true,"orderByChange":true,"direction":"increase"}}
«а какие из них я стал чаще покупать?» → {"intent":"product_report","productReport":{"followUp":true,"shownOnly":true,"metric":"frequency","direction":"increase"}}
«сколько я потратил на кафе в этом месяце» → {"intent":"total","categories":["Кафе и рестораны"]}
«сколько я потратил на такси в октябре» → {"intent":"total","categories":["Такси"],"from":"${october.atDay(1)}","to":"${october.atEndOfMonth()}"}
«сколько я потратил на шоколадки» → {"intent":"total","categories":["шоколадки"]}
«сколько литров молока я купил в этом месяце» → {"intent":"total","categories":["молоко"]}
«сколько коктейлей я купил в октябре» → {"intent":"total","categories":["коктейли"],"from":"${october.atDay(1)}","to":"${october.atEndOfMonth()}"}
«как менялись траты на продукты по месяцам» → {"intent":"trend","categories":["Продукты"],"groupBy":"month"}
«в какой день недели я трачу больше всего» → {"intent":"trend","groupBy":"weekday"}
«когда я последний раз был в кафе» → {"intent":"list","categories":["Кафе и рестораны"]}
«топ 3 покупки за неделю» → {"intent":"top","limit":3,"from":"${today.minusDays(6)}","to":"$today"}
«где я больше всего трачу на продукты» → {"intent":"places","categories":["Продукты"]}
«могу ли я купить наушники за 15 тысяч» → {"intent":"afford","amount":15000}
«хватит ли на ресторан за 4000» → {"intent":"afford","amount":4000,"categories":["Кафе и рестораны"]}
«сколько осталось по плану на продукты» → {"intent":"plan","categories":["Продукты"]}
«составь мне план трат на месяц» → {"intent":"suggest_plan"}
«план трат на месяц» → {"intent":"suggest_plan"}
«поставь лимит на кафе 8 тысяч» → {"intent":"set_plan","categories":["Кафе и рестораны"],"amount":8000}
«увеличь лимит на кафе на 1000» → {"intent":"set_plan","categories":["Кафе и рестораны"],"amount":1000,"relative":true}
«уменьши план на продукты на 500» → {"intent":"set_plan","categories":["Продукты"],"amount":-500,"relative":true}
«подними лимит на кафе на 10 процентов» → {"intent":"set_plan","categories":["Кафе и рестораны"],"amount":10,"relative":true,"percent":true}
«убери лимит на кафе» → {"intent":"remove_plan","categories":["Кафе и рестораны"]}
«отмени подписку на Яндекс Плюс» → {"intent":"cancel_subscription","place":"Яндекс Плюс"}
«поставь Netflix на паузу» → {"intent":"pause_subscription","place":"Netflix"}
«возобнови Netflix» → {"intent":"resume_subscription","place":"Netflix"}
«Spotify подорожал до 300 рублей» → {"intent":"edit_subscription","place":"Spotify","amount":300}
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
        "Если спрашивают, сколько товара куплено (штук, литров, килограммов), отвечай по строке «Количество» из данных, а сумму назови вторым числом. " +
        "Если просят совет — дай один-два конкретных и выполнимых. Тон спокойный и поддерживающий, без упрёков. " +
        "Если в данных есть предложение плана трат, кратко объясни, из чего оно получилось, и скажи, что строки можно отметить и добавить в план кнопкой под ответом; сам план ты не меняешь. " +
        "Если в данных предложено убрать или изменить план, отменить, приостановить, возобновить или изменить подписку, удалить, закрыть или отменить покупку частями, удалить траты или перенести их в другую категорию, скажи, что именно изменится, и что пока ничего не сделано — нужно нажать кнопку подтверждения под ответом. " +
        "Про отмену подписки обязательно добавь, если это есть в данных: приложение убирает её только из списка, а в самом сервисе отписаться нужно самому. " +
        "Если в данных сказано, что подходит несколько вариантов или чего-то не хватает, задай один уточняющий вопрос. " +
        "Без markdown, без списков, без приветствий."

    fun answerUser(question: String, facts: String): String =
        "Вопрос: $question\n\nДанные, посчитанные приложением:\n$facts"

    /** Ответ без обрамляющих пробелов и звёздочек markdown. */
    fun clean(answer: String): String = answer.replace("**", "").replace("__", "").trim()
}
