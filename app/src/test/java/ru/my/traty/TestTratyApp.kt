package ru.my.traty

import androidx.room.Room
import ru.my.traty.ai.LlmClient
import ru.my.traty.data.AppDatabase

/** Приложение для тестов ViewModel: база в памяти вместо настоящей traty.db и подменяемая языковая модель. */
class TestTratyApp : TratyApp() {
    // Фоновый seed на Dispatchers.IO переживает тест и роняет соседей (UncaughtExceptionsBeforeTest).
    override val seedOnStart: Boolean get() = false

    override fun createContainer(): AppContainer = AppContainer(
        this,
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build(),
    ) { llm }

    companion object {
        /** Какую модель «видит» QuickEntry в тесте; null — режим без ИИ. Тест выставляет сам. */
        @Volatile var llm: LlmClient? = null
    }
}
