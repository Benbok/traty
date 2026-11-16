package ru.my.traty.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRetryTest {
    @Test
    fun retriesOnlyTransientServerErrors() {
        for (code in listOf(429, 500, 502, 503, 504)) assertTrue("$code", LlmRetry.shouldRetry(code))
        // Неверный ключ, нет денег, плохой запрос — повтор ничего не изменит
        for (code in listOf(400, 401, 402, 403, 404, 501)) assertFalse("$code", LlmRetry.shouldRetry(code))
    }

    @Test
    fun pauseFollowsRetryAfterButStaysShort() {
        assertEquals(1_000L, LlmRetry.delayMillis(null))
        assertEquals(1_000L, LlmRetry.delayMillis("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(3_000L, LlmRetry.delayMillis("3"))
        assertEquals(5_000L, LlmRetry.delayMillis("120"))
        assertEquals(1_000L, LlmRetry.delayMillis("0"))
    }

    @Test
    fun entryWaitsLessThanAssistant() {
        assertTrue(LlmRetry.ENTRY_TIMEOUT_SECONDS < LlmRetry.DEFAULT_TIMEOUT_SECONDS)
        assertEquals(1, LlmRetry.RETRIES)
    }
}
