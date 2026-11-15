package ru.my.traty.ui.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Версия приложения, модель телефона и Android — то, что добавляется к письму. */
fun Context.feedbackDeviceInfo(): FeedbackDeviceInfo {
    val version = try {
        @Suppress("DEPRECATION")
        val p = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        "${p.versionName} (${p.versionCode})"
    } catch (_: Exception) {
        "неизвестна"
    }
    return FeedbackDeviceInfo(
        appVersion = version,
        device = deviceName(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()),
        android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )
}

/**
 * Открывает почтовое приложение с готовым письмом. Отправляет его сам пользователь.
 * Тема и текст передаются и в ссылке mailto, и в extras: одни почтовые приложения читают одно, другие другое.
 * @return false, если на телефоне нет почтового приложения.
 */
fun Context.openFeedbackMail(letter: FeedbackLetter): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(letter.mailtoUri())).apply {
        if (letter.to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(letter.to))
        putExtra(Intent.EXTRA_SUBJECT, letter.subject)
        putExtra(Intent.EXTRA_TEXT, letter.body)
    }
    return try {
        // Без createChooser: системный выбор не бросает ошибку, а показывает пустой список,
        // и понятное сообщение «нет почты» тогда не появится.
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
