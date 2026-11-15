package ru.my.traty.ui.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackLetterTest {

    private val info = FeedbackDeviceInfo(appVersion = "0.5 (12)", device = "Samsung SM-A525F", android = "14 (API 34)")

    @Test
    fun blankTextGivesNoLetter() {
        assertNull(buildFeedbackLetter("   \n ", info, to = "a@b.ru"))
    }

    @Test
    fun letterHasSubjectTextAndDeviceInfo() {
        val letter = buildFeedbackLetter("  Не сохраняется трата  ", info, to = " a@b.ru ")!!
        assertEquals("a@b.ru", letter.to)
        assertEquals("Найденные проблемы", letter.subject)
        assertTrue(letter.body.startsWith("Не сохраняется трата\n"))
        assertTrue(letter.body.contains("Версия приложения: 0.5 (12)"))
        assertTrue(letter.body.contains("Телефон: Samsung SM-A525F"))
        assertTrue(letter.body.contains("Android: 14 (API 34)"))
    }

    @Test
    fun longTextIsCut() {
        val letter = buildFeedbackLetter("я".repeat(FEEDBACK_MAX_LENGTH + 500), info, to = "")!!
        assertEquals(FEEDBACK_MAX_LENGTH, letter.body.substringBefore("\n").length)
    }

    @Test
    fun mailtoEncodesSpacesAndSpecialCharacters() {
        val letter = FeedbackLetter(to = "a@b.ru", subject = "Найденные проблемы", body = "1+1 & ?=\nok")
        val uri = letter.mailtoUri()
        assertTrue(uri.startsWith("mailto:a@b.ru?subject="))
        assertTrue(!uri.contains("+") && !uri.contains(" "))
        val body = uri.substringAfter("&body=")
        assertEquals("1%2B1%20%26%20%3F%3D%0Aok", body)
        val subject = uri.substringAfter("subject=").substringBefore("&")
        assertEquals("Найденные проблемы", java.net.URLDecoder.decode(subject, "UTF-8"))
    }

    @Test
    fun emptyAddressStillGivesValidMailto() {
        val letter = buildFeedbackLetter("текст", info, to = "")!!
        assertTrue(letter.mailtoUri().startsWith("mailto:?subject="))
    }

    @Test
    fun deviceNameDoesNotRepeatBrand() {
        assertEquals("Samsung SM-A525F", deviceName("samsung", "SM-A525F"))
        assertEquals("Google Pixel 7", deviceName("Google", "Pixel 7"))
        assertEquals("Xiaomi 13T", deviceName("Xiaomi", "Xiaomi 13T"))
        assertEquals("Redmi Note 12", deviceName("", "Redmi Note 12"))
    }
}
