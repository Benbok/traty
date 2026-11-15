package ru.my.traty.ui.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.onboarding.OnboardingPolicy
import ru.my.traty.data.period.MIN_PERIOD_START_DAY

/** Знакомство с приложением: показать при первом запуске, по кнопке из Настроек, запомнить, что показано. */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    /** null — ещё проверяем; true — показываем; false — скрыто. */
    private val _visible = MutableStateFlow<Boolean?>(null)
    val visible: StateFlow<Boolean?> = _visible.asStateFlow()

    val periodStartDay: StateFlow<Int> = c.settings.flow.map { it.periodStartDay }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MIN_PERIOD_START_DAY)

    init {
        viewModelScope.launch {
            _visible.value = try {
                c.onboarding.shouldShowOnLaunch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Знакомство не важнее самого приложения: при ошибке просто не показываем
                Log.w(TAG, "onboarding check failed: ${e.message}")
                false
            }
        }
    }

    /** Открыть знакомство снова (Настройки → «Как пользоваться»). */
    fun show() {
        _visible.value = true
    }

    /** Закрыть и запомнить. Флаг ставится при любом выходе, в том числе «Пропустить». */
    fun finish() {
        _visible.value = false
        viewModelScope.launch {
            try {
                c.onboarding.markSeen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "onboarding mark failed: ${e.message}")
            }
        }
    }

    fun setPeriodStart(fromSalary: Boolean, salaryDay: Int) {
        viewModelScope.launch {
            try {
                c.settings.setPeriodStartDay(OnboardingPolicy.periodStartDay(fromSalary, salaryDay))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "period start save failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "Onboarding"
    }
}
