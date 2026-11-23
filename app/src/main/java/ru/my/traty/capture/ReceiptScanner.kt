package ru.my.traty.capture

import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptImageExtractor
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.canScanReceipts
import ru.my.traty.data.selectable
import java.time.LocalDate
import java.time.ZoneId

/** Читает фото и собирает черновик. Запись происходит после проверки пользователем. */
class ReceiptScanner(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val visionFor: (AppSettings) -> ReceiptImageExtractor?,
) {
    suspend fun scanPhotos(photos: List<ByteArray>): ReceiptReview {
        if (photos.size !in 1..ReceiptParts.MAX_PHOTOS) {
            throw ReceiptException("Добавьте от 1 до ${ReceiptParts.MAX_PHOTOS} снимков одного чека")
        }
        if (photos.any { it.isEmpty() || it.size > ReceiptImage.MAX_BYTES }) {
            throw ReceiptException("Не удалось подготовить один из снимков. Переснимите чек")
        }
        val config = settings.current()
        if (!config.canScanReceipts) throw ReceiptException("Заполните ключ DeepSeek в настройках ИИ")
        val extractor = visionFor(config) ?: throw ReceiptException("Распознавание фото DeepSeek недоступно")
        val categories = db.categories().getActiveWithSubs().selectable()
        val parts = extractor.extract(photos, categories.map { it.category.name })
        val merged = ReceiptParts.merge(parts)
        val review = ReceiptDraft.build(
            parsed = merged.receipt,
            categories = categories,
            rules = db.rules().getAll(),
            knownPlaces = db.expenses().distinctPlaces(),
            today = LocalDate.now(zone),
        )
        val unreadable = parts.mapIndexedNotNull { index, part ->
            val empty = part.lines.isEmpty() && part.totalKopecks == null && part.date == null && part.place == null
            (index + 1).takeIf { empty }
        }
        val notices = if (unreadable.isEmpty()) emptyList() else {
            listOf("На снимках ${unreadable.joinToString(", ")} не распознаны данные чека. Проверьте кадры")
        }
        return review.copy(notices = notices + merged.notices + review.notices)
    }
}
