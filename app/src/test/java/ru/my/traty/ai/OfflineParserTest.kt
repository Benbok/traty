package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Subcategory
import java.time.LocalDate

class OfflineParserTest {
    private val today = LocalDate.of(2026, 11, 15) // воскресенье
    private val cats = listOf(
        CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), listOf(Subcategory(10, 1, "Супермаркет"))),
        CategoryWithSubs(Category(id = 2, name = "Здоровье", emoji = "💊"), listOf(Subcategory(20, 2, "Аптека"))),
        CategoryWithSubs(
            Category(id = 3, name = "Питомцы", emoji = "🐾"),
            listOf(Subcategory(30, 3, "Корм"), Subcategory(31, 3, "Ветеринар")),
        ),
        CategoryWithSubs(Category(id = 4, name = "Прочее", emoji = "📦"), emptyList()),
    )

    private fun parse(text: String, categories: List<CategoryWithSubs> = cats) =
        OfflineParser.parse(text, categories, today).single()

    @Test
    fun petShopPhraseKeepsWholeNote() {
        val e = parse("зоомагазин вчера 731 лекарство для кошки Чуни от блох")
        assertEquals(731.0, e.amount, 0.0)
        assertEquals("Питомцы", e.category)
        assertNull(e.emoji)
        assertEquals("Зоомагазин", e.subcategory)
        assertEquals("лекарство для кошки Чуни от блох", e.note)
        assertEquals("2026-11-14", e.date)
        assertNull(e.place)
    }

    @Test
    fun spokenWordsAmount() {
        val e = parse("зоомагазин вчера семьсот тридцать один лекарство для кошки Чуни от блох")
        assertEquals(731.0, e.amount, 0.0)
        assertEquals("лекарство для кошки Чуни от блох", e.note)
    }

    @Test
    fun missingCategoryIsSuggestedWithEmoji() {
        val e = parse("в зоомагазине 731 корм", categories = cats.filter { it.category.name != "Питомцы" })
        assertEquals("Питомцы", e.category)
        assertEquals("🐾", e.emoji)
        assertEquals("Зоомагазин", e.subcategory)
        assertEquals("корм", e.note)
    }

    @Test
    fun petWordsWithoutShopGoToPets() {
        val e = parse("корм для собаки 1200")
        assertEquals("Питомцы", e.category)
        assertEquals("Корм", e.subcategory)
        assertEquals("корм для собаки", e.note)
    }

    @Test
    fun medicineForCatIsPetsNotHealth() {
        val e = parse("лекарство для кошки 500")
        assertEquals("Питомцы", e.category)
    }

    @Test
    fun pharmacyPhrase() {
        val e = parse("аптека позавчера 450 таблетки от головы")
        assertEquals("Здоровье", e.category)
        assertEquals("Аптека", e.subcategory)
        assertEquals("таблетки от головы", e.note)
        assertEquals("2026-11-13", e.date)
    }

    @Test
    fun weekdayIsLastSuchDay() {
        assertEquals("2026-11-11", parse("в среду продуктовый 900").date)
        assertEquals("Продукты", parse("в среду продуктовый 900").category)
        assertEquals("Супермаркет", parse("в среду продуктовый 900").subcategory)
    }

    @Test
    fun currencyWordsAreNotInNote() {
        val e = parse("ветклиника 2000 рублей прививка Чуне")
        assertEquals("Ветеринар", e.subcategory)
        assertEquals("прививка Чуне", e.note)
    }

    @Test
    fun noDateMeansNull() {
        assertNull(parse("кофе 250").date)
        assertEquals("кофе", parse("кофе 250").note)
    }

    @Test
    fun groceryListIsOneExpenseWithItemsInNote() {
        val e = parse("в продуктовом на 2500 купил рыбу, консерву, ещё что-то")
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("Продукты", e.category)
        assertEquals("Супермаркет", e.subcategory)
        assertEquals("рыбу, консерву", e.note)
    }

    @Test
    fun groceryTotalWinsOverFirstPrice() {
        val e = parse("продуктовый рыба 500, хлеб 80, всего 2500")
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("рыба 500 ₽, хлеб 80 ₽", e.note)
    }

    @Test
    fun groceryWithoutCommasStillDropsFillerWords() {
        val e = parse("в продуктовом на 2500 купил рыбу консерву")
        assertEquals(2500.0, e.amount, 0.0)
        assertEquals("рыбу консерву", e.note)
    }

    @Test
    fun shortLabelsAndPrepositionsDoNotPickCategory() {
        val withShort = cats + listOf(
            CategoryWithSubs(Category(id = 5, name = "Авто", emoji = "🚗"), listOf(Subcategory(50, 5, "ТО"), Subcategory(51, 5, "Бензин"))),
            CategoryWithSubs(Category(id = 6, name = "Связь и подписки", emoji = "📱"), listOf(Subcategory(60, 6, "Подписки"))),
        )
        // «торт» начинался с «то» и попадал в «Авто › ТО»
        assertEquals("Прочее", parse("торт 800", withShort).category)
        // предлог «под» совпадал с началом «Подписки»
        assertEquals("Прочее", parse("коврик под мышку 500", withShort).category)
        assertEquals("Бензин", parse("бензина 2000", withShort).subcategory)
        assertEquals("Подписки", parse("подписка на музыку 299", withShort).subcategory)
    }
}
