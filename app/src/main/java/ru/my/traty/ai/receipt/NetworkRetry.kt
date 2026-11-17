package ru.my.traty.ai.receipt

import kotlinx.coroutines.delay
import ru.my.traty.ai.LlmHttpException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Повтор при обрыве связи: мобильный интернет пропадает на секунды, и одна неудача не должна
 * выбрасывать человека на экран ошибки. Повторяется только сетевая ошибка ([IOException]):
 * ответ сервера с кодом ([LlmHttpException]) не повторяется — 401 или 400 от повтора не исправятся,
 * а 429 и 5xx уже повторяет сам HTTP-клиент. Истёкший срок ожидания ([InterruptedIOException]) тоже
 * не повторяется: человек уже прождал полминуты, дальше решает он сам кнопкой «Повторить».
 */
object NetworkRetry {
    /** Паузы перед повторами, мс: всего три попытки за ~6 секунд ожидания. */
    val DEFAULT_PAUSES = listOf(2_000L, 4_000L)

    suspend fun <T> run(
        pauses: List<Long> = DEFAULT_PAUSES,
        wait: suspend (Long) -> Unit = { delay(it) },
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (e is LlmHttpException || e is InterruptedIOException || attempt >= pauses.size) throw e
                wait(pauses[attempt])
                attempt++
            }
        }
    }
}
