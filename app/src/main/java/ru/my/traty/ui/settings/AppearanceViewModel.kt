package ru.my.traty.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ThemeMode

/** Раздел «Оформление» в настройках: тема и цвета приложения. */
class AppearanceViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = (app as TratyApp).container.settings

    val settings: StateFlow<AppSettings> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
}
