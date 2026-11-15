package ru.my.traty.ui.capture

import android.app.Application
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
import ru.my.traty.data.Recurring

/** Вопрос «Добавить в Подписки?» после сохранения траты: на главной и в панели быстрой записи. */
class RecurringOfferViewModel(app: Application) : AndroidViewModel(app) {
    private val offers = (app as TratyApp).container.quickEntry.recurringOffers

    /** Первое предложение, которое ждёт ответа; null — спрашивать нечего. */
    val offer: StateFlow<Recurring?> = offers.pending.map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, offers.pending.value.firstOrNull())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var busy = false

    /** «Добавить»: платёж появляется в «Подписках». Двойной тап не заводит второй платёж. */
    fun accept(offer: Recurring) = answer("Не удалось добавить в подписки") { offers.accept(offer) }

    /** «Не надо»: трата остаётся, в «Подписки» ничего не добавляется, тот же платёж больше не предлагается. */
    fun decline(offer: Recurring) = answer("Не удалось сохранить ответ") { offers.decline(offer) }

    /** Диалог закрыли без ответа: спросим при следующей такой трате. */
    fun skip(offer: Recurring) {
        _error.value = null
        offers.skip(offer)
    }

    private fun answer(errorText: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "$errorText. Попробуйте ещё раз."
            } finally {
                busy = false
            }
        }
    }
}
