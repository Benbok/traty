package ru.my.traty.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Category
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.items.ExpenseItem
import java.time.LocalDate
import java.time.ZoneOffset

/** «Сколько коктейлей я купил», «сколько гуляша за месяц»: количество с фасовкой считает приложение. */
class AssistantItemQuantityTest {
    private val today = LocalDate.of(2026, 11, 17)
    private var nextId = 1L
    private val items = HashMap<Long, List<ExpenseItem>>()

    private fun exp(rub: Long, day: Int, note: String? = null, vararg goods: ExpenseItem) =
        ExpenseWithCategory(
            Expense(
                id = nextId++, amountKopecks = rub * 100, categoryId = 1, place = "Пятёрочка", note = note, source = ExpenseSource.PHOTO,
                timestamp = LocalDate.of(2026, 11, day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            ),
            categoryName = "Продукты", categoryEmoji = "🛒", subcategoryName = null,
        ).also { r -> if (goods.isNotEmpty()) items[r.expense.id] = goods.map { it.copy(expenseId = r.expense.id) } }

    private val rows = listOf(
        exp(
            500, 3, null,
            ExpenseItem(name = "Молочный коктейль", priceKopecks = 27_000, quantityMilli = 3_000, unit = "шт", packMilli = 200_000, packUnit = "мл"),
            ExpenseItem(name = "Хлеб", priceKopecks = 6_000),
        ),
        exp(
            300, 10, null,
            ExpenseItem(name = "Коктейль молочный 450мл", priceKopecks = 12_000),
            ExpenseItem(name = "Говядина гуляш", priceKopecks = 45_700, packMilli = 500_000, packUnit = "г"),
        ),
        // Записано до фасовки: одна трата без списка, вес в заметке
        exp(460, 12, "говядина гуляш 500 г"),
    )

    private val categories = listOf(CategoryWithSubs(Category(id = 1, name = "Продукты", emoji = "🛒"), emptyList()))

    private fun run(term: String) = AssistantEngine.run(
        AssistantQuery(intent = Intents.TOTAL, categories = listOf(term)),
        AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories, expenseItems = items),
    )

    @Test
    fun shakesInPiecesAndLitres() {
        val facts = run("коктейли").facts
        // 3 шт по 200 мл + 1 шт 450 мл (количество не названо — одна штука)
        assertTrue(facts, "Количество: 4 шт · 1,05 л" in facts)
        assertTrue(facts, "у 1 позиции количество не названо" in facts)
    }

    @Test
    fun goulashWeightIncludesOldNote() {
        val facts = run("гуляш").facts
        assertTrue(facts, "Количество: 2 шт · 1 кг" in facts)
    }

    @Test
    fun listLineShowsPack() {
        val line = AssistantItems.quantity(listOf(ItemPart(0, rows[0].let { items.getValue(it.expense.id).take(1) }, whole = false)))
        assertEquals("Количество: 3 шт · 600 мл.", line)
    }

    private fun ask(
        question: String,
        expenses: List<ExpenseWithCategory>,
        ai: AssistantQuery? = AssistantQuery(intent = Intents.TOTAL),
    ): AssistantData {
        val guessed = QuestionRules.guess(question, categories)
        val query = QuestionRules.resolve(question, ai, guessed, today, 1, categories = categories)
        return AssistantEngine.run(
            query,
            AssistantContext(expenses, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories, expenseItems = items),
        )
    }

    @Test
    fun spokenQuantityKeepsProductEvenWhenModelReturnsACategory() {
        val receipt = exp(
            500, 10, null,
            ExpenseItem(name = "Чудо Коктейль молочный шоколад 3%", priceKopecks = 19_996, quantityMilli = 4_000, unit = "шт"),
            ExpenseItem(name = "Чудо коктейль молочный стерилизованный с шоколадом 200 мл", priceKopecks = 17_997, quantityMilli = 3_000, unit = "шт"),
            ExpenseItem(name = "Чудо коктейль молочный ванильный", priceKopecks = 4_500),
            ExpenseItem(name = "Хлеб", priceKopecks = 3_000),
        )
        val answer = ask(
            "Сколько э молочных коктейлей шоколад мы купили?",
            rows + receipt, AssistantQuery(intent = Intents.TOTAL, categories = listOf("Продукты")),
        )
        assertTrue(answer.headline.orEmpty(), answer.headline.orEmpty().startsWith("7 шт"))
        assertTrue(answer.facts, "379,93" in answer.facts)
        assertEquals(listOf(receipt.expense.id), answer.expenseIds)
        assertTrue(AssistantAnswers.template(Intents.TOTAL, answer).orEmpty().startsWith("Количество:"))
    }

    @Test
    fun ambiguousProductShowsCandidatesFromTheRequestedShopAndPeriod() {
        val inScope = exp(100, 10, null, ExpenseItem(name = "Чудо коктейль молочный шоколад"))
        val otherShop = exp(200, 10, null, ExpenseItem(name = "Чудо йогурт клубничный"))
            .let { it.copy(expense = it.expense.copy(place = "Магнит")) }
        val otherMonth = exp(300, 10, null, ExpenseItem(name = "Чудо творожок ванильный"))
            .let { it.copy(expense = it.expense.copy(timestamp = it.expense.timestamp - 31L * 24 * 60 * 60 * 1000)) }
        val answer = ask(
            "Сколько чудо молока мы купили в Пятёрочке в ноябре?",
            listOf(inScope, otherShop, otherMonth),
        )
        val reply = answer.direct.orEmpty()
        assertTrue(reply, "не нашёл в чеках" in reply)
        val choice = answer.itemSuggestions.single()
        assertEquals("Чудо коктейль молочный шоколад", choice.itemName)
        assertEquals(listOf(inScope.expense.id), choice.expenseIds)
        assertEquals("1 шт", choice.headline)
        assertTrue(choice.text, "100 ₽" in choice.text)
        assertEquals(null, answer.headline)
        assertTrue(answer.expenseIds.isEmpty())
    }

    @Test
    fun ambiguousSpendingQuestionOffersSeparateCalculationsForEachName() {
        val chocolate = exp(100, 10, null, ExpenseItem(name = "Чудо коктейль шоколадный"))
        val vanilla = exp(200, 10, null, ExpenseItem(name = "Чудо коктейль ванильный"))
        val answer = ask("Сколько потратили на чудо молоко в ноябре?", listOf(chocolate, vanilla))
        assertEquals(listOf("Чудо коктейль шоколадный", "Чудо коктейль ванильный"), answer.itemSuggestions.map { it.itemName })
        assertEquals(listOf("100 ₽", "200 ₽"), answer.itemSuggestions.map { it.headline })
        assertEquals(listOf(listOf(chocolate.expense.id), listOf(vanilla.expense.id)), answer.itemSuggestions.map { it.expenseIds })
    }

    @Test
    fun unknownProductDoesNotBecomeTotalSpendingEvenOffline() {
        val answer = ask("Сколько мы купили фисташкового крема?", rows, ai = null)
        assertTrue(answer.direct.orEmpty(), "фисташкового крема" in answer.direct.orEmpty())
        assertTrue(answer.expenseIds.isEmpty())
        assertEquals(null, answer.headline)
    }

    @Test
    fun unscopedSpendingQuestionStillReturnsMoney() {
        val answer = ask("Сколько потратили в ноябре?", rows)
        assertTrue(answer.headline.orEmpty(), answer.headline.orEmpty().endsWith("₽"))
        assertEquals(rows.asReversed().map { it.expense.id }, answer.expenseIds)
    }

    @Test
    fun selectedNamesAreCalculatedTogetherEvenInTheSameReceipt() {
        val receipt = exp(
            500, 10, null,
            ExpenseItem(name = "Чудо Коктейль молочный шоколад 3%", priceKopecks = 19_996, quantityMilli = 4_000, unit = "шт"),
            ExpenseItem(name = "Чудо коктейль молочный с шоколадом 200 мл", priceKopecks = 17_997, quantityMilli = 3_000, unit = "шт"),
            ExpenseItem(name = "Хлеб", priceKopecks = 12_007),
        )
        val answer = ask("Сколько шоколадного молока я купил?", listOf(receipt))
        val indexes = answer.candidateNames.indices.filter { answer.candidateNames[it].startsWith("Чудо") }
        val selected = ProductResolution.answer(answer, """{"status":"matched","ids":${indexes}}""")
        assertEquals("7 шт · 600 мл", selected.headline)
        assertTrue(selected.direct.orEmpty(), "379,93 ₽" in selected.direct.orEmpty())
        assertTrue(selected.direct.orEmpty(), "У 4 шт. объём упаковки не указан." in selected.direct.orEmpty())
        assertEquals(listOf(receipt.expense.id), selected.expenseIds)
    }

    @Test
    fun fiveWaysToAskForTheChocolateDrink() {
        val chocolate = exp(
            500, 10, null,
            ExpenseItem(name = "Чудо Коктейль молочный шоколад 3%", priceKopecks = 19_996, quantityMilli = 4_000, unit = "шт"),
            ExpenseItem(name = "Хлеб", priceKopecks = 30_004),
        )
        val secondLabel = exp(
            180, 11, null,
            ExpenseItem(name = "Чудо коктейль молочный стерилизованный с шоколадом 200 мл", quantityMilli = 3_000, unit = "шт"),
        )
        val otherProduct = exp(200, 12, null, ExpenseItem(name = "Чудо йогурт ванильный"))
        val expenses = listOf(chocolate, secondLabel, otherProduct)
        val expected = setOf(chocolate.expense.id, secondLabel.expense.id)

        for (phrase in listOf("шоколадное молочко", "чудо молоко", "шоколадное молоко", "чудо коктель")) {
            val answer = ask("Сколько $phrase мы купили?", expenses)
            assertEquals(phrase, expected, answer.itemSuggestions.flatMap { it.expenseIds }.toSet())
            assertTrue(phrase, answer.expenseIds.isEmpty())
            assertEquals(phrase, setOf("4 шт", "3 шт · 600 мл"), answer.itemSuggestions.map { it.headline }.toSet())
        }

        val broad = ask("Сколько молочный коктейль мы купили?", expenses)
        assertEquals(expected, broad.expenseIds.toSet())
        assertTrue(broad.headline.orEmpty(), broad.headline.orEmpty().startsWith("7 шт"))
        assertTrue(broad.itemSuggestions.isEmpty())
    }

    @Test
    fun productRankChangesMetricAndPreservesReferencedDates() {
        val bread = ExpenseItem(name = "Хлеб", quantityMilli = 1_000, unit = "шт", priceKopecks = 5_000)
        val yogurt = ExpenseItem(name = "Йогурт", quantityMilli = 10_000, unit = "шт", priceKopecks = 30_000)
        val cheese = ExpenseItem(name = "Сыр", quantityMilli = 1_000, unit = "шт", priceKopecks = 50_000)
        val receipts = listOf(exp(100, 4, null, bread, bread), exp(100, 8, null, bread), exp(800, 9, null, yogurt, cheese))
        val ctx = AssistantContext(receipts, emptyList(), emptyList(), today, 1, ZoneOffset.UTC, categories = categories, expenseItems = items)
        fun resolve(question: String, previous: AssistantQuery? = null) = QuestionRules.resolve(
            question, AssistantQuery(Intents.TOP), QuestionRules.guess(question, categories), today, 1, previous, categories,
        )

        val popular = resolve("Какие самые популярные продукты из трат у меня были в принципе за вот этот весь период?",
            AssistantQuery(Intents.TOTAL, from = "2026-11-01", to = "2026-11-08"))
        assertEquals(Intents.PRODUCT_RANK, popular.intent)
        assertEquals("2026-11-08", popular.to)
        assertEquals(null, popular.place)
        val frequency = AssistantEngine.run(popular, ctx).direct.orEmpty()
        assertTrue(frequency, "• Хлеб — 2 покупки · 3 шт" in frequency)
        assertTrue(frequency, "Йогурт" !in frequency)

        val byPieces = AssistantEngine.run(resolve("Чего купил больше всего в штуках?"), ctx).direct.orEmpty()
        assertTrue(byPieces, byPieces.substringAfter('\n').startsWith("• Йогурт"))
        val bySpending = AssistantEngine.run(resolve("На какие товары ушло больше всего денег?"), ctx).direct.orEmpty()
        assertTrue(bySpending, bySpending.substringAfter('\n').startsWith("• Сыр"))
        assertEquals(QuestionDates.ALL_TIME.toString(), resolve("Что я покупал чаще всего за всё время?").from)
        assertEquals(Intents.TOP, resolve("Топ крупных трат на продукты").intent)
        assertEquals("пятерочке", resolve("Какие товары чаще всего покупал в Пятёрочке?").place)
    }

    @Test
    fun groceryRankingExplainsSinglePurchasesAndUsesTheContentsOfGroceryReceipts() {
        val chips = ExpenseItem(name = "Чипсы", priceKopecks = 13_999)
        val milk = ExpenseItem(name = "Молоко", priceKopecks = 9_999)
        val petFood = ExpenseItem(name = "SHEBA Корм для кошек", priceKopecks = 6_598)
        val basket = exp(320, 8, null, chips, milk, petFood)
        val uncatalogued = exp(100, 9, "молоко")
        val otherCategory = exp(500, 10, null, ExpenseItem(name = "Шампунь")).let {
            it.copy(expense = it.expense.copy(categoryId = 2), categoryName = "Красота и уход")
        }
        val question = "Какие продукты я покупаю чаще всего?"
        val query = QuestionRules.resolve(
            question, AssistantQuery(Intents.PRODUCT_RANK, place = "магазине"),
            QuestionRules.guess(question, categories), today, 1, categories = categories,
        )
        assertEquals(null, query.place)
        val receipts = listOf(basket, uncatalogued, otherCategory)
        fun report(rows: List<ExpenseWithCategory>) = AssistantEngine.run(
            query, AssistantContext(rows, emptyList(), emptyList(), today, 1, ZoneOffset.UTC,
                categories = categories, expenseItems = items),
        ).direct.orEmpty()

        val tied = report(receipts)
        assertTrue(tied, "каждый найденный товар встречается в одной покупке" in tied)
        assertTrue(tied, "• Чипсы — 1 покупка" in tied)
        assertTrue(tied, "Состав чека известен для 1 из 2 покупок" in tied)
        assertTrue(tied, "SHEBA" !in tied && "Шампунь" !in tied && "1. Чипсы" !in tied)

        val repeated = report(receipts + exp(100, 11, null, milk))
        assertTrue(repeated, "• Молоко — 2 покупки" in repeated)
        assertTrue(repeated, "Чипсы" !in repeated)
    }
    @Test
    fun habitualRankingCombinesReceiptNamesAcrossMonthsBeforeTakingTop() {
        val first = ExpenseItem(name = "Чудо Коктейль молочный шоколад 3%", priceKopecks = 4_999, unit = "шт", quantityMilli = 1_000)
        val second = first.copy(name = "Чудо коктейль молочный стерилизованный с шоколадом 200 мл", priceKopecks = 5_999)
        val older = exp(300, 16, null, first, first, first, first).let {
            it.copy(expense = it.expense.copy(timestamp = it.expense.timestamp - 31L * 86_400_000))
        }
        val newer = exp(300, 17, null, second, second, second)
        val distractors = exp(400, 17, null, ExpenseItem(name = "Чипсы"), first.copy(name = "Чудо коктейль молочный ваниль"))
        val receipts = listOf(distractors, newer, older)
        val answer = ask("Какие продукты я покупаю чаще всего?", receipts)
        val reply = answer.direct.orEmpty()
        assertTrue(reply, "2 покупки · 7 шт" in reply && "379,93 ₽" in reply)
        assertTrue(reply, "Чипсы" !in reply && "ваниль" !in reply)
        assertEquals(setOf(older.expense.id, newer.expense.id), answer.expenseIds.toSet())
        val november = ask("Какие продукты я покупаю чаще всего в ноябре?", receipts)
        assertTrue(november.direct.orEmpty(), "7 шт" !in november.direct.orEmpty())
    }
}
