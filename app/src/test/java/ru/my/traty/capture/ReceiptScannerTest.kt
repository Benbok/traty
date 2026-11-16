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
    fun emptyPhotoGivesClearError() = runTest {
        val error = runCatching { scanner(Ocr("  \n "), Llm(answer)).scan(ByteArray(1)) }.exceptionOrNull()
        assertTrue(error is ReceiptException)
    }

    @Test
    fun withoutYandexKeysNoScanning() {
        assertFalse(AppSettings(yandexKey = "k").canScanReceipts)
        assertTrue(AppSettings(yandexKey = "k", yandexFolderId = "b1g").canScanReceipts)
    }
}
