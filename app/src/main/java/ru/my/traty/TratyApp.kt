package ru.my.traty

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.SettingsRepository

open class TratyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        if (seedOnStart) container.seedIfEmpty()
    }

    protected open val seedOnStart: Boolean get() = true

    /** Тестовый шов: тесты подменяют контейнер (например, БД в памяти). */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}

/** Простейший «DI»: все общие объекты создаются здесь один раз. */
class AppContainer(
    context: Context,
    val db: AppDatabase = AppDatabase.build(context),
) {
    val settings = SettingsRepository(context)
    val categories = CategoryRepository(db)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfEmpty() {
        scope.launch { categories.seedIfEmpty() }
    }
}
