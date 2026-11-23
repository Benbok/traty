package ru.my.traty.capture

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.my.traty.TestTratyApp
import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptImageExtractor
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.LlmProvider
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.canScanReceipts
import ru.my.traty.data.secure.AesGcmSecretCipher
import ru.my.traty.data.secure.SecretCodec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTratyApp::class)
class ReceiptScannerTest {
    private val app = ApplicationProvider.getApplicationContext<TestTratyApp>()
    private val settings = SettingsRepository(app, SecretCodec(AesGcmSecretCipher {
        SecretKeySpec(ByteArray(16), "AES")
    }))

    @Test fun availabilityDependsOnlyOnSavedDeepSeekKey() {
        assertTrue(AppSettings(provider = LlmProvider.OFFLINE, deepseekKey = "d", deepseekModel = "").canScanReceipts)
        assertFalse(AppSettings(deepseekKey = " \t", yandexKey = "y", yandexFolderId = "folder").canScanReceipts)
    }

    @Test fun twoFramesProduceOnePurchaseOnlyWhenConfirmed() = runTest {
        settings.save(AppSettings(deepseekKey = "test"))
        app.container.categories.seedIfEmpty()
        val frames = listOf(byteArrayOf(1), byteArrayOf(2))
        val apple = ReceiptLine("Яблоко", 2_500)
        val bread = ReceiptLine("Хлеб", 4_000)
        val tea = ReceiptLine("Чай", 8_000)
        val extractor = ReceiptImageExtractor { photos, categories ->
            assertEquals(listOf(1.toByte(), 2.toByte()), photos.map { it.single() })
            assertTrue("Продукты" in categories)
            listOf(
                ParsedReceipt("Магазин", "2026-01-01", null, "Продукты", listOf(apple, bread, tea)),
                ParsedReceipt(null, null, 17_000, null, listOf(bread, tea, apple)),
            )
        }
        val scanner = ReceiptScanner(app.container.db, settings, visionFor = {
            assertEquals("test", it.deepseekKey)
            assertTrue(it.yandexKey.isEmpty())
            extractor
        })
        val review = scanner.scanPhotos(frames)
        assertTrue(app.container.db.expenses().distinctPlaces().isEmpty())
        assertEquals(listOf("Яблоко", "Хлеб", "Чай", "Яблоко"), review.draft.rows.single().items.map { it.name })
        val id = app.container.quickEntry.save(review.draft).saved.expenseIds.single()
        val purchase = app.container.db.expenses().getByIds(listOf(id)).single().expense
        assertEquals(ExpenseSource.PHOTO, purchase.source)
        assertEquals(17_000L, purchase.amountKopecks)
        assertEquals(listOf(2_500L, 4_000L, 8_000L, 2_500L), app.container.expenseItems.getFor(id).map { it.priceKopecks })
    }

    @Test fun emptyFramesNeverReachTheExtractor() = runTest {
        val scanner = ReceiptScanner(app.container.db, settings, visionFor = { error("Unexpected extractor") })
        assertTrue(runCatching { scanner.scanPhotos(listOf(byteArrayOf())) }.exceptionOrNull() is ReceiptException)
    }
}
