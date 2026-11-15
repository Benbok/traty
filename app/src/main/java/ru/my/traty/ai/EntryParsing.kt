package ru.my.traty.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.IncomeSources
import ru.my.traty.data.Rule
import java.io.IOException
import java.time.LocalDate

enum class EntryType { EXPENSE, INCOME }

/** Ошибка HTTP от языковой модели (в отличие от сетевой — обрыва, отсутствия сети). */
class LlmHttpException(val code: Int, message: String) : IOException(message)

/** Одна операция из фразы: трата или пополнение. [type] — сырая строка от модели. */
@Serializable
data class ParsedEntry(
    val type: String = "expense",
    val amount: Double = 0.0,
    val category: String = DefaultCategories.OTHER,
    val emoji: String? = null,
    val subcategory: String? = null,
    val place: String? = null,
    val note: String? = null,
    /** Только для income: источник из [IncomeSources.ALL]. */
    val source: String? = null,
    /** YYYY-MM-DD или null (= день, когда фраза произнесена). */
    val date: String? = null,
    /** Покупка частями: аванс, рассрочка, кредит, доплата. null — обычная трата. */
    val part: PurchaseTerms? = null,
) {
    /** null — тип неизвестен (например, «goal_deposit» из будущих этапов). */
    val entryType: EntryType?
        get() = when (type.trim().lowercase()) {
            "expense" -> EntryType.EXPENSE
            "income" -> EntryType.INCOME
            else -> null
        }
}

@Serializable
private data class EntryParseResult(val items: List<ParsedEntry> = emptyList())

object EntryPrompt {
    fun system(categories: List<CategoryWithSubs>, today: LocalDate, rules: List<Rule>): String = buildString {
        appendLine("Ты разбираешь короткие фразы о деньгах на русском языке и отвечаешь строго в формате JSON.")
        appendLine("В одной фразе может быть несколько операций, разного типа: траты (expense) и пополнения (income).")
        appendLine("Сегодня: $today (${today.dayOfWeek}).")
        appendLine("Доступные категории трат (категория: подкатегории):")
        categories.forEach { c -> appendLine("- ${c.category.name}: ${c.subNames().joinToString(", ")}") }
        appendLine("Источники пополнений: ${IncomeSources.ALL.joinToString(", ") { it.first }}.")
        appendLine()
        appendLine(
            """Формат ответа: {"items":[{"type":"expense" или "income","amount":число в рублях,"category":"название из списка или новая категория (для expense)","emoji":"один эмодзи для новой категории или null","subcategory":"из списка или новая подкатегория или null","place":"магазин/место или null","note":"что куплено и для кого, словами из фразы, или null","source":"источник из списка (для income) или null","date":"YYYY-MM-DD или null","part":null или {"kind":"partial|installment|loan|topup","total":полная цена в рублях или null,"count":число платежей или null,"rate":процент годовых или null,"payment":платёж по договору в рублях или null,"period":"month|week|2weeks" или null}}]}"""
        )
        appendLine("Правила:")
        appendLine("- type = income, если деньги пришли: зарплата, аванс, дежурства, подработка, перевод мне, вернули долг, кэшбэк. Всё остальное — expense.")
        appendLine("- Оплата покупки или услуги — всегда expense, даже со словами «аванс», «предоплата», «доплата», «пополнение», «плюс»: «аванс за холодильник, купленный на Озоне, 20 тысяч» → expense 20000; «пополнение за холодильник Озону плюс 5000» → expense 5000 (доплата за ту же покупку). Как income «аванс» — только аванс по зарплате («пришёл аванс», «аванс за ноябрь»).")
        appendLine("- Числа словами переводи в цифры: «двести пятьдесят» → 250, «полторы тысячи» → 1500, «тыща», «косарь» → 1000, «пятихатка» → 500, «восемьдесят тысяч» → 80000.")
        appendLine("- В разговорной речи слово «тысяча» часто пропускают: «две сто» → 2100, «две пятьсот» → 2500, «три двести» → 3200, «двадцать пять триста» → 25300. Если за числом идёт название сотен («сто», «двести», … «девятьсот»), первое число — тысячи. Но «сто пятьдесят» — это 150, а «двадцать пять» без сотен после — это 25.")
        appendLine("- Если в фразе несколько операций — верни несколько элементов.")
        appendLine("- Если названо несколько магазинов со своими суммами («Пятёрочка 1200, строительный Стройландия 3500, Магнит 800») — отдельный элемент на каждый магазин, со своей суммой и категорией.")
        appendLine("- Несколько товаров из одной покупки («в продуктовом на 2500 купил рыбу, консерву, ещё что-то») — это ОДНА трата, а не трата на каждый товар: amount — названная общая сумма, note — товары через запятую, словами из фразы («рыбу, консерву»). Пустые слова вроде «ещё что-то» в note не пиши. Если у товаров названы цены, пиши цену после товара: «рыба 500 ₽, хлеб 80 ₽», а amount всё равно бери из общей суммы.")
        appendLine("- Цены товаров не складывай сам. Если общая сумма не названа, а у товаров есть цены, верни каждый товар отдельным элементом с его ценой, одинаковыми category, place и date — приложение соберёт их в одну трату.")
        appendLine("- place — только собственное название магазина или места, с заглавной буквы, без слов «магазин», «супермаркет», «строительный» и без кавычек: «магазин Пятёрочка» → «Пятёрочка», «строительный Стройландия» → «Стройландия». Пиши название так, как оно обычно пишется (Пятёрочка, Магнит, ВкусВилл, Леруа Мерлен). Если место не названо — null.")
        appendLine("- Если покупка явно совершена на маркетплейсе, используй категорию «Маркетплейсы» независимо от вида товара; для новой категории emoji = «📦». Площадку укажи одновременно в subcategory и place, а сам товар — кратко в note.")
        appendLine("- Нормализуй названия площадок: Ozon/Озон → «Ozon»; Wildberries/WB/ВБ/Вайлдберриз → «Wildberries»; AliExpress/Алиэкспресс → «AliExpress»; Яндекс Маркет → «Яндекс Маркет»; Мегамаркет/СберМегаМаркет → «Мегамаркет».")
        appendLine("- Применяй правило маркетплейсов только если из фразы ясно, что это площадка покупки, а не просто упоминание бренда или названия в другом смысле. Если площадка не распознана однозначно — не выдумывай её и используй обычную категорию по товару.")
        appendLine("- Тип магазина подсказывает категорию: продуктовый — «Продукты», строительный — ремонт/дом, аптека — «Здоровье», зоомагазин и ветклиника — «Питомцы».")
        appendLine("- Вид магазина без собственного названия («зоомагазин», «аптека», «продуктовый», «заправка») — это не place, place = null. Используй его для category и subcategory.")
        appendLine("- «вчера», «позавчера», «в понедельник» переводи в дату относительно сегодняшней; если дата не названа — null.")
        appendLine("- Для expense категорию по возможности бери из списка (название пиши точно как в списке, emoji = null).")
        appendLine("- Если ни одна категория явно не подходит — предложи новую: короткое общее название (1–2 слова, с заглавной буквы, во множественном числе, как в списке) и подходящий emoji. Не создавай новую категорию, если подходит существующая.")
        appendLine("- subcategory — уточнение внутри выбранной категории: вид магазина или вид траты. Бери из подкатегорий этой категории (точно как в списке). Если подходящей нет, а из фразы уточнение понятно — предложи новую: 1–2 слова, с заглавной буквы, как в списке («Зоомагазин», «Ветаптека»). Не повторяй в subcategory название категории. Подкатегорию из другой категории не бери.")
        appendLine("- Если подходит новая подкатегория, но ни одна категория не подходит ей по смыслу — предложи новую категорию, а не «${DefaultCategories.OTHER}».")
        appendLine("- note — всё, что сказано о покупке, кроме суммы, даты, места и вида магазина. Сохраняй слова из фразы и имена как сказаны, не сокращай и не пересказывай: «лекарство для кошки Чуни от блох». Не добавляй в note название места или категории. Если о покупке больше ничего не сказано — null.")
        appendLine("- Если непонятно, на что потрачено, — «${DefaultCategories.OTHER}».")
        appendLine("- part заполняй только для покупки частями: аванс или предоплата за товар — partial; рассрочка, «Долями», «Сплит» — installment; покупка в кредит — loan; доплата или очередной платёж за уже начатую покупку — topup. Для обычной траты и для income part = null.")
        appendLine("- У покупки частями amount — сколько заплачено сейчас. Если сейчас ничего не платили (взял в кредит, первый платёж позже) — amount = 0, а цену укажи в part.total. note — сам товар («холодильник»).")
        appendLine("- В part переноси только сказанное: total — полная цена; count — число платежей («на 6 месяцев» → 6, «на год» → 12); rate — процент годовых («под 20 процентов» → 20); payment — платёж по договору; period — month, week или 2weeks («Долями» — 4 платежа раз в 2 недели). Чего нет во фразе — null. Ничего не вычисляй.")
        appendLine("- Для income поле source — строго одно из списка источников; если непонятно, откуда деньги, — «${IncomeSources.DEFAULT}».")
        appendLine("- Никакого текста, кроме JSON.")
        appendLine()
        appendLine("Пример: «зоомагазин вчера 731 лекарство для кошки Чуни от блох» → ")
        appendLine("""{"items":[{"type":"expense","amount":731,"category":"Питомцы","emoji":null,"subcategory":"Зоомагазин","place":null,"note":"лекарство для кошки Чуни от блох","source":null,"date":"${today.minusDays(1)}"}]}""")
        appendLine("Пример: «взял телевизор в рассрочку на 6 месяцев за 60 тысяч, первый платёж 10 тысяч» → ")
        appendLine("""{"items":[{"type":"expense","amount":10000,"category":"Дом и быт","emoji":null,"subcategory":"Техника","place":null,"note":"телевизор","source":null,"date":null,"part":{"kind":"installment","total":60000,"count":6,"rate":null,"payment":null,"period":"month"}}]}""")
        appendLine("Пример: «в продуктовом на 2500 купил рыбу, консерву, хлеб за 80» → ")
        appendLine("""{"items":[{"type":"expense","amount":2500,"category":"Продукты","emoji":null,"subcategory":"Супермаркет","place":null,"note":"рыбу, консерву, хлеб 80 ₽","source":null,"date":null}]}""")
        if (rules.isNotEmpty()) {
            appendLine()
            appendLine("Выученные правила (место/слово → категория; применяй если совпало):")
            rules.forEach { r -> appendLine("- «${r.pattern}» → categoryId=${r.categoryId}") }
        }
    }
}

object EntryParsing {
    suspend fun parse(
        client: LlmClient,
        text: String,
        categories: List<CategoryWithSubs>,
        rules: List<Rule>,
        today: LocalDate,
    ): List<ParsedEntry> {
        val raw = client.complete(EntryPrompt.system(categories, today, rules), text, jsonMode = true)
        return decode(raw)
    }

    /** Модели иногда оборачивают JSON в ```json ... ``` или возвращают голый массив — обрабатываем оба случая. */
    internal fun decode(raw: String): List<ParsedEntry> {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arrStart = cleaned.indexOf('[')
        val objStart = cleaned.indexOf('{')
        val items = if (arrStart != -1 && (objStart == -1 || arrStart < objStart)) {
            val arr = cleaned.substring(arrStart, cleaned.lastIndexOf(']') + 1)
            lenientJson.decodeFromString(ListSerializer(ParsedEntry.serializer()), arr)
        } else {
            val obj = cleaned.substring(objStart, cleaned.lastIndexOf('}') + 1)
            lenientJson.decodeFromString(EntryParseResult.serializer(), obj).items
        }
        // Покупка в кредит без оплаты сегодня приходит с amount = 0, но с ценой: её не теряем
        return items.filter { it.amount > 0 || (it.entryType == EntryType.EXPENSE && (it.part?.total ?: 0.0) > 0) }.map {
            if (it.entryType == EntryType.INCOME) it.copy(source = IncomeSources.normalize(it.source)) else it
        }
    }
}

/**
 * Запасной разбор без ИИ: одна операция; тип — по словам «зарплата / аванс / пришло / вернули…».
 * «аванс за холодильник», «доплата» — трата ([PurchasePayment]).
 * После него всегда нужно подтверждение.
 */
object OfflineEntryParser {
    private val incomeWords = Regex("""зарплат|аванс|дежур|пришл|вернул|возврат|подработ|кэшбэк|кешбэк""")

    fun parse(text: String, categories: List<CategoryWithSubs>, today: LocalDate = LocalDate.now()): List<ParsedEntry> {
        if (incomeWords.containsMatchIn(text.lowercase()) && !PurchasePayment.isPurchasePayment(text)) {
            val day = SpokenDateParser.find(text, today)?.date?.toString()
            return OfflineIncomeParser.parse(text).map {
                ParsedEntry(type = "income", amount = it.amount, source = it.source, note = it.note, date = it.date ?: day)
            }
        }
        return OfflineParser.parse(text, categories, today).map {
            ParsedEntry(
                type = "expense", amount = it.amount, category = it.category, emoji = it.emoji,
                subcategory = it.subcategory, place = it.place, note = it.note, date = it.date,
                part = PurchaseTermsParser.find(text),
            )
        }
    }
}
