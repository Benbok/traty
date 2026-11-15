package ru.my.traty

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.clientFor
import ru.my.traty.capture.PendingParseWork
import ru.my.traty.capture.QuickEntry
import ru.my.traty.data.search.SearchRepository
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.IncomeRepository
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.ReconciliationRepository
import ru.my.traty.data.budget.BudgetAlertWatcher
import ru.my.traty.data.budget.BudgetRepository
import ru.my.traty.data.digest.DailyDigestWork
import ru.my.traty.data.onboarding.OnboardingRepository
import ru.my.traty.data.weekly.WeeklyTaskRepository
import ru.my.traty.data.weekly.WeeklyTaskWork
import ru.my.traty.data.recurring.RecurringReminderWork
import ru.my.traty.data.SettingsRepository
import ru.my.traty.speech.GigaAmRuntime
import ru.my.traty.speech.ModelManager
import java.io.File
import ru.my.traty.widget.TratyRecordWidget
import ru.my.traty.widget.TratyWidget

open class TratyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        if (seedOnStart) {
            container.seedIfEmpty()
            container.protectSecrets()
        }
        if (updateWidgetOnChanges) {
            appScope.launch {
                container.quickEntry.changes.collect {
                    // Виджеты не должны ронять приложение, если их нет на рабочем столе
                    runCatching {
                        TratyWidget().updateAll(this@TratyApp)
                        TratyRecordWidget().updateAll(this@TratyApp)
                    }
                }
            }
        }
        if (parsePendingOnStart) {
            runCatching { RecurringReminderWork.schedule(this) }
            runCatching { DailyDigestWork.schedule(this) }
            container.budgetAlerts.start(appScope)
            runCatching { WeeklyTaskWork.schedule(this) }
            // Есть фразы без разбора (в т. ч. оставшиеся с прошлого запуска) — поставить фоновый разбор
            appScope.launch {
                container.db.pendingPhrases().observeAll()
                    .map { list -> list.any { it.status == PendingStatus.WAITING } }
                    // Без distinctUntilChanged: каждое изменение очереди снова ставит разбор (KEEP не трогает активный, но заменяет упавший)
                    .filter { it }
                    .collect { runCatching { PendingParseWork.enqueue(this@TratyApp) } }
            }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected open val seedOnStart: Boolean get() = true

    /** Тесты выключают: WorkManager в Robolectric не нужен. */
    protected open val parsePendingOnStart: Boolean get() = true

    /** Тесты выключают: Glance в Robolectric не нужен. */
    protected open val updateWidgetOnChanges: Boolean get() = true

    /** Тестовый шов: тесты подменяют контейнер (например, БД в памяти). */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}

/** Простейший «DI»: все общие объекты создаются здесь один раз. */
class AppContainer(
    context: Context,
    val db: AppDatabase = AppDatabase.build(context),
    clientProvider: (AppSettings) -> LlmClient? = ::clientFor,
) {
    /** Единая точка выбора языковой модели: экраны берут клиента здесь, тесты подменяют. */
    val llm: (AppSettings) -> LlmClient? = clientProvider
    val settings = SettingsRepository(context)
    val categories = CategoryRepository(db)
    val reconciliations = ReconciliationRepository(db, categories)
    val onboarding = OnboardingRepository(db, settings)
    val incomes = IncomeRepository(db)
    val quickEntry = QuickEntry(db, categories, settings, clientProvider = llm)
    val budgets = BudgetRepository(db)
    val weeklyTasks = WeeklyTaskRepository(db)
    val search = SearchRepository(db)

    /** Уведомления «потрачено 80 % / 100 % плана»; запускается из [TratyApp.onCreate]. */
    val budgetAlerts = BudgetAlertWatcher(context.applicationContext, db, settings)

    /** Модель GigaAM: качается в filesDir; можно положить вручную в Android/data/<пакет>/files/models/gigaam. */
    val models = ModelManager(
        listOfNotNull(
            File(context.filesDir, "models/gigaam"),
            context.getExternalFilesDir(null)?.let { File(it, "models/gigaam") },
        )
    )
    val gigaAm = GigaAmRuntime(context, models)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Скачивание модели речи в процессном скоупе: переживает закрытие экрана настроек. */
    fun downloadModel() {
        scope.launch { models.download() }
    }

    fun seedIfEmpty() {
        scope.launch { categories.seedIfEmpty() }
    }

    /** Ключи ИИ из старых версий (открытым текстом) шифруются при первом запуске. */
    fun protectSecrets() {
        scope.launch { runCatching { settings.upgradeStoredSecrets() } }
    }
}
