package ru.my.traty.assistant

/** Вопрос только читает данные; команда что-то меняет, но только после кнопки подтверждения. */
enum class IntentKind { QUESTION, COMMAND }

/**
 * Одно намерение ассистента: ключ в JSON, вид и строка для промпта шага 1.
 * Список [IntentRegistry.ALL] — единственное место, где перечислены намерения: из него собираются [Intents.ALL],
 * строки промпта и группы вроде «про покупки частями». Новое намерение добавляется сюда.
 */
data class IntentSpec(val key: String, val kind: IntentKind, val hint: String, val group: String? = null) {
    /** Строка списка намерений в промпте. */
    fun promptLine(last: Boolean): String = "- $key — $hint" + if (last) "." else ";"
}

object IntentRegistry {
    const val DEBT = "debt"

    val ALL: List<IntentSpec> = listOf(
        IntentSpec(ru.my.traty.assistant.apartments.ApartmentIntents.READINGS, IntentKind.QUESTION, "последние показания счётчиков, заполни housing"),
        IntentSpec(ru.my.traty.assistant.apartments.ApartmentIntents.CONSUMPTION, IntentKind.QUESTION, "потребление воды, газа или электричества в физических единицах, заполни housing"),
        IntentSpec(ru.my.traty.assistant.apartments.ApartmentIntents.COMPARE, IntentKind.QUESTION, "сравни потребление ресурсов, без периода два последних завершённых интервала, заполни housing"),
        IntentSpec(ru.my.traty.assistant.apartments.ApartmentIntents.TREND, IntentKind.QUESTION, "история и динамика потребления ресурсов, заполни housing"),
        IntentSpec(Intents.TOTAL, IntentKind.QUESTION, """сколько потрачено (всего, на категорию, в месте)"""),
        IntentSpec(Intents.TOP, IntentKind.QUESTION, """самые крупные траты (limit — сколько, по умолчанию 5)"""),
        IntentSpec(Intents.BREAKDOWN, IntentKind.QUESTION, """на что ушли деньги, разбивка по категориям"""),
        IntentSpec(Intents.PLACES, IntentKind.QUESTION, """где (в каких магазинах, местах) больше всего тратится"""),
        IntentSpec(Intents.COMPARE, IntentKind.QUESTION, """сравнение двух отрезков («больше или меньше, чем в прошлом месяце», «почему вышло больше»)"""),
        IntentSpec(Intents.AVERAGE, IntentKind.QUESTION, """сколько в среднем в день или неделю"""),
        IntentSpec(Intents.TREND, IntentKind.QUESTION, """как менялись траты: «по месяцам», «по неделям», «в какой день недели я трачу больше», «динамика трат на кафе»: groupBy — month, week, weekday или day"""),
        IntentSpec(Intents.LIST, IntentKind.QUESTION, """сами покупки: «когда я последний раз был в кафе», «покажи траты в Пятёрочке», «как часто я езжу на такси», «что я купил вчера»"""),
        IntentSpec(Intents.SUBSCRIPTIONS, IntentKind.QUESTION, """подписки и регулярные платежи"""),
        IntentSpec(Intents.DEBTS, IntentKind.QUESTION, """покупки частями, рассрочки, кредиты и долги: «сколько я должен», «сколько осталось за холодильник», «когда платёж по рассрочке», «какая переплата по кредиту»: place — название покупки, если названо""", DEBT),
        IntentSpec(Intents.DELETE_DEBT, IntentKind.COMMAND, """«удали рассрочку за холодильник», «удали эти платежи, они ошибочные» (после ответа про покупки частями): place — название покупки; «эти», «их», «все» — all: true и place из предыдущего запроса""", DEBT),
        IntentSpec(Intents.DELETE_EXPENSE, IntentKind.COMMAND, """удалить сами траты: «удали последнюю трату», «удали траты в Пятёрочке за октябрь», «удали эти траты» (после ответа со списком): categories, place, from, to — какие траты; limit — сколько (последняя — 1)"""),
        IntentSpec(Intents.MOVE_EXPENSE, IntentKind.COMMAND, """«перенеси траты в Пятёрочке в Продукты», «поменяй категорию последней траты на Кафе»: target — категория или подкатегория из списка, КУДА переносить; categories и place — откуда (не клади туда target)"""),
        IntentSpec(Intents.CLOSE_DEBT, IntentKind.COMMAND, """«я выплатил рассрочку за телефон», «закрой кредит»: place — название покупки""", DEBT),
        IntentSpec(Intents.CANCEL_DEBT, IntentKind.COMMAND, """«вернул телевизор, отмени покупку»: place — название покупки""", DEBT),
        IntentSpec(Intents.INCOME, IntentKind.QUESTION, """доходы, поступления, «сколько заработал», «в плюсе ли я»"""),
        IntentSpec(Intents.AFFORD, IntentKind.QUESTION, """«могу ли я позволить / хватит ли денег на покупку»: amount — сумма в рублях; если покупка из понятной категории («сходить в кафе за 3000») — укажи её в categories"""),
        IntentSpec(Intents.PLAN, IntentKind.QUESTION, """план трат (бюджет, лимиты): «как я иду по плану», «сколько осталось на кафе по плану», «где перерасход»"""),
        IntentSpec(Intents.BALANCE, IntentKind.QUESTION, """сколько денег на счёте сейчас, «какой у меня баланс»"""),
        IntentSpec(Intents.SUGGEST_PLAN, IntentKind.COMMAND, """«предложи / составь план трат (бюджет, лимиты)», «какой лимит поставить на кафе»: categories — если названы"""),
        IntentSpec(Intents.SET_PLAN, IntentKind.COMMAND, """«поставь план на кафе 10 000», «лимит на такси 3 тысячи»: categories — одна категория или подкатегория из списка; если такой в списке нет — напиши слово пользователя как есть («Такси»), не подменяй его категорией; amount — сумма в рублях. «Увеличь / уменьши лимит на 1000» — это set_plan с relative: true (amount отрицательный при уменьшении); «измени лимит на 5000» и «подними до 5000» — обычный set_plan с новой суммой; «подними лимит на 10 процентов» — set_plan с relative: true, percent: true и amount = 10 (проценты, не рубли; при уменьшении минус)"""),
        IntentSpec(Intents.REMOVE_PLAN, IntentKind.COMMAND, """«убери / сними / отмени лимит (план) на кафе»: categories — категория или подкатегория, с плана которой снять"""),
        IntentSpec(Intents.CANCEL_SUB, IntentKind.COMMAND, """«отмени подписку на Яндекс Плюс», «удали подписку Netflix»: place — название подписки как сказал пользователь"""),
        IntentSpec(Intents.PAUSE_SUB, IntentKind.COMMAND, """«поставь Netflix на паузу», «приостанови подписку»: place — название"""),
        IntentSpec(Intents.RESUME_SUB, IntentKind.COMMAND, """«возобнови подписку Netflix», «сними с паузы»: place — название"""),
        IntentSpec(Intents.EDIT_SUB, IntentKind.COMMAND, """«подписка Яндекс подорожала до 400», «перенеси списание Netflix на 20 ноября»: place — название, amount — новая сумма в рублях, from — новая дата списания ГГГГ-ММ-ДД (только если названа)"""),
        IntentSpec(Intents.ADVICE, IntentKind.QUESTION, """советы: где сэкономить, что не так, как тратить меньше"""),
        IntentSpec(Intents.UNKNOWN, IntentKind.QUESTION, """вопрос не про траты и доходы"""),
    )

    private val byKey = ALL.associateBy { it.key }

    fun of(key: String): IntentSpec? = byKey[key]

    fun inGroup(group: String): Set<String> = ALL.filter { it.group == group }.map { it.key }.toSet()

    /** Намерения, которые что-то меняют после подтверждения. */
    val COMMANDS: Set<String> = ALL.filter { it.kind == IntentKind.COMMAND }.map { it.key }.toSet()

    /** Строки «- ключ — описание» для промпта. */
    fun promptLines(): String = ALL.mapIndexed { i, spec -> spec.promptLine(last = i == ALL.lastIndex) }.joinToString("\n")
}
