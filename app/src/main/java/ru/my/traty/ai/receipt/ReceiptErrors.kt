package ru.my.traty.ai.receipt

import ru.my.traty.ai.LlmHttpException
import java.io.IOException

/** Понятный текст ошибки для экрана чека. Ключ и тело ответа в текст не попадают. */
object ReceiptErrors {
    /** [step] — на каком шаге ошибка: чтение фото (Яндекс) или разбор текста (DeepSeek). */
    fun text(e: Throwable, step: ReceiptService = ReceiptService.OCR): String = when {
        e is ReceiptServiceFailure -> text(e.cause ?: e, e.service)
        e is LlmHttpException && (e.code == 401 || e.code == 403) -> when (step) {
            ReceiptService.OCR ->
                "Яндекс не принял ключ. Проверьте API-ключ и ID каталога, а у сервисного аккаунта — роль ai.vision.user"
            ReceiptService.PARSE -> "DeepSeek не принял ключ. Проверьте его в настройках ИИ"
        }
        e is LlmHttpException && e.code == 402 -> "На счёте DeepSeek закончились деньги"
        e is LlmHttpException && e.code == 429 -> "${step.title} просит подождать: слишком много запросов. Попробуйте через минуту"
        e is LlmHttpException && e.code == 400 && step == ReceiptService.OCR -> "Яндекс не смог прочитать фото. Переснимите чек ближе и ровнее"
        e is LlmHttpException -> "${step.title} ответил ошибкой ${e.code}. Попробуйте ещё раз"
        e is IOException -> "Нет связи с сервисом ${step.title}. Проверьте интернет и попробуйте ещё раз"
        e is ReceiptException || e is IllegalArgumentException -> e.message ?: "Не получилось разобрать чек"
        else -> "Не получилось разобрать чек. Попробуйте ещё раз"
    }
}

/**
 * Поможет ли «Повторить» с тем же фото: да при обрыве связи, перегрузке и сбое сервиса;
 * нет, если не принят ключ, фото не читается или на нём нет чека — тут нужно чинить настройки или переснять.
 */
fun ReceiptErrors.isRetryable(e: Throwable): Boolean {
    val cause = if (e is ReceiptServiceFailure) e.cause ?: e else e
    return when {
        cause is LlmHttpException -> cause.code == 429 || cause.code == 408 || cause.code >= 500
        cause is IOException -> true
        // ИИ ответил не в формате: другой ответ на тот же текст обычно приходит нормальным
        cause is ReceiptException -> cause.message == ReceiptParsing.BAD_ANSWER
        else -> false
    }
}

/** Какой сервис работал, когда случилась ошибка. */
enum class ReceiptService(val title: String) {
    OCR("Яндекс"),
    PARSE("DeepSeek"),
}

/** Ошибка сервиса на шаге [service]: по ней экран говорит, кто не ответил — Яндекс или DeepSeek. */
class ReceiptServiceFailure(val service: ReceiptService, cause: Throwable) : Exception(cause)
