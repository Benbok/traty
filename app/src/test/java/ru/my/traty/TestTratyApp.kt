package ru.my.traty

import androidx.room.Room
import ru.my.traty.data.AppDatabase

/** Приложение для тестов ViewModel: база в памяти вместо настоящей traty.db. */
class TestTratyApp : TratyApp() {
    // Фоновый seed на Dispatchers.IO переживает тест и роняет соседей (UncaughtExceptionsBeforeTest).
    override val seedOnStart: Boolean get() = false

    override fun createContainer(): AppContainer = AppContainer(
        this,
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build(),
    )
}
