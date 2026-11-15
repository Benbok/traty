package ru.my.traty.data.onboarding

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.SettingsRepository

/** Флаг «знакомство показано» и проверка, есть ли у человека записи. */
class OnboardingRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    /** Показать ли знакомство при запуске. Заодно ставит флаг тем, кто обновился с записями. */
    suspend fun shouldShowOnLaunch(): Boolean {
        val seen = settings.onboardingSeen.first()
        if (seen) return false
        val hasRecords = hasRecords()
        if (OnboardingPolicy.markSeenSilently(seen, hasRecords)) settings.setOnboardingSeen(true)
        return OnboardingPolicy.showOnLaunch(seen, hasRecords)
    }

    suspend fun markSeen() = settings.setOnboardingSeen(true)

    /** Есть ли хоть одна трата или пополнение. Один лёгкий запрос, без загрузки списков. */
    suspend fun hasRecords(): Boolean = withContext(Dispatchers.IO) {
        db.query(SimpleSQLiteQuery(HAS_RECORDS)).use { c -> c.moveToFirst() && c.getInt(0) != 0 }
    }

    private companion object {
        const val HAS_RECORDS =
            "SELECT EXISTS(SELECT 1 FROM expenses) OR EXISTS(SELECT 1 FROM incomes)"
    }
}
