package ru.my.traty.ai

/**
 * Вид магазина или места без собственного названия: «зоомагазин», «аптека», «заправка».
 * Это не место (place), а подсказка категории и подкатегории.
 */
data class ShopType(val category: String, val emoji: String, val subcategory: String?)

/** Найденный во фразе вид магазина и где он стоит в тексте. */
data class ShopTypeMatch(val type: ShopType, val range: IntRange)

object ShopTypes {
    private fun t(category: String, emoji: String, sub: String?) = ShopType(category, emoji, sub)

    private val pets = "Питомцы" to "🐾"
    private val health = "Здоровье" to "💊"
    private val food = "Продукты" to "🛒"

    // Основа слова → вид. Порядок важен: «ветаптека» раньше «аптеки».
    private val table: List<Pair<Regex, ShopType>> = listOf(
        "зоомагазин\\p{L}*|зоо\\s+магазин\\p{L}*|зоотовар\\p{L}*|зоолавк\\p{L}*" to t(pets.first, pets.second, "Зоомагазин"),
        "ветаптек\\p{L}*|ветклиник\\p{L}*|ветеринар\\p{L}*|ветеринарк\\p{L}*" to t(pets.first, pets.second, "Ветеринар"),
        "аптек\\p{L}*" to t(health.first, health.second, "Аптека"),
        "стоматолог\\p{L}*" to t(health.first, health.second, "Стоматология"),
        "продуктов\\p{L}*|супермаркет\\p{L}*|гипермаркет\\p{L}*" to t(food.first, food.second, "Супермаркет"),
        "рын(?:ок|ке|ка)" to t(food.first, food.second, "Рынок"),
        "пекарн\\p{L}*" to t(food.first, food.second, "Пекарня"),
        "кофейн\\p{L}*" to t("Кафе и рестораны", "☕", "Кофе"),
        "кафе|ресторан\\p{L}*" to t("Кафе и рестораны", "☕", null),
        "строительн\\p{L}*|стройматериал\\p{L}*" to t("Жильё", "🏠", "Ремонт"),
        "хозяйственн\\p{L}*|хозмаг\\p{L}*" to t("Дом и быт", "🧴", null),
        "заправк\\p{L}*|азс" to t("Авто", "🚗", "Бензин"),
        "автомойк\\p{L}*" to t("Авто", "🚗", "Мойка"),
        "парикмахерск\\p{L}*|барбершоп\\p{L}*" to t("Красота и уход", "💇", "Парикмахерская"),
        "кинотеатр\\p{L}*" to t("Развлечения", "🎬", "Кино"),
        "книжн\\p{L}*" to t("Образование", "📚", "Книги"),
    ).map { (stem, type) ->
        // «в зоомагазине», «из аптеки», «строительный магазин» — предлог и слово «магазин» вырезаются вместе с видом
        Regex("""(?<![\p{L}])(?:(?:в|во|из|на)\s+)?(?:$stem)(?:\s+магазин\p{L}*)?(?![\p{L}])""", RegexOption.IGNORE_CASE) to type
    }

    // Слова о покупке, по которым видна категория, но которые остаются в заметке: «лекарство для кошки».
    private val hints: List<Pair<Regex, ShopType>> = listOf(
        "кош[кеи]\\p{L}*|кот[ауёеы]?|котён\\p{L}*|котен\\p{L}*|собак\\p{L}*|пс[аую]|щен\\p{L}*|питом\\p{L}*|попуга\\p{L}*|хомяк\\p{L}*" to
            t(pets.first, pets.second, null),
        "корм\\p{L}*|наполнител\\p{L}*|лоток\\p{L}*" to t(pets.first, pets.second, "Корм"),
        "лекарств\\p{L}*|таблет\\p{L}*" to t(health.first, health.second, "Аптека"),
    ).map { (stem, type) -> Regex("""(?<![\p{L}])(?:$stem)(?![\p{L}])""", RegexOption.IGNORE_CASE) to type }

    /** Первый вид магазина во фразе. */
    fun find(text: String): ShopTypeMatch? =
        table.mapNotNull { (re, type) -> re.find(text)?.let { ShopTypeMatch(type, it.range) } }.minByOrNull { it.range.first }

    /** Вся строка — вид магазина без названия («Зоомагазин», «аптека»). */
    fun whole(text: String): ShopType? {
        val s = text.trim()
        return table.firstOrNull { (re, _) -> re.matchEntire(s) != null }?.second
    }

    /** Категория по словам о покупке; питомцы важнее лекарств: «лекарство для кошки» — это питомец. */
    fun hint(text: String): ShopType? {
        val found = hints.filter { (re, _) -> re.containsMatchIn(text) }.map { it.second }
        if (found.isEmpty()) return null
        val petSub = found.firstOrNull { it.category == pets.first && it.subcategory != null }
        return petSub ?: found.first()
    }
}

/** Маркетплейс во фразе («на Озоне», «Озону», «ВБ») — как в промпте: категория «Маркетплейсы», площадка в подкатегории и месте. */
object Marketplaces {
    const val CATEGORY = "Маркетплейсы"
    const val EMOJI = "📦"

    data class Match(val name: String, val range: IntRange)

    private val table: List<Pair<Regex, String>> = listOf(
        "озон\\p{L}*|ozon" to "Ozon",
        "wildberries|вайлдберриз\\p{L}*|вайлдбериз\\p{L}*|вб|wb" to "Wildberries",
        "алиэкспресс\\p{L}*|aliexpress" to "AliExpress",
        "яндекс\\s+маркет\\p{L}*" to "Яндекс Маркет",
        "сбермегамаркет\\p{L}*|мегамаркет\\p{L}*" to "Мегамаркет",
    ).map { (stem, name) ->
        Regex("""(?<![\p{L}])(?:(?:на|в|во|с|со)\s+)?(?:$stem)(?![\p{L}])""", RegexOption.IGNORE_CASE) to name
    }

    fun find(text: String): Match? =
        table.mapNotNull { (re, name) -> re.find(text)?.let { Match(name, it.range) } }.minByOrNull { it.range.first }
}
