package ru.my.traty.ui.feedback

import java.net.URLEncoder

/**
 * Адрес, на который приходят сообщения о проблемах.
 * Пока пустой: почта откроется с незаполненным получателем. Подставить адрес перед раздачей сборки.
 */
const val FEEDBACK_EMAIL = ""

const val FEEDBACK_SUBJECT = "Найденные проблемы"

/** Длинное письмо в ссылке mailto некоторые почтовые приложения обрезают или не открывают. */
const val FEEDBACK_MAX_LENGTH = 3000

/** Что приложение добавляет к письму, чтобы было понятно, на каком телефоне проблема. */
data class FeedbackDeviceInfo(
    val appVersion: String,
    val device: String,
    val android: String,
)

/** Готовое письмо: получатель, тема, текст и ссылка mailto для почтового приложения. */
data class FeedbackLetter(
    val to: String,
    val subject: String,
    val body: String,
) {
    /**
     * mailto:адрес?subject=…&body=… с пробелами как %20 (не «+», иначе почта покажет плюсы).
     * Адрес не кодируется: «@» в нём разрешён, а «%40» понимают не все почтовые приложения.
     */
    fun mailtoUri(): String =
        "mailto:$to?subject=${encode(subject)}&body=${encode(body)}"

    private fun encode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")
}

/** Собирает письмо из текста пользователя и сведений о телефоне. Пустой текст — null, отправлять нечего. */
fun buildFeedbackLetter(
    text: String,
    info: FeedbackDeviceInfo,
    to: String = FEEDBACK_EMAIL,
): FeedbackLetter? {
    val message = text.trim().take(FEEDBACK_MAX_LENGTH)
    if (message.isEmpty()) return null
    val body = buildString {
        appendLine(message)
        appendLine()
        appendLine("—")
        appendLine("Версия приложения: ${info.appVersion}")
        appendLine("Телефон: ${info.device}")
        append("Android: ${info.android}")
    }
    return FeedbackLetter(to = to.trim(), subject = FEEDBACK_SUBJECT, body = body)
}

/** «Samsung SM-A525F»; производитель не повторяется, если модель уже с него начинается («Xiaomi 13T»). */
fun deviceName(manufacturer: String, model: String): String {
    val m = model.trim()
    val brand = manufacturer.trim().replaceFirstChar { it.uppercase() }
    return when {
        brand.isEmpty() -> m
        m.isEmpty() -> brand
        m.startsWith(brand, ignoreCase = true) -> m
        else -> "$brand $m"
    }
}
