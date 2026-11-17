package ru.my.traty.ai.receipt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.LlmHttpException
import java.io.IOException
import java.net.SocketTimeoutException

class NetworkRetryTest {
    private val waits = mutableListOf<Long>()

    private fun <T> run(block: suspend () -> T): Result<T> = runBlocking {
        runCatching { NetworkRetry.run(wait = { waits += it }, block = block) }
    }

    @Test fun shortDrop_isRetriedAndSucceeds() {
        var calls = 0
        val r = run { if (++calls < 3) throw IOException("reset") else "текст" }
        assertEquals("текст", r.getOrNull())
        assertEquals(3, calls)
        assertEquals(NetworkRetry.DEFAULT_PAUSES, waits)
    }

    @Test fun longOutage_givesUpAfterThreeAttempts() {
        var calls = 0
        val r = run<String> { calls++; throw IOException("no route") }
        assertTrue(r.exceptionOrNull() is IOException)
        assertEquals(3, calls)
    }

    @Test fun serverAnswerAndTimeout_notRetried() {
        var calls = 0
        assertTrue(run<String> { calls++; throw LlmHttpException(401, "x") }.isFailure)
        assertTrue(run<String> { calls++; throw SocketTimeoutException("timeout") }.isFailure)
        assertEquals(2, calls)
        assertTrue(waits.isEmpty())
    }

    @Test fun retryButton_onlyWhereItHelps() {
        assertTrue(ReceiptErrors.isRetryable(ReceiptServiceFailure(ReceiptService.OCR, IOException("x"))))
        assertTrue(ReceiptErrors.isRetryable(ReceiptServiceFailure(ReceiptService.PARSE, LlmHttpException(503, "x"))))
        assertTrue(ReceiptErrors.isRetryable(ReceiptException(ReceiptParsing.BAD_ANSWER)))
        assertFalse(ReceiptErrors.isRetryable(ReceiptServiceFailure(ReceiptService.OCR, LlmHttpException(403, "x"))))
        assertFalse(ReceiptErrors.isRetryable(ReceiptException("На фото не нашлось текста чека")))
    }
}
