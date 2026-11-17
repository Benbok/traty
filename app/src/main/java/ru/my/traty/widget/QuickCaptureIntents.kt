package ru.my.traty.widget

import android.content.Context
import android.content.Intent
import ru.my.traty.MainActivity
import ru.my.traty.ui.capture.CaptureActivity
import ru.my.traty.ui.receipt.RECEIPT_CAMERA_ACTION

/** Разные явные Intent: кнопки виджета не подменяют действия друг друга. */
fun quickVoiceIntent(context: Context): Intent =
    Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun quickReceiptIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(RECEIPT_CAMERA_ACTION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
