package ru.my.traty.ui.whatsnew

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.whatsnew.WhatsNewEntry

/** Окно «Что нового»: после обновления показывает новинки один раз. */
class WhatsNewViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container

    private val _entries = MutableStateFlow<List<WhatsNewEntry>>(emptyList())

    /** Записи для показа; пусто — окно закрыто. */
    val entries: StateFlow<List<WhatsNewEntry>> = _entries.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _entries.value = c.whatsNew.pending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Новинки не важнее самого приложения: при ошибке просто не показываем
                Log.w(TAG, "whats new check failed: ${e.message}")
            }
        }
    }

    fun dismiss() {
        _entries.value = emptyList()
        viewModelScope.launch {
            try {
                c.whatsNew.markSeen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "whats new mark failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "WhatsNew"
    }
}
