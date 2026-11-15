package ru.my.traty.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import ru.my.traty.data.Recurring
import ru.my.traty.data.recurring.RecurringRepository

/**
 * Предложения «добавить в Подписки» после сохранения траты. В «Подписки» ничего не попадает само:
 * платёж заводится только по кнопке «Добавить» ([accept]), «Не надо» ([decline]) запоминает отказ,
 * а закрытый без ответа диалог ([skip]) просто убирает предложение до следующей такой траты.
 * Живёт в [QuickEntry] (один на приложение), поэтому предложение из фонового разбора дождётся экрана.
 */
class RecurringOffers(private val repo: RecurringRepository) {
    private val _pending = MutableStateFlow<List<Recurring>>(emptyList())
    val pending: StateFlow<List<Recurring>> = _pending.asStateFlow()

    /** Добавляет предложения; одно и то же (по ключу платежа) дважды не показывается. */
    fun add(offers: List<Recurring>) {
        if (offers.isEmpty()) return
        _pending.update { cur -> cur + offers.filter { o -> cur.none { same(it, o) } }.distinctBy { it.detectKey ?: it.title } }
    }

    /** @return id заведённого платежа или null, если такой уже есть. */
    /** Предложение убирается только после записи в базу: при ошибке вопрос остаётся на экране. */
    suspend fun accept(offer: Recurring): Long? =
        withContext(Dispatchers.IO) { repo.acceptOffer(offer) }.also { remove(offer) }

    suspend fun decline(offer: Recurring) {
        withContext(Dispatchers.IO) { repo.declineOffer(offer) }
        remove(offer)
    }

    fun skip(offer: Recurring) = remove(offer)

    private fun remove(offer: Recurring) = _pending.update { list -> list.filterNot { same(it, offer) } }

    private fun same(a: Recurring, b: Recurring) =
        if (a.detectKey != null || b.detectKey != null) a.detectKey == b.detectKey else a.title == b.title
}
