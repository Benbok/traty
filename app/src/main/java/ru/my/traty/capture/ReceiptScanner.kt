package ru.my.traty.capture

import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.receipt.ReceiptOcr
import ru.my.traty.ai.receipt.ReceiptException
import ru.my.traty.ai.receipt.ReceiptParsing
import ru.my.traty.ai.receipt.ReceiptPrompt
import ru.my.traty.ai.receipt.ReceiptText
import ru.my.traty.ai.receipt.receiptLlmFor
import ru.my.traty.ai.receipt.receiptOcrFor
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.selectable
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
 * В Яндекс уходят фото (Vision OCR), текст чека без служебных строк и названия категорий (YandexGPT).
 */
class ReceiptScanner(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val ocrFor: (AppSettings) -> ReceiptOcr? = ::receiptOcrFor,
    private val llmFor: (AppSettings) -> LlmClient? = ::receiptLlmFor,
) {
    suspend fun scan(jpeg: ByteArray, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview {
        val s = settings.current()
        val ocr = ocrFor(s)
        val llm = llmFor(s)
        require(ocr != null && llm != null) { "Заполните API-ключ и ID каталога Yandex Cloud в настройках ИИ" }
        onStep(ReceiptStep.READING)
        val text = ocr.recognize(jpeg)
        return parse(text, llm, onStep)
    }

    /** Разбор уже распознанного текста: отдельно, чтобы проверять без фото. */
    internal suspend fun parse(ocrText: String, llm: LlmClient, onStep: (ReceiptStep) -> Unit = {}): ReceiptReview {
        val forAi = ReceiptText.forAi(ocrText)
        if (forAi.length < MIN_TEXT) throw ReceiptException("На фото не нашлось текста чека. Переснимите его ближе и при хорошем свете")
        onStep(ReceiptStep.PARSING)
        val cats = db.categories().getActiveWithSubs().selectable()
        val today = LocalDate.now(zone)
        val answer = llm.complete(ReceiptPrompt.system(cats.map { it.category.name }, today), ReceiptPrompt.user(forAi), jsonMode = true)
        val parsed = ReceiptParsing.decode(answer)
        return ReceiptDraft.build(
            parsed = parsed,
            printedTotal = ReceiptText.printedTotal(ocrText),
            categories = cats,
            rules = db.rules().getAll(),
            knownPlaces = db.expenses().distinctPlaces(),
            today = today,
            receiptText = forAi,
        )
    }

    private companion object {
        /** Меньше этого — на фото не чек (или он не прочитался). */
        const val MIN_TEXT = 10
    }
}
