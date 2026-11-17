package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.receipt.ParsedReceipt
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptLine
import ru.my.traty.data.items.Pack

class ReceiptPartsTest {
    private val milk = ReceiptLine("Молоко 3,2%", 8999, 1000, "шт", Pack(1000, "л"))
    private val bread = ReceiptLine("Хлеб бородинский", 4500, 1000, "шт")
    private val cheese = ReceiptLine("Сыр Российский", 15000, 1000, "шт")
    private fun part(vararg lines: ReceiptLine, total: Long? = null) =
        ParsedReceipt("Пятёрочка", "2026-11-17", total, "Продукты", lines.toList())

    @Test fun overlappingThreePhotosBecomeOneOrderedPurchase() {
        val r = ReceiptParts.merge(listOf(part(milk, bread), part(bread, cheese), part(cheese, total = 28499)))
        assertEquals(listOf(milk, bread, cheese), r.receipt.lines)
        assertEquals(28499L, r.receipt.totalKopecks)
        assertEquals(2, r.removed)
    }

    @Test fun repeatedRowsInsidePhotoArePreservedWithMultiplicityAtBoundary() {
        val r = ReceiptParts.merge(listOf(part(milk, bread, bread), part(bread, bread, cheese)))
        assertEquals(listOf(milk, bread, bread, cheese), r.receipt.lines)
        assertEquals(2, r.removed)
    }

    @Test fun sameProductInAnotherPlaceIsNotGloballyDeduplicated() {
        val r = ReceiptParts.merge(listOf(part(milk, bread), part(bread, cheese, milk)))
        assertEquals(listOf(milk, bread, cheese, milk), r.receipt.lines)
    }

    @Test fun repeatedNonAdjacentPhotoIsSkippedOnlyWithMatchingFullText() {
        val texts = listOf("Молоко 3,2% 89,99", "Хлеб 45,00", "МОЛОКО 3.2% 89.99")
        val r = ReceiptParts.merge(listOf(part(milk), part(bread), part(milk)), texts)
        assertEquals(listOf(milk, bread), r.receipt.lines)
        assertEquals(1, r.removed)
    }

    @Test fun identicalItemWithoutRepeatedPhotoTextIsRetainedAwayFromBoundary() {
        val r = ReceiptParts.merge(listOf(part(milk, bread), part(milk)))
        assertEquals(listOf(milk, bread, milk), r.receipt.lines)
        assertTrue(r.notices.any { it.contains("не найдено") })
    }

    @Test fun quantitiesPacksPricesAndUnknownPricesDoNotCollapse() {
        val variants = listOf(
            milk.copy(quantityMilli = 2000), milk.copy(pack = Pack(500, "л")),
            milk.copy(priceKopecks = 9999), milk.copy(priceKopecks = null),
        )
        variants.forEach { variant ->
            assertEquals(2, ReceiptParts.merge(listOf(part(milk), part(variant))).receipt.lines.size)
        }
        val unknown = milk.copy(priceKopecks = null)
        assertEquals(2, ReceiptParts.merge(listOf(part(unknown), part(unknown))).receipt.lines.size)
    }

    @Test fun casingQuotesYoAndDecimalSeparatorDoNotCreateDuplicates() {
        val first = milk.copy(name = "Молоко «Алёнка» 3,2%")
        val second = milk.copy(name = "МОЛОКО Аленка 3.2%")
        assertEquals(1, ReceiptParts.merge(listOf(part(first), part(second))).receipt.lines.size)
    }

    @Test fun numericDifferencesAndLongNameSuffixesAreNotIgnored() {
        listOf(
            milk.copy(name = "Молоко 32%"),
            milk.copy(name = "Молоко 2,5%"),
        ).forEach { variant ->
            assertEquals(2, ReceiptParts.merge(listOf(part(milk), part(variant))).receipt.lines.size)
        }
        val long = milk.copy(name = "Очень длинное название одного и того же производителя: сливочное")
        val other = long.copy(name = "Очень длинное название одного и того же производителя: шоколадное")
        assertEquals(2, ReceiptParts.merge(listOf(part(long), part(other))).receipt.lines.size)
    }

    @Test fun totalsAreNeverAddedAcrossPhotos() {
        val r = ReceiptParts.merge(listOf(part(milk, total = 8999), part(milk, bread, total = 13499)))
        assertEquals(13499L, r.receipt.totalKopecks)
        assertEquals(listOf(milk, bread), r.receipt.lines)
    }

    @Test fun missingTotalStaysNullForDraftToSumMergedLinesOnce() {
        assertEquals(null, ReceiptParts.merge(listOf(part(milk, bread), part(bread, cheese))).receipt.totalKopecks)
    }

    @Test(expected = ReceiptException::class) fun differentDatesAreRejected() {
        ReceiptParts.merge(listOf(part(milk), part(bread).copy(date = "2026-11-16")))
    }

    @Test fun cleaningPreservesLongReceiptAndStripsPersonalDataOnEveryPhoto() {
        val text = "Молоко 89.99\n".repeat(700) + "Кассир: Иванова\nФН 12345678\nИТОГ 62993"
        val clean = ReceiptParts.clean(listOf(text, "Хлеб 45.00\nКассир: Петрова"))
        assertTrue(clean.first().length > 8000)
        assertTrue(clean.first().endsWith("ИТОГ 62993"))
        assertTrue(clean.none { it.contains("Кассир") || it.contains("Иванова") || it.contains("Петрова") || it.contains("12345678") })
    }

    @Test(expected = ReceiptException::class) fun oversizedInputIsRejectedRatherThanTruncated() {
        ReceiptParts.clean(listOf("а".repeat(ReceiptParts.MAX_CHARS + 1)))
    }

    @Test(expected = ReceiptException::class) fun emptyPhotoIsRejected() {
        ReceiptParts.clean(listOf("Молоко 89.99", "  "))
    }

    @Test(expected = ReceiptException::class) fun tooManyPhotosAreRejected() {
        ReceiptParts.clean(List(ReceiptParts.MAX_PHOTOS + 1) { "Молоко 89.99" })
    }
}
