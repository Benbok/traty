package ru.my.traty.ai

import ru.my.traty.data.Subcategory
import ru.my.traty.data.Rule
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryParsingTest {
    @Test
    fun decodesMixedTypes() {
        val r = EntryParsing.decode(
            """{"items":[{"type":"expense","amount":250,"category":"Кафе и рестораны"},{"type":"income","amount":80000,"source":"Зарплата"}]}"""
        )
        assertEquals(listOf(EntryType.EXPENSE, EntryType.INCOME), r.map { it.entryType })
        assertEquals("Зарплата", r[1].source)
    }

    @Test
    fun unknownTypeHasNullEntryType() {
        val r = EntryParsing.decode("""[{"type":"goal_deposit","amount":5000}]""")
        assertNull(r.single().entryType)
    }

    @Test
    fun incomeSourceIsNormalized() {
        val r = EntryParsing.decode("""{"items":[{"type":"income","amount":100,"source":"зарплата"}]}""")
        assertEquals("Зарплата", r.single().source)
    }

    @Test
    fun dropsNonPositiveAndStripsFence() {
        val r = EntryParsing.decode("```json\n{\"items\":[{\"amount\":0},{\"amount\":400,\"category\":\"Транспорт\"}]}\n```")
        assertEquals(listOf(400.0), r.map { it.amount })
        assertEquals(EntryType.EXPENSE, r.single().entryType)
    }

    @Test
    fun incomeKeepsDateAndFallsBackToDefaultSource() {
        val r = EntryParsing.decode(
            "{\"items\":[{\"type\":\"income\",\"amount\":80000,\"source\":\"Зарплата\",\"date\":\"2026-11-10\"}," +
                "{\"type\":\"income\",\"amount\":2,\"source\":\"Лотерея\"},{\"type\":\"income\",\"amount\":3,\"source\":\"  \"},{\"type\":\"income\",\"amount\":4}]}"
        )
        assertEquals("2026-11-10", r[0].date)
        assertEquals(listOf("Зарплата", "Прочее", "Прочее", "Прочее"), r.map { it.source })
    }

    @Test
    fun offlineDetectsIncomeByKeyword() {
        assertEquals(EntryType.INCOME, OfflineEntryParser.parse("пришла зарплата 80000", emptyList()).single().entryType)
        assertEquals(EntryType.EXPENSE, OfflineEntryParser.parse("кофе 250", emptyList()).single().entryType)
    }

    @Test
    fun severalShopsInOnePhraseGiveSeparateExpensesWithPlace() {
        // Ответ модели на «магазин Пятёрочка 1200, строительный Стройландия 3500, Магнит 800»
        val r = EntryParsing.decode(
            """{"items":[""" +
                """{"type":"expense","amount":1200,"category":"Продукты","place":"Пятёрочка"},""" +
                """{"type":"expense","amount":3500,"category":"Жильё","subcategory":"Ремонт","place":"Стройландия"},""" +
                """{"type":"expense","amount":800,"category":"Продукты","place":"магазин Магнит"}]}"""
        )
        assertEquals(listOf(1200.0, 3500.0, 800.0), r.map { it.amount })
        assertEquals(listOf("Пятёрочка", "Стройландия", "магазин Магнит"), r.map { it.place })
    }

    @Test
    fun promptAsksForOneItemPerShopAndCleanPlace() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("отдельный элемент на каждый магазин" in p)
        assertTrue("«строительный Стройландия» → «Стройландия»" in p)
    }

    @Test
    fun promptExplainsSpokenAmountsWithoutThousand() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("«две сто» → 2100" in p)
        assertTrue("«две пятьсот» → 2500" in p)
        assertTrue("«сто пятьдесят» — это 150" in p)
    }

    @Test
    fun promptMentionsTypesAndToday() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
        assertTrue("\"type\"" in p && "income" in p && "2026-11-14" in p)
    }
    @Test
    fun promptMapsMarketplaceNamesToMarketplaceSubcategories() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 15), emptyList())
        assertTrue("категорию «Маркетплейсы» независимо от вида товара" in p)
        assertTrue("Wildberries/WB/ВБ/Вайлдберриз → «Wildberries»" in p)
        assertTrue("Ozon/Озон → «Ozon»" in p)
        assertTrue("Площадку укажи одновременно в subcategory и place" in p)
    }

    @Test
    fun promptAsksForSubcategoryNoteAndShowsPetShopExample() {
        val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 15), emptyList())
        assertTrue("зоомагазин и ветклиника — «Питомцы»" in p)
        assertTrue("это не place, place = null" in p)
        assertTrue("предложи новую: 1–2 слова" in p)
        assertTrue("не сокращай и не пересказывай" in p)
        assertTrue("\"note\":\"лекарство для кошки Чуни от блох\"" in p)
        assertTrue("\"date\":\"2026-11-14\"" in p)
    }

    @Test
    fun offlineFallbackUnderstandsDateForIncome() {
        val r = OfflineEntryParser.parse("вчера пришла зарплата 80000", emptyList(), LocalDate.of(2026, 11, 15)).single()
        assertEquals(EntryType.INCOME, r.entryType)
        assertEquals("2026-11-14", r.date)
    }

    @Test
    fun offlineForcedPaymentIsNotIncomeButBonusIs() {
        assertEquals(EntryType.EXPENSE, OfflineEntryParser.parse("пришлось заплатить штраф 500", emptyList()).single().entryType)
        assertEquals(EntryType.EXPENSE, OfflineEntryParser.parse("премиум подписка 399", emptyList()).single().entryType)
        assertEquals(EntryType.INCOME, OfflineEntryParser.parse("премия двадцать тысяч", emptyList()).single().entryType)
    }

    @Test
    fun promptShowsLearnedRulesByCategoryName() {
        val cats = listOf(
            CategoryWithSubs(Category(id = 7, name = "Продукты", emoji = "🛒"), listOf(Subcategory(70, 7, "Супермаркет"))),
        )
        val rules = listOf(
            Rule(id = 1, pattern = "лента", categoryId = 7),
            Rule(id = 2, pattern = "пятёрочка", categoryId = 7, subcategoryId = 70),
            Rule(id = 3, pattern = "старое", categoryId = 999),
        )
        val p = EntryPrompt.system(cats, LocalDate.of(2026, 11, 15), rules)
        assertTrue("«лента» → Продукты" in p)
        assertTrue("«пятёрочка» → Продукты › Супермаркет" in p)
        // номер категории модели ничего не говорит, а правило на удалённую категорию не показываем
        assertTrue("categoryId" !in p)
        assertTrue("старое" !in p)
    }

    @Test
    fun oneBrokenRecordDoesNotLoseTheOthers() {
        val r = EntryParsing.decodeChecked(
            """{"items":[{"type":"expense","amount":"много","category":"Кафе"},{"type":"expense","amount":400,"category":"Транспорт"}]}"""
        )
        assertEquals(listOf(400.0), r.items.map { it.amount })
        assertEquals(1, r.dropped)
    }

    @Test
    fun negativeAndAbsurdAmountsAreDroppedAndCounted() {
        val r = EntryParsing.decodeChecked(
            """[{"type":"expense","amount":-300},{"type":"expense","amount":9999999999},{"type":"expense","amount":250,"category":"Кафе и рестораны"}]"""
        )
        assertEquals(listOf(250.0), r.items.map { it.amount })
        assertEquals(2, r.dropped)
    }

    @Test
    fun answerWithNothingUsableIsAnErrorSoPhraseGoesToOfflineParser() {
        assertFailsWith("все записи испорчены") {
            EntryParsing.decode("""{"items":[{"type":"expense","amount":-5}]}""")
        }
        assertFailsWith("нет JSON") { EntryParsing.decode("Не могу разобрать эту фразу") }
    }

    @Test
    fun zeroAmountFillerIsNotCountedAsBroken() {
        val r = EntryParsing.decodeChecked("""{"items":[{"amount":0},{"amount":400,"category":"Транспорт"}]}""")
        assertEquals(0, r.dropped)
        assertEquals(1, r.items.size)
    }

    @Test
    fun longTextsFromModelAreCapped() {
        val long = "а".repeat(900)
        val e = EntryParsing.decode("""{"items":[{"amount":10,"category":"$long","note":"$long","place":"$long"}]}""").single()
        assertEquals(60, e.category.length)
        assertEquals(60, e.place!!.length)
        assertEquals(500, e.note!!.length)
    }

    @Test
    fun singleObjectWithoutItemsIsAccepted() {
        assertEquals(300.0, EntryParsing.decode("""{"type":"expense","amount":300,"category":"Транспорт"}""").single().amount, 0.0)
    }

    private fun assertFailsWith(message: String, block: () -> Unit) {
        val failed = runCatching(block).isFailure
        assertTrue(message, failed)
    }
}
