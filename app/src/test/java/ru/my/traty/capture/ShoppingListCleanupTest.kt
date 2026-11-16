package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.my.traty.ai.EntryParsing
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Subcategory
import java.time.LocalDate

class ShoppingListCleanupTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), listOf(Subcategory(10, 1, "Супермаркет"))),
        CategoryWithSubs(Category(id = 2, name = "Транспорт", emoji = "🚕"), listOf(Subcategory(20, 2, "Такси"))),
        CategoryWithSubs(Category(id = 4, name = "Прочее", emoji = "📦"), emptyList()),
    )

    private fun clean(entries: List<ParsedEntry>, text: String) = EntryCleanup.apply(entries, text, cats, today)

    private fun food(amount: Double, note: String?, place: String? = null, sub: String? = "Супермаркет") =
        ParsedEntry(amount = amount, category = "Продукты", subcategory = sub, place = place, note = note)

    @Test
    fun goodAnswerIsOneExpenseWithList() {
        val text = "в продуктовом на 2500 купил рыбу, консерву, ещё что-то"
        val ai = EntryParsing.decode(
            """{"items":[{"type":"expense","amount":2500,"category":"Продукты","subcategory":"Супермаркет","place":null,"note":"рыбу, консерву","date":null}]}"""
        )
        val e = clean(ai, text).single()
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("рыбу, консерву", e.note)
        assertEquals("Супермаркет", e.subcategory)
    }

    @Test
    fun splitAnswerIsMergedAndTotalTaken() {
        val text = "в продуктовом на 2500 купил рыбу, консерву, хлеб"
        val e = clean(listOf(food(1500.0, "рыбу"), food(600.0, "консерву"), food(400.0, "хлеб")), text).single()
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("Продукты", e.category)
        assertEquals("рыбу, консерву, хлеб", e.note)
    }

    @Test
    fun pricesWithoutTotalAreAddedByApp() {
        val text = "в Пятёрочке хлеб 80, молоко 90"
        val e = clean(listOf(food(80.0, "хлеб", "Пятёрочка"), food(90.0, "молоко", "Пятёрочка")), text).single()
        assertEquals(170.0, e.amount, 0.0)
        assertEquals("Пятёрочка", e.place)
        assertEquals("хлеб 80 ₽, молоко 90 ₽", e.note)
    }

    @Test
    fun mergeWithoutCommasUsesAiItems() {
        val text = "в пятёрочке хлеб 80 молоко 90"
        val e = clean(listOf(food(80.0, "хлеб", "Пятёрочка"), food(90.0, "молоко", "Пятёрочка")), text).single()
        assertEquals(170.0, e.amount, 0.0)
        assertEquals("хлеб 80 ₽, молоко 90 ₽", e.note)
    }

    @Test
    fun mergeTrustsAiPricesAndNamesOverMessyPhrase() {
        // Текст GigaAM с телефона: точки и запятые стоят как попало, марка отделена от товара.
        // ИИ разобрал верно — товары и цены берутся из его ответа, а не из разбора фразы.
        val text = "Те Национальная гречка фасованная 900 г, — 83—90. Горошек зелёный — 99 ₽. " +
            "Фруктовый сок яблочный, мооя цена — 69,90, Оливки зелёные — 91—90"
        val ai = listOf(
            food(83.9, "гречка фасованная 900 г"),
            food(99.0, "горошек зелёный"),
            food(69.9, "сок яблочный «Моя цена»"),
            food(91.9, "оливки зелёные"),
        )
        val e = clean(ai, text).single()
        assertEquals(344.7, e.amount, 1e-9)
        assertEquals(
            listOf("гречка фасованная 900 г", "горошек зелёный", "сок яблочный «Моя цена»", "оливки зелёные"),
            e.items!!.map { it.name },
        )
        assertEquals(listOf(83.9, 99.0, 69.9, 91.9), e.items!!.map { it.price })
        assertEquals("гречка фасованная 900 г 83,90 ₽, горошек зелёный 99 ₽, сок яблочный «Моя цена» 69,90 ₽, оливки зелёные 91,90 ₽", e.note)
    }

    @Test
    fun mergeFallsBackToPhraseWhenAiGaveNoNames() {
        // У трат от ИИ нет названий товаров — товары по-прежнему берутся из самой фразы
        val text = "в Пятёрочке хлеб 80, молоко 90"
        val e = clean(listOf(food(80.0, null, "Пятёрочка"), food(90.0, null, "Пятёрочка")), text).single()
        assertEquals(listOf("хлеб", "молоко"), e.items!!.map { it.name })
        assertEquals("хлеб 80 ₽, молоко 90 ₽", e.note)
    }

    @Test
    fun lostPricesAreRestoredInNote() {
        val text = "в продуктовом рыба 500, хлеб 80, всего 2500"
        val e = clean(listOf(food(2500.0, "рыба, хлеб")), text).single()
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("рыба 500 ₽, хлеб 80 ₽", e.note)
    }

    @Test
    fun aiSummedInsteadOfNamedTotal() {
        val text = "в продуктовом рыба 500, хлеб 80, всего 2500"
        val e = clean(listOf(food(580.0, "рыба 500 ₽, хлеб 80 ₽")), text).single()
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("рыба 500 ₽, хлеб 80 ₽", e.note)
    }

    @Test
    fun differentShopsStaySeparate() {
        val text = "Пятёрочка 1200, Магнит 800"
        val out = clean(listOf(food(1200.0, null, "Пятёрочка"), food(800.0, null, "Магнит")), text)
        assertEquals(2, out.size)
    }

    @Test
    fun differentCategoriesStaySeparate() {
        val text = "хлеб 80, такси 300"
        val taxi = ParsedEntry(amount = 300.0, category = "Транспорт", subcategory = "Такси")
        assertEquals(2, clean(listOf(food(80.0, "хлеб", sub = null), taxi), text).size)
    }

    @Test
    fun ordinaryPhraseUntouched() {
        val text = "зоомагазин вчера 731 лекарство для кошки Чуни от блох"
        val e = ParsedEntry(amount = 731.0, category = "Питомцы", subcategory = "Зоомагазин", note = "лекарство для кошки Чуни от блох", date = "2026-11-14")
        assertEquals(listOf(e), ShoppingListCleanup.apply(listOf(e), text))
    }

    @Test
    fun installmentIsNotAList() {
        val text = "взял телевизор и подставку в рассрочку на 6 месяцев, первый платёж 10 тысяч"
        val e = ParsedEntry(amount = 10000.0, category = "Дом и быт", note = "телевизор и подставку")
        assertEquals(listOf(e), ShoppingListCleanup.apply(listOf(e), text))
    }
}
