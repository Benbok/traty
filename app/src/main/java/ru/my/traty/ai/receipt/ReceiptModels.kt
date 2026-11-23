package ru.my.traty.ai.receipt

import ru.my.traty.data.items.Pack

/** Цена строки — напечатанная сумма за всю позицию с учётом скидки, в копейках. */
data class ReceiptLine(
    val name: String,
    val priceKopecks: Long?,
    val quantityMilli: Long? = null,
    val unit: String? = null,
    val pack: Pack? = null,
    val unitPriceKopecks: Long? = null,
    val discountKopecks: Long? = null,
)

data class ParsedReceipt(
    val place: String?,
    val date: String?,
    val totalKopecks: Long?,
    val category: String?,
    val lines: List<ReceiptLine>,
)

class ReceiptException(message: String) : Exception(message)

internal const val BAD_RECEIPT_ANSWER = "ИИ ответил не в том формате, попробуйте ещё раз"
