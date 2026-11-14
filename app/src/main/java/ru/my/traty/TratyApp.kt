package ru.my.traty

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.clientFor
import ru.my.traty.capture.QuickEntry
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.ReconciliationRepository
import ru.my.traty.data.SettingsRepository
import ru.my.traty.widget.TratyWidget

open class TratyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        if (seedOnStart) container.seedIfEmpty()
        if (updateWidgetOnChanges) {
            appScope.launch {
                container.quickEntry.changes.collect {
                    // Виджет не должен ронять приложение, если его нет на рабочем столе
                    runCatching { TratyWidget().updateAll(this@TratyApp) }
                }
            }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    protected open val seedOnStart: Boolean get() = true

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
    val settings = SettingsRepository(context)
    val categories = CategoryRepository(db)
    val reconciliations = ReconciliationRepository(db, categories)
    val quickEntry = QuickEntry(db, categories, settings, clientProvider = clientProvider)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfEmpty() {
        scope.launch { categories.seedIfEmpty() }
    }
}
