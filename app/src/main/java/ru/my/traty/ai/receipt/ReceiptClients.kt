package ru.my.traty.ai.receipt

import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.DeepSeekClient
import ru.my.traty.data.AppSettings
import ru.my.traty.data.canScanReceipts

/**
 * Фото чеков, какой бы ИИ ни был выбран для голоса: Yandex Vision OCR читает текст с фото,
 * DeepSeek раскладывает этот текст по товарам. null — не заполнен ключ Яндекса, его каталог
 * или ключ DeepSeek: кнопка камеры не показывается.
 */
fun receiptOcrFor(s: AppSettings): ReceiptOcr? =
    if (s.canScanReceipts) YandexVisionOcr(s.yandexKey, s.yandexFolderId) else null

fun receiptLlmFor(s: AppSettings): LlmClient? =
    if (s.canScanReceipts) {
        // Длинный чек DeepSeek раскладывает дольше обычной фразы: обычный срок в минуту, а не 15 секунд ввода голосом
        DeepSeekClient(s.deepseekKey, s.deepseekModel)
    } else null
