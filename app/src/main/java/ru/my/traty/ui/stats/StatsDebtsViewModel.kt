package ru.my.traty.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.my.traty.TratyApp
import ru.my.traty.data.debt.DebtOverview
import ru.my.traty.data.debt.DebtOverviews
import ru.my.traty.data.debt.DebtRepository
import ru.my.traty.data.recurring.RecurringRepository
import ru.my.traty.data.sameName
import java.time.LocalDate
import java.time.ZoneId

/** Значок категории покупки для строки в карточке долгов. */
data class DebtCategoryLabel(val emoji: String, val name: String)

/**
 * Карточка «Долги по взносам» в «Статистике»: сводка открытых рассрочек, кредитов и покупок частями
 * на сегодня. От выбранного периода не зависит: долг — это то, что осталось заплатить сейчас.
 */
class StatsDebtsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone: ZoneId = ZoneId.systemDefault()

    /** null — данные ещё не загрузились (карточку не показываем, чтобы она не мигала «долгов нет»). */
    val overview: StateFlow<DebtOverview?> = combine(
        c.debts.observeAll(),
        c.debts.observePayments(),
        RecurringRepository(c.db).observeAll(),
        c.categories.observeAllWithSubs(),
    ) { debts, payments, recurring, cats ->
        val loanCategory = cats.firstOrNull { sameName(it.category.name, DebtRepository.INTEREST_CATEGORY) }?.category?.id
        DebtOverviews.of(debts, payments, recurring, loanCategory, LocalDate.now(), zone)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<Map<Long, DebtCategoryLabel>> = c.categories.observeAllWithSubs()
        .map { cats -> cats.associate { it.category.id to DebtCategoryLabel(it.category.emoji, it.category.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
