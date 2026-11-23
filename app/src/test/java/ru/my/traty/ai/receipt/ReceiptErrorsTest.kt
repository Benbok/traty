package ru.my.traty.ai.receipt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.LlmHttpException
import java.io.IOException
import java.net.SocketTimeoutException

class ReceiptErrorsTest {
    @Test fun errorsIdentifyDeepSeekAndHideProviderResponseBody() {
        val detail = "sensitive provider response"
        for (code in listOf(400, 401, 403, 503)) {
            val message = ReceiptErrors.text(LlmHttpException(code, detail))
            assertTrue(message.contains("DeepSeek"))
            assertFalse(message.contains(detail))
        }
        assertFalse(ReceiptErrors.text(IllegalArgumentException(detail)).contains(detail))
    }

    @Test fun transientFailuresAndInvalidResponsesCanBeRetried() {
        assertTrue(ReceiptErrors.isRetryable(IOException()))
        assertTrue(ReceiptErrors.isRetryable(LlmHttpException(503, "")))
        assertTrue(ReceiptErrors.isRetryable(ReceiptException(BAD_RECEIPT_ANSWER)))
        assertFalse(ReceiptErrors.isRetryable(LlmHttpException(403, "")))
        assertTrue(ReceiptErrors.text(SocketTimeoutException()).contains("не успел"))
    }
}
