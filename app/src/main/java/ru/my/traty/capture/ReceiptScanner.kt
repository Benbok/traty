package ru.my.traty.capture

import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.receipt.NetworkRetry
import ru.my.traty.ai.receipt.ReceiptOcr
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptParsing
import ru.my.traty.ai.receipt.ReceiptPrompt
import ru.my.traty.ai.receipt.ReceiptService
import ru.my.traty.ai.receipt.ReceiptServiceFailure
import ru.my.traty.ai.receipt.ReceiptText
import ru.my.traty.ai.receipt.receiptLlmFor
import ru.my.traty.ai.receipt.receiptOcrFor
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.selectable
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId

/** Что сейчас делает разбор чека: показывается на экране ожидания. */
enum class ReceiptStep(val title: String) {
    READING("Читаю текст с фото…"),
    PARSING("Раскладываю чек по товарам…"),
}

/**
 * Путь «фото чека → текст → товары → черновик траты». Ничего не сохраняет:
 * черновик всегда показывается на экране проверки, сохраняет человек.
 * В Яндекс уходит только фото (Vision OCR), в DeepSeek — текст чека без служебных строк и названия категорий.
 */
class ReceiptScanner(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val ocrFor: (AppSettings) -> ReceiptOcr? = ::receiptOcrFor,
    private val llmFor: (AppSettings) -> LlmClient? = ::receiptLlmFor,
) {
    /** Весь путь сразу: фото → текст → черновик. */
    suspend fun scan(jpeg: ByteArray, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview =
        parse(recognize(jpeg, onStep), onStep)

    /**
     * Шаг 1: текст с фото (Яндекс). Отдельно от [parse], чтобы после ошибки DeepSeek повторять
     * только разбор, не отправляя фото ещё раз.
     */
    suspend fun recognize(jpeg: ByteArray, onStep: (ReceiptStep) -> Unit = {}): String {
        val ocr = ocrFor(settings.current())
        require(ocr != null) { NOT_READY }
        onStep(ReceiptStep.READING)
        return step(ReceiptService.OCR) { NetworkRetry.run { ocr.recognize(jpeg) } }
    }

    /** Шаг 2: текст чека → товары (DeepSeek) → черновик. */
    suspend fun parse(ocrText: String, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview {
        return parseParts(listOf(ocrText), onStep)
    }

    /** Все части уходят одним запросом, итог и товары объединяются один раз до сохранения. */
    suspend fun parseParts(texts: List<String>, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview {
        val llm = llmFor(settings.current())
        require(llm != null) { NOT_READY }
        return parseParts(texts, llm, onStep)
    }

    /** Разбор уже распознанного текста: отдельно, чтобы проверять без фото. */
    internal suspend fun parse(ocrText: String, llm: LlmClient, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview {
        return parseParts(listOf(ocrText), llm, onStep)
    }

    internal suspend fun parseParts(texts: List<String>, llm: LlmClient, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview {
        val cleaned = ReceiptParts.clean(texts)
        val multiple = cleaned.size > 1
        val totals = texts.mapNotNull(ReceiptText::printedTotal).distinct()
        if (totals.size > 1) throw ReceiptException("На снимках разные итоги. Проверьте, что это части одного чека, и удалите неверный снимок")
        onStep(ReceiptStep.PARSING)
        val cats = db.categories().getActiveWithSubs().selectable()
        val today = LocalDate.now(zone)
        val answer = step(ReceiptService.PARSE) {
            NetworkRetry.run {
                val names = cats.map { it.category.name }
                llm.complete(
                    if (multiple) ReceiptPrompt.partsSystem(names, today) else ReceiptPrompt.system(names, today),
                    if (multiple) ReceiptPrompt.partsUser(cleaned) else ReceiptPrompt.user(cleaned.single()),
                    jsonMode = true,
                )
            }
        }
        val parts = if (multiple) ReceiptParsing.decodeParts(answer, cleaned.size) else listOf(ReceiptParsing.decode(answer))
        val merged = ReceiptParts.merge(parts, cleaned)
        val review = ReceiptDraft.build(
            parsed = merged.receipt,
            printedTotal = totals.singleOrNull(),
            categories = cats,
            rules = db.rules().getAll(),
            knownPlaces = db.expenses().distinctPlaces(),
            today = today,
            receiptText = if (multiple) ReceiptPrompt.partsUser(cleaned) else cleaned.single(),
        )
        return review.copy(notices = merged.notices + review.notices)
    }

    private suspend fun <T> step(service: ReceiptService, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ReceiptServiceFailure(service, e)
        }

    private companion object {
        const val NOT_READY = "Заполните ключ и ID каталога Yandex Cloud и ключ DeepSeek в настройках ИИ"
    }
}

