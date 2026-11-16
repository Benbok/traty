package ru.my.traty.ai.receipt

import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.LlmHttpException
import ru.my.traty.ai.YandexGptClient
import ru.my.traty.data.AppSettings
import ru.my.traty.data.DEFAULT_RECEIPT_MODEL
import ru.my.traty.data.canScanReceipts
import java.io.IOException

/**
 * Фото чеков работают только через Яндекс, какой бы ИИ ни был выбран для голоса:
 * Vision OCR читает текст с фото, YandexGPT раскладывает его по товарам.
 * null — ключ или каталог Яндекса не заполнены, кнопка камеры не показывается.
 */
fun receiptOcrFor(s: AppSettings): ReceiptOcr? =
    if (s.canScanReceipts) YandexVisionOcr(s.yandexKey, s.yandexFolderId) else null

fun receiptLlmFor(s: AppSettings): LlmClient? =
    if (s.canScanReceipts) {
        YandexGptClient(
            apiKey = s.yandexKey,
            folderId = s.yandexFolderId,
            model = s.yandexReceiptModel.ifBlank { DEFAULT_RECEIPT_MODEL },
            maxTokens = RECEIPT_MAX_TOKENS,
        )
    } else null

/** Хватает на чек примерно из 80 позиций. */
private const val RECEIPT_MAX_TOKENS = 4_000

/** Понятный текст ошибки для экрана чека. Ключ и тело ответа в текст не попадают. */
object ReceiptErrors {
    fun text(e: Throwable): String = when {
        e is LlmHttpException && (e.code == 401 || e.code == 403) ->
            "Яндекс не принял ключ. Проверьте API-ключ и ID каталога, а у сервисного аккаунта — роли " +
                "ai.vision.user и ai.languageModels.user"
        e is LlmHttpException && e.code == 429 -> "Яндекс просит подождать: слишком много запросов. Попробуйте через минуту"
        e is LlmHttpException && e.code == 400 -> "Яндекс не смог прочитать фото. Переснимите чек ближе и ровнее"
        e is LlmHttpException -> "Сервис Яндекса ответил ошибкой ${e.code}. Попробуйте ещё раз"
        e is IOException -> "Нет связи с Яндексом. Проверьте интернет и попробуйте ещё раз"
        e is ReceiptException || e is IllegalArgumentException -> e.message ?: "Не получилось разобрать чек"
        else -> "Не получилось разобрать чек. Попробуйте ещё раз"
    }
}
