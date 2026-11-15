package ru.my.traty.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.my.traty.data.period.MAX_PERIOD_START_DAY
import ru.my.traty.data.period.MIN_PERIOD_START_DAY
import ru.my.traty.data.secure.AesGcmSecretCipher
import ru.my.traty.data.secure.KeystoreKeys
import ru.my.traty.data.secure.SecretCodec

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

enum class VoiceEngine(val title: String) {
    GIGAAM("GigaAM (на телефоне, без интернета)"),
    SYSTEM("Системное распознавание (Google)"),
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
    /** Чем распознаётся речь. GigaAM работает, только когда модель скачана; иначе — системное. */
    val voiceEngine: VoiceEngine = VoiceEngine.GIGAAM,
    /** Показывать метки 🔺 🔁 🆕 ⚡ 📅 на тратах. */
    val labelsEnabled: Boolean = true,
    /** Вечернее уведомление «Итог дня» (около 20:00). */
    val dailyDigestEnabled: Boolean = true,
    /** Ключи скрытых карточек наблюдений на Главной. */
    val dismissedObservations: Set<String> = emptySet(),
    /** Карточка «План трат» на Тратах: показаны ли категории. По умолчанию свёрнуто — только общий итог. */
    val homePlanExpanded: Boolean = false,
)

internal fun encodeLines(lines: List<String>): String =
    lines.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.joinToString("\n")

internal fun decodeLines(raw: String?): List<String> =
    raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

private val Context.dataStore by preferencesDataStore(name = "settings")

private const val MAX_DISMISSED = 60

/**
 * API-ключи ИИ хранятся зашифрованными ключом Android Keystore ([SecretCodec]);
 * сам файл настроек лежит в приватной папке приложения и исключён из резервных копий и переноса.
 */
class SettingsRepository(
    private val context: Context,
    private val secrets: SecretCodec = SecretCodec(AesGcmSecretCipher(KeystoreKeys::secretsKey)),
) {
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
        val voiceEngine = stringPreferencesKey("voice_engine")
        val labelsEnabled = booleanPreferencesKey("labels_enabled")
        val dailyDigest = booleanPreferencesKey("daily_digest")
        val dismissedObservations = stringSetPreferencesKey("dismissed_observations")
        val homePlanExpanded = booleanPreferencesKey("home_plan_expanded")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = p[K.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            provider = p[K.provider]?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() } ?: d.provider,
            deepseekKey = secrets.open(p[K.dsKey]),
            deepseekModel = p[K.dsModel] ?: d.deepseekModel,
            yandexKey = secrets.open(p[K.yaKey]),
            yandexFolderId = p[K.yaFolder] ?: "",
            yandexModel = p[K.yaModel] ?: d.yandexModel,
            periodStartDay = (p[K.periodStartDay] ?: d.periodStartDay)
                .coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY),
            reconcileSnoozeUntilEpochDay = (p[K.reconcileSnooze] ?: 0L).coerceAtLeast(0L),
            calculatorLines = decodeLines(p[K.calculatorLines]),
            voiceEngine = p[K.voiceEngine]?.let { runCatching { VoiceEngine.valueOf(it) }.getOrNull() } ?: d.voiceEngine,
            labelsEnabled = p[K.labelsEnabled] ?: d.labelsEnabled,
            dailyDigestEnabled = p[K.dailyDigest] ?: d.dailyDigestEnabled,
            dismissedObservations = p[K.dismissedObservations] ?: d.dismissedObservations,
            homePlanExpanded = p[K.homePlanExpanded] ?: d.homePlanExpanded,
        )
    }
        // Расшифровка ключей обращается к Keystore — не на главном потоке
        .flowOn(Dispatchers.Default)

    suspend fun current(): AppSettings = flow.first()

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[K.themeMode] = s.themeMode.name
            p[K.provider] = s.provider.name
            p[K.dsKey] = secrets.seal(s.deepseekKey.trim())
            p[K.dsModel] = s.deepseekModel.trim()
            p[K.yaKey] = secrets.seal(s.yandexKey.trim())
            p[K.yaFolder] = s.yandexFolderId.trim()
            p[K.yaModel] = s.yandexModel.trim()
            p[K.periodStartDay] = s.periodStartDay.coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY)
        }
    }

    /** Перешифровывает ключи, сохранённые старыми версиями открытым текстом. */
    suspend fun upgradeStoredSecrets() {
        context.dataStore.edit { p ->
            for (key in listOf(K.dsKey, K.yaKey)) {
                val stored = p[key]
                if (secrets.needsUpgrade(stored)) p[key] = secrets.seal(stored!!)
            }
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

    suspend fun setVoiceEngine(engine: VoiceEngine) {
        context.dataStore.edit { p -> p[K.voiceEngine] = engine.name }
    }

    /** Скрывает карточку наблюдения. Помним последние [MAX_DISMISSED] ключей: старые периоды уже не вернутся. */
    suspend fun dismissObservation(key: String) {
        context.dataStore.edit { p ->
            val kept = (p[K.dismissedObservations].orEmpty() - key).toList().takeLast(MAX_DISMISSED - 1)
            p[K.dismissedObservations] = (kept + key).toSet()
        }
    }

    suspend fun setDailyDigestEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[K.dailyDigest] = enabled }
    }

    suspend fun setHomePlanExpanded(expanded: Boolean) {
        context.dataStore.edit { p -> p[K.homePlanExpanded] = expanded }
    }

    suspend fun setLabelsEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[K.labelsEnabled] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { p ->
            p[K.themeMode] = mode.name
        }
    }
}
