package ru.my.traty.ui.apartments

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.data.apartments.ApartmentSnapshot

/** Общая загрузка и обработка ошибок; каждый экран имеет собственный экземпляр ViewModel. */
abstract class HousingViewModel(app: Application) : AndroidViewModel(app) {
    protected val repo = (app as TratyApp).container.apartments
    private val _snapshot = MutableStateFlow(ApartmentSnapshot())
    val snapshot = _snapshot.asStateFlow()
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            try { repo.observe().collect { _snapshot.value = it } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Не удалось загрузить квартиры. Откройте раздел заново." }
        }
    }
    fun setApartmentArchived(id: Long, archived: Boolean, onSaved: () -> Unit) =
        change(onSaved, archiveError(archived)) { repo.setApartmentArchived(id, archived) }

    fun setMeterArchived(id: Long, archived: Boolean, onSaved: () -> Unit) =
        change(onSaved, archiveError(archived)) { repo.setMeterArchived(id, archived) }

    private fun archiveError(archived: Boolean): String =
        if (archived) "Не удалось перенести в архив. Попробуйте ещё раз."
        else "Не удалось вернуть из архива. Попробуйте ещё раз."

    protected fun change(
        onSaved: () -> Unit = {},
        failureMessage: String = "Не удалось сохранить. Проверьте, нет ли показания за эту дату.",
        block: suspend () -> Unit,
    ) {
        if (busy) return
        busy = true; message = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block(); _snapshot.value = repo.snapshot() }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { message = e.message ?: "Проверьте введённые данные" }
            catch (e: Exception) { message = failureMessage }
            finally { busy = false }
        }
    }
}
