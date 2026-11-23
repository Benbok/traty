package ru.my.traty.ai.receipt

import ru.my.traty.ai.LlmHttpException
import java.io.IOException
import java.io.InterruptedIOException

/** Сообщения для экрана чека. В них попадают только локальные подсказки и статус HTTP. */
object ReceiptErrors {
    fun text(e: Throwable): String = when (e) {
        is LlmHttpException -> when (e.code) {
            400, 413 -> "DeepSeek не смог принять фото. Переснимите чек ближе или уберите лишние кадры"
            401, 403 -> "DeepSeek не принял ключ. Проверьте его в настройках ИИ"
            402 -> "На счёте DeepSeek закончились деньги"
            429 -> "DeepSeek просит подождать. Попробуйте через минуту"
            else -> "DeepSeek ответил ошибкой ${e.code}. Попробуйте ещё раз"
        }
        is InterruptedIOException -> "DeepSeek не успел ответить. Попробуйте ещё раз или снимите чек по частям"
        is IOException -> "Нет связи с DeepSeek. Проверьте интернет и попробуйте ещё раз"
        is ReceiptException -> e.message ?: "Не получилось разобрать чек"
        else -> "Не получилось разобрать чек. Попробуйте ещё раз"
    }
}

fun ReceiptErrors.isRetryable(e: Throwable): Boolean = when (e) {
    is LlmHttpException -> e.code == 408 || e.code == 429 || e.code >= 500
    is IOException -> true
    is ReceiptException -> e.message == BAD_RECEIPT_ANSWER
    else -> false
}
