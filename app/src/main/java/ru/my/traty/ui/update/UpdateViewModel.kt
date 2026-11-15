package ru.my.traty.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.update.AvailableUpdate
import ru.my.traty.data.update.UpdateCheckResult

/** Обновления: плашка «Вышла новая версия» на главном экране и проверка в настройках. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    /** Новая версия, о которой надо сказать, или null. */
    val available: StateFlow<AvailableUpdate?> = c.settings.flow
        .map { AvailableUpdate.from(it, c.updates.installedVersion) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Включена ли проверка (для переключателя в настройках). */
    val enabled: StateFlow<Boolean> = c.settings.flow
        .map { it.updateCheckEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val installedVersion: String get() = c.updates.installedVersion

    /** Идёт проверка по кнопке. */
    val checking = MutableStateFlow(false)

    /** Итог последней проверки по кнопке; null, пока не проверяли. */
    val result = MutableStateFlow<UpdateCheckResult?>(null)

    init {
        // Тихая проверка при открытии приложения; сама решает, пора ли (раз в полдня) и включена ли
        viewModelScope.launch {
            try {
                c.updates.checkIfDue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Проверка обновлений не должна ничего ломать
            }
        }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { c.settings.setUpdateCheckEnabled(value) }
    }

    /** «Позже»: плашку про эту версию больше не показываем, про следующую покажем. */
    fun dismiss(version: String) {
        viewModelScope.launch { c.settings.dismissUpdate(version) }
    }

    fun checkNow() {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            result.value = null
            try {
                result.value = c.updates.checkNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.value = UpdateCheckResult.Failed("Не получилось проверить")
            } finally {
                checking.value = false
            }
        }
    }
}
