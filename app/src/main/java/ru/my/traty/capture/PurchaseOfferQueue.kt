package ru.my.traty.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import ru.my.traty.data.debt.DebtRepository
import ru.my.traty.data.debt.DebtStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Вопросы «Доплата за холодильник?» и «Покупка частями?» после сохранения траты.
 * Трата уже сохранена; покупка заводится или трата привязывается только по кнопке «Да».
 * Закрытый без ответа вопрос просто убирается: трата остаётся обычной.
 * Живёт в [QuickEntry] (один на приложение), поэтому вопрос из фонового разбора дождётся экрана.
 */
class PurchaseOfferQueue(private val repo: DebtRepository, private val zone: ZoneId) {
    private val _pending = MutableStateFlow<List<PurchaseOffer>>(emptyList())
    val pending: StateFlow<List<PurchaseOffer>> = _pending.asStateFlow()

    fun add(offers: List<PurchaseOffer>) {
        if (offers.isEmpty()) return
        _pending.update { cur -> cur + offers.filter { o -> cur.none { it.key == o.key } }.distinctBy { it.key } }
    }

    /** Остаток по покупке до этой доплаты: «будет оплачено 25 000 из 60 000 ₽». */
    suspend fun statusOf(offer: PurchaseOffer): DebtStatus? {
        val debt = offer.match ?: return null
        return withContext(Dispatchers.IO) { repo.status(debt.id, zone, LocalDate.now(zone)) }
    }

    /** «Да, доплата». @return false, если покупка уже закрыта или трата привязана. */
    suspend fun attach(offer: PurchaseOffer): Boolean {
        val debt = offer.match ?: return false
        val expenseId = offer.expenseId ?: return false
        return withContext(Dispatchers.IO) { repo.attachExpense(debt.id, expenseId, zone, LocalDate.now(zone)) }
            .also { remove(offer) }
    }

    /** «Сохранить покупку». @return false, если условий не хватает (вопрос остаётся на экране). */
    suspend fun create(offer: PurchaseOffer, form: PurchaseForm): Boolean {
        val debt = form.toDebt(offer) ?: return false
        withContext(Dispatchers.IO) { repo.startFromExpense(debt, offer.expenseId, zone, LocalDate.now(zone)) }
        remove(offer)
        return true
    }

    /** «Нет» или закрыли: трата остаётся обычной. */
    fun skip(offer: PurchaseOffer) = remove(offer)

    private fun remove(offer: PurchaseOffer) = _pending.update { list -> list.filterNot { it.key == offer.key } }
}
