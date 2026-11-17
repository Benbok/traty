package ru.my.traty.ui.backup

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.backup.FullBackup
import ru.my.traty.backup.FullBackupException
import ru.my.traty.backup.FullBackupReader
import ru.my.traty.backup.FullBackupWriter
import ru.my.traty.backup.recordCount
import ru.my.traty.backup.summary
import ru.my.traty.data.DB_SCHEMA_VERSION
import ru.my.traty.data.portable

/** Сообщение под кнопками копии; [isError] — показать цветом ошибки. */
data class BackupNotice(val text: String, val isError: Boolean = false)

/** Копия прочитана и проверена, ждёт подтверждения «заменить всё». */
data class RestoreOffer(val backup: FullBackup, val sections: List<Pair<String, Int>>)

/**
 * Полная резервная копия в настройках: сохранить всё в JSON и восстановить «заменить всё».
 * Файлы выбираются только системным окном выбора (SAF). В лог ничего не пишется: в копии суммы и названия.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val resolver get() = getApplication<Application>().contentResolver

    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<BackupNotice?>(null)
        private set
    var offer by mutableStateOf<RestoreOffer?>(null)
        private set

    fun save(uri: Uri) = launchGuarded(SAVE_FAILED) {
        val backup = withContext(Dispatchers.IO) {
            val b = FullBackupWriter.build(
                c.backups.snapshot(), c.settings.current().portable(), System.currentTimeMillis(), c.appVersion, DB_SCHEMA_VERSION,
            )
            val out = resolver.openOutputStream(uri, "wt") ?: throw FullBackupException(SAVE_FAILED)
            out.bufferedWriter(Charsets.UTF_8).use { FullBackupWriter.write(b, it) }
            b
        }
        notice = BackupNotice("Полная копия сохранена, записей в ней: ${backup.recordCount}. $KEYS_HINT")
    }

    /** Читает и проверяет файл; в базу ничего не пишет, пока пользователь не подтвердит. */
    fun open(uri: Uri) = launchGuarded(READ_FAILED) {
        val backup = withContext(Dispatchers.IO) {
            val bytes = resolver.openInputStream(uri)?.use(FullBackupReader::readLimited)
                ?: throw FullBackupException(READ_FAILED)
            FullBackupReader.decode(bytes, DB_SCHEMA_VERSION)
        }
        notice = null
        offer = RestoreOffer(backup, backup.summary())
    }

    fun cancelRestore() {
        offer = null
    }

    fun confirmRestore() {
        val backup = offer?.backup ?: return
        offer = null
        launchGuarded(RESTORE_FAILED) {
            val r = c.fullBackupImporter.restore(backup)
            // Виджеты и плитка пересчитывают суммы по новой базе; напоминания читают базу сами при следующем запуске
            c.quickEntry.notifyChanged()
            notice = if (r.settingsRestored) {
                BackupNotice("Данные восстановлены из копии. $KEYS_HINT")
            } else {
                BackupNotice("Данные восстановлены, но настройки остались прежними. $KEYS_HINT", isError = true)
            }
        }
    }

    private fun launchGuarded(fallback: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FullBackupException) {
                notice = BackupNotice(e.message ?: fallback, isError = true)
            } catch (e: Exception) {
                notice = BackupNotice(fallback, isError = true)
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val KEYS_HINT = "Ключи ИИ в копию не входят: на новом телефоне введите их заново в «Модели и API-ключи»."
        const val SAVE_FAILED = "Не удалось сохранить копию. Проверьте, что на телефоне есть место, и попробуйте ещё раз."
        const val READ_FAILED = "Не удалось открыть файл копии."
        const val RESTORE_FAILED = "Не удалось восстановить копию. Данные на телефоне не изменились."
    }
}
