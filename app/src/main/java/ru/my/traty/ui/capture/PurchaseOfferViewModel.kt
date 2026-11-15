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
import ru.my.traty.capture.PurchaseForm
import ru.my.traty.capture.PurchaseOffer
import ru.my.traty.data.debt.DebtStatus

/** Вопросы «Доплата за покупку?» и «Покупка частями?» после сохранения траты: на главной и в панели записи. */
class PurchaseOfferViewModel(app: Application) : AndroidViewModel(app) {
    private val offers = (app as TratyApp).container.quickEntry.purchaseOffers

    /** Первый вопрос, который ждёт ответа; null — спрашивать нечего. */
    val offer: StateFlow<PurchaseOffer?> = offers.pending.map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, offers.pending.value.firstOrNull())

    private val _status = MutableStateFlow<DebtStatus?>(null)

    /** Остаток покупки до доплаты (для вопроса «Доплата за…?»). */
    val status: StateFlow<DebtStatus?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var busy = false

    init {
        viewModelScope.launch {
            offer.collect { o ->
                _status.value = null
                if (o?.match != null) {
                    _status.value = try {
                        offers.statusOf(o)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    fun attach(offer: PurchaseOffer) = answer("Не удалось привязать доплату") {
        if (!offers.attach(offer)) _error.value = "Эта покупка уже оплачена или закрыта. Трата сохранена как обычная."
    }

    fun create(offer: PurchaseOffer, form: PurchaseForm) = answer("Не удалось сохранить покупку") {
        if (!offers.create(offer, form)) _error.value = "Не хватает условий: укажите цену и число платежей."
    }

    /** «Нет» или закрыли: трата остаётся обычной. */
    fun skip(offer: PurchaseOffer) {
        _error.value = null
        offers.skip(offer)
    }

    private fun answer(errorText: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        _error.value = null
        viewModelScope.launch {
            try {
                block()
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
