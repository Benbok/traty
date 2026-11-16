package ru.my.traty.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

private const val TAG = "TratyEntry"

/**
 * Отладочный журнал разбора фразы: что услышано, что ответил ИИ, что сделали правила приложения и что попало в черновик.
 * Только для отладочной сборки (суммы и текст диктовки не должны попадать в журнал release, см. CLAUDE.md, раздел 4);
 * в release возвращает null, и ничего не пишется.
 * Смотреть: `adb logcat -s TratyEntry` или фильтр по тегу TratyEntry в Android Studio.
 */
fun debugEntryLog(context: Context): ((String) -> Unit)? {
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return null
    return { message -> message.lines().forEach { Log.d(TAG, it) } }
}
