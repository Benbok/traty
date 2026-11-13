package ru.my.traty

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.SettingsRepository

class TratyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedIfEmpty()
    }
}

/** Простейший «DI»: все общие объекты создаются здесь один раз. */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfEmpty() {
        scope.launch {
            if (db.categories().count() == 0) db.categories().upsert(DefaultCategories.list)
        }
    }
}
