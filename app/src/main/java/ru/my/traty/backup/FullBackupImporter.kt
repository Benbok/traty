package ru.my.traty.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.backup.BackupRepository

/** [settingsRestored] = false — данные восстановлены, а настройки записать не удалось (остались прежние). */
data class RestoreResult(val records: Int, val settingsRestored: Boolean)

/**
 * Восстановление «заменить всё»: сначала вся проверка в памяти ([FullBackupRestore]), потом одна транзакция Room.
 * Если копия повреждена или запись упала, база остаётся как была. Слияния с имеющимися данными нет.
 */
class FullBackupImporter(
    private val repo: BackupRepository,
    private val settings: SettingsRepository?,
) {
    suspend fun restore(b: FullBackup): RestoreResult {
        val snapshot = withContext(Dispatchers.Default) { FullBackupRestore.toSnapshot(b) }
        withContext(Dispatchers.IO) { repo.replaceAll(snapshot) }
        // Настройки в отдельном хранилище: в транзакцию Room они не входят, поэтому пишутся после данных
        val settingsOk = try {
            settings?.restore(b.settings)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        return RestoreResult(b.recordCount, settingsOk)
    }
}
