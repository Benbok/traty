package ru.my.traty.ai

/**
 * Сроки и повторы запросов к языковой модели.
 * Ввод траты голосом ждёт ответа сразу, поэтому ему дают 15 секунд вместо минуты: дольше человек ждать не будет,
 * а фраза не пропадёт, а встанет в очередь (как при обрыве сети). Разовая ошибка сервера (429, 500, 502, 503, 504)
 * не должна отправлять фразу в разбор без ИИ: запрос повторяется один раз.
 */
internal object LlmRetry {
    const val DEFAULT_TIMEOUT_SECONDS = 60L
    const val ENTRY_TIMEOUT_SECONDS = 15L

    private const val DEFAULT_DELAY_MS = 1_000L
    private const val MAX_DELAY_MS = 5_000L

    /** Сколько раз повторять запрос после первой неудачи. */
    const val RETRIES = 1

    fun shouldRetry(code: Int): Boolean = code == 429 || code == 500 || code in 502..504

    /** Пауза перед повтором: по заголовку Retry-After (в секундах), но не дольше пяти секунд. */
    fun delayMillis(retryAfter: String?): Long {
        val seconds = retryAfter?.trim()?.toLongOrNull() ?: return DEFAULT_DELAY_MS
        return (seconds * 1_000).coerceIn(DEFAULT_DELAY_MS, MAX_DELAY_MS)
    }
}
