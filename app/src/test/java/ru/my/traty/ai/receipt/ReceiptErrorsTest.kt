package ru.my.traty.ai.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.LlmHttpException
import java.io.IOException

class ReceiptErrorsTest {
    @Test fun keyRejected_namesTheService() {
        val ocr = ReceiptErrors.text(ReceiptServiceFailure(ReceiptService.OCR, LlmHttpException(403, "x")))
        val parse = ReceiptErrors.text(ReceiptServiceFailure(ReceiptService.PARSE, LlmHttpException(401, "x")))
        assertTrue(ocr.startsWith("Яндекс") && ocr.contains("ai.vision.user"))
        assertEquals("DeepSeek не принял ключ. Проверьте его в настройках ИИ", parse)
    }

    @Test fun noNetwork_namesTheService() {
        val text = ReceiptErrors.text(ReceiptServiceFailure(ReceiptService.PARSE, IOException("timeout")))
        assertTrue(text.contains("DeepSeek"))
    }

    @Test fun badPhotoOnlyForOcr() {
        assertTrue(ReceiptErrors.text(ReceiptServiceFailure(ReceiptService.OCR, LlmHttpException(400, "x"))).contains("Переснимите"))
        assertTrue(ReceiptErrors.text(ReceiptServiceFailure(ReceiptService.PARSE, LlmHttpException(400, "x"))).contains("ошибкой 400"))
    }

    @Test fun ownMessagesPassThrough() {
        assertEquals("Фото слишком большое", ReceiptErrors.text(ReceiptException("Фото слишком большое")))
    }
}
