package ru.my.traty.ai.receipt

import ru.my.traty.data.AppSettings
import ru.my.traty.data.canUseLegacyReceiptOcr

/** Yandex Vision для сравнения с прямым распознаванием через DeepSeek. */
fun receiptOcrFor(s: AppSettings): ReceiptOcr? =
    if (s.canUseLegacyReceiptOcr) YandexVisionOcr(s.yandexKey, s.yandexFolderId) else null
