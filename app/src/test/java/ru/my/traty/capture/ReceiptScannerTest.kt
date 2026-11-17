package ru.my.traty.capture

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.TratyApp
import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptOcr
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.canScanReceipts
import java.time.ZoneOffset

/** Фото чека → одна трата с товарами; в ИИ не уходят кассир и номера кассы; без проверки ничего не сохраняется. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ReceiptScannerTest {
    private lateinit var app: TratyApp

    private val receipt = """
        Пятёрочка
        Молоко 3,2% 1л   89.99
        Хлеб бородинский 2 x 45.00 =90.00
        ИТОГ =179.99
        Кассир: Иванова Мария
        ФН 9999078900012345
    """.trimIndent()

    private class Ocr(private val text: String) : ReceiptOcr {
        override suspend fun recognize(jpeg: ByteArray) = text
    }

    private class Llm(private val json: String) : LlmClient {
        var sentUser = ""
        override val name = "Fake"
        override suspend fun complete(system: String, user: String, jsonMode: Boolean): String {
            sentUser = user
            return json
        }
    }

    private val answer =
        """{"store":"Пятёрочка","date":null,"total":"179.99","category":"Продукты",
        "items":[{"name":"Молоко 3,2%","qty":1,"unit":"шт","sum":"89.99"},{"name":"Хлеб бородинский","qty":2,"unit":"шт","sum":"90.00"}]}"""

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    private fun scanner(ocr: ReceiptOcr, llm: LlmClient) = ReceiptScanner(
        app.container.db, app.container.settings, ZoneOffset.UTC, ocrFor = { ocr }, llmFor = { llm },
    )

    @Test
    fun photoBecomesOneExpenseWithItems() = runTest {
        app.container.categories.seedIfEmpty()
        val llm = Llm(answer)
        val review = scanner(Ocr(receipt), llm).scan(ByteArray(1))
        assertFalse(llm.sentUser.contains("Иванова"))
        assertFalse(llm.sentUser.contains("9999078900012345"))
        val row = review.draft.rows.single()
        assertEquals("179.99", row.amountText)
        assertEquals(2, row.items.size)

        val saved = app.container.quickEntry.save(review.draft)
        val id = saved.saved.expenseIds.single()
        assertEquals(ExpenseSource.PHOTO, app.container.db.expenses().getByIds(listOf(id)).single().expense.source)
        val items = app.container.expenseItems.getFor(id)
        assertEquals(17_999L, items.sumOf { it.priceKopecks ?: 0L })
    }

    @Test
    fun shortNetworkDropIsRetried() = runTest {
        app.container.categories.seedIfEmpty()
        var calls = 0
        val flaky = object : ReceiptOcr {
            override suspend fun recognize(jpeg: ByteArray): String {
                if (++calls < 3) throw java.io.IOException("connection reset")
                return receipt
            }
        }
        val review = scanner(flaky, Llm(answer)).scan(ByteArray(1))
        assertEquals(3, calls)
        assertEquals("179.99", review.draft.rows.single().amountText)
    }

    @Test
    fun afterDeepSeekErrorTextIsParsedWithoutPhoto() = runTest {
        app.container.categories.seedIfEmpty()
        var ocrCalls = 0
        val ocr = object : ReceiptOcr {
            override suspend fun recognize(jpeg: ByteArray): String = receipt.also { ocrCalls++ }
        }
        val s = scanner(ocr, Llm(answer))
        val text = s.recognize(ByteArray(1))
        val review = s.parse(text)
        assertEquals(1, ocrCalls)
        assertEquals(2, review.draft.rows.single().items.size)
    }

    @Test
    fun emptyPhotoGivesClearError() = runTest {
        val error = runCatching { scanner(Ocr("  \n "), Llm(answer)).scan(ByteArray(1)) }.exceptionOrNull()
        assertTrue(error is ReceiptException)
    }

    @Test
    fun severalPhotosSaveOneExpenseWithOverlappingItemsOnlyOnce() = runTest {
        app.container.categories.seedIfEmpty()
        val llm = Llm("""{"parts":[
            {"store":"Пятёрочка","category":"Продукты","items":[
                {"name":"Молоко","sum":89.99},{"name":"Хлеб","sum":45}]},
            {"total":284.99,"items":[{"name":"Хлеб","sum":45},{"name":"Сыр","sum":150}]}]}""")
        val s = scanner(Ocr(receipt), llm)
        val review = s.parseParts(listOf(
            "Пятёрочка\nМолоко 89.99\nХлеб 45.00\nКассир: Иванова",
            "Хлеб 45.00\nСыр 150.00\nИТОГ 284.99\nФН 1234567",
        ))
        assertTrue(llm.sentUser.contains("Снимок 1:"))
        assertTrue(llm.sentUser.contains("Снимок 2:"))
        assertFalse(llm.sentUser.contains("Иванова"))
        assertFalse(llm.sentUser.contains("1234567"))
        assertEquals("284.99", review.draft.rows.single().amountText)
        assertEquals(3, review.draft.rows.single().items.size)
        val id = app.container.quickEntry.save(review.draft).saved.expenseIds.single()
        assertEquals(3, app.container.expenseItems.getFor(id).size)
        assertEquals(28499L, app.container.expenseItems.getFor(id).sumOf { it.priceKopecks ?: 0 })
    }

    @Test
    fun missingPrintedTotalUsesMergedItemSumWithoutOverlap() = runTest {
        app.container.categories.seedIfEmpty()
        val llm = Llm("""{"parts":[{"items":[{"name":"Молоко","sum":89.99},{"name":"Хлеб","sum":45}]},
            {"items":[{"name":"Хлеб","sum":45},{"name":"Сыр","sum":150}]}]}""")
        val review = scanner(Ocr(receipt), llm).parseParts(listOf("Молоко 89.99\nХлеб 45.00", "Хлеб 45.00\nСыр 150.00"))
        assertEquals("284.99", review.draft.rows.single().amountText)
    }

    @Test
    fun conflictingPrintedTotalsCannotBecomeOnePurchase() = runTest {
        app.container.categories.seedIfEmpty()
        val llm = Llm("""{"parts":[{"items":[]},{"items":[]}]}""")
        val result = runCatching {
            scanner(Ocr(receipt), llm).parseParts(listOf("Молоко 89.99\nИТОГ 89.99", "Хлеб 45.00\nИТОГ 45.00"))
        }
        assertTrue(result.exceptionOrNull() is ReceiptException)
    }

    @Test
    fun withoutYandexOrDeepSeekKeysNoScanning() {
        assertFalse(AppSettings(yandexKey = "k").canScanReceipts)
        assertFalse(AppSettings(yandexKey = "k", yandexFolderId = "b1g").canScanReceipts)
        assertFalse(AppSettings(yandexFolderId = "b1g", deepseekKey = "d").canScanReceipts)
        assertTrue(AppSettings(yandexKey = "k", yandexFolderId = "b1g", deepseekKey = "d").canScanReceipts)
    }
}

