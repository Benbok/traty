package ru.my.traty.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import java.time.LocalTime

internal class DailyDigestSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as TratyApp).container.settings
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun setEnabled(enabled: Boolean) = save { settings.setDailyDigestEnabled(enabled) }

    fun setTime(time: LocalTime) = save { settings.setDailyDigestTime(time) }

    private fun save(change: suspend () -> Unit) = viewModelScope.launch {
        errorMessage = null
        try {
            change()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            errorMessage = "Не удалось сохранить настройку уведомления"
        }
    }
}
