package ru.my.traty.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.my.traty.data.period.MAX_PERIOD_START_DAY
import ru.my.traty.data.period.MIN_PERIOD_START_DAY

enum class ThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

enum class LlmProvider(val title: String) {
    DEEPSEEK("DeepSeek"),
    YANDEX("YandexGPT"),
    OFFLINE("Без ИИ (простой разбор)"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val provider: LlmProvider = LlmProvider.DEEPSEEK,
    val deepseekKey: String = "",
    val deepseekModel: String = "deepseek-chat",
    val yandexKey: String = "",
    val yandexFolderId: String = "",
    val yandexModel: String = "yandexgpt-lite/latest",
    /** День месяца (1..28), с которого начинается расчётный период. */
    val periodStartDay: Int = MIN_PERIOD_START_DAY,
    /** Последний день (epoch day), в который карточка сверки скрыта кнопкой «Напомнить завтра»; 0 - нет. */
    val reconcileSnoozeUntilEpochDay: Long = 0,
    /** Названия строк калькулятора остатка (без сумм). */
    val calculatorLines: List<String> = emptyList(),
)

internal fun encodeLines(lines: List<String>): String =
    lines.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.joinToString("\n")

internal fun decodeLines(raw: String?): List<String> =
    raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Ключи хранятся в приватном хранилище приложения (другим приложениям недоступно,
 * allowBackup=false). Для личного APK этого достаточно.
 */
class SettingsRepository(private val context: Context) {
    private object K {
        val themeMode = stringPreferencesKey("theme_mode")
        val provider = stringPreferencesKey("provider")
        val dsKey = stringPreferencesKey("deepseek_key")
        val dsModel = stringPreferencesKey("deepseek_model")
        val yaKey = stringPreferencesKey("yandex_key")
        val yaFolder = stringPreferencesKey("yandex_folder")
        val yaModel = stringPreferencesKey("yandex_model")
        val periodStartDay = intPreferencesKey("period_start_day")
        val reconcileSnooze = longPreferencesKey("reconcile_snooze_until")
        val calculatorLines = stringPreferencesKey("calculator_lines")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = p[K.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            provider = p[K.provider]?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() } ?: d.provider,
            deepseekKey = p[K.dsKey] ?: "",
            deepseekModel = p[K.dsModel] ?: d.deepseekModel,
            yandexKey = p[K.yaKey] ?: "",
            yandexFolderId = p[K.yaFolder] ?: "",
            yandexModel = p[K.yaModel] ?: d.yandexModel,
            periodStartDay = (p[K.periodStartDay] ?: d.periodStartDay)
                .coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY),
            reconcileSnoozeUntilEpochDay = (p[K.reconcileSnooze] ?: 0L).coerceAtLeast(0L),
            calculatorLines = decodeLines(p[K.calculatorLines]),
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[K.themeMode] = s.themeMode.name
            p[K.provider] = s.provider.name
            p[K.dsKey] = s.deepseekKey.trim()
            p[K.dsModel] = s.deepseekModel.trim()
            p[K.yaKey] = s.yandexKey.trim()
            p[K.yaFolder] = s.yandexFolderId.trim()
            p[K.yaModel] = s.yandexModel.trim()
            p[K.periodStartDay] = s.periodStartDay.coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY)
        }
    }

    suspend fun setPeriodStartDay(day: Int) {
        context.dataStore.edit { p ->
            p[K.periodStartDay] = day.coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY)
        }
    }

    suspend fun setReconcileSnoozeUntil(epochDay: Long) {
        context.dataStore.edit { p -> p[K.reconcileSnooze] = epochDay.coerceAtLeast(0L) }
    }

    suspend fun setCalculatorLines(lines: List<String>) {
        context.dataStore.edit { p -> p[K.calculatorLines] = encodeLines(lines) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { p ->
            p[K.themeMode] = mode.name
        }
    }
}
