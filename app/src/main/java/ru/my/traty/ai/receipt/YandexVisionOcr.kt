package ru.my.traty.ai.receipt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import ru.my.traty.ai.postJson
import ru.my.traty.ai.textAt
import java.util.Base64

/** Распознавание текста на фото. Отдельный интерфейс, чтобы тесты подменяли сервис. */
interface ReceiptOcr {
    /** [jpeg] — фото чека в JPEG; возвращает распознанный текст строками. */
    suspend fun recognize(jpeg: ByteArray): String
}

/**
 * Yandex Vision OCR (AI Studio), синхронный запрос: одна картинка — один ответ.
 * Модель `page` читает печатный текст в одну колонку, как на кассовой ленте; языки — русский и английский.
 * Сервисному аккаунту ключа нужна роль `ai.vision.user`.
 */
class YandexVisionOcr(
    private val apiKey: String,
    private val folderId: String,
) : ReceiptOcr {
    override suspend fun recognize(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank() && folderId.isNotBlank()) {
            "Не указан API-ключ или ID каталога Yandex Cloud (Настройки → Разбор фраз и ИИ)"
        }
        val body = buildJsonObject {
            put("mimeType", "JPEG")
            putJsonArray("languageCodes") { add("ru"); add("en") }
            put("model", MODEL)
            put("content", Base64.getEncoder().encodeToString(jpeg))
        }
        val resp = postJson(
            URL,
            body,
            mapOf(
                "Authorization" to "Api-Key $apiKey",
                "x-folder-id" to folderId,
                // Яндекс не сохраняет фото у себя для обучения
                "x-data-logging-enabled" to "false",
            ),
            TIMEOUT_SECONDS,
        )
        fullText(resp)
    }

    companion object {
        const val URL = "https://ai.api.cloud.yandex.net/ocr/v1/recognizeText"
        const val MODEL = "page"
        private const val TIMEOUT_SECONDS = 45L

        /** Текст из ответа: `result.textAnnotation.fullText` (в документации встречается и без `result`). */
        internal fun fullText(resp: JsonObject): String {
            val root = (resp["result"] as? JsonObject) ?: resp
            return root.textAt("Yandex Vision") {
                it["textAnnotation"]?.jsonObject?.get("fullText")?.jsonPrimitive?.content
            }
        }
    }
}
