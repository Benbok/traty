package ru.my.traty.ui.recurring

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
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringWithNames
import ru.my.traty.data.recurring.RecurringCandidate
import ru.my.traty.data.recurring.RecurringCharge
import ru.my.traty.data.recurring.RecurringRepository
import ru.my.traty.data.recurring.RecurringSchedule
import ru.my.traty.data.recurring.RecurringSummary
import ru.my.traty.data.recurring.RecurringTotals
import ru.my.traty.data.selectable
import java.time.LocalDate

/** Экран «Подписки»: заведённые платежи, итоги и предложения из истории трат. */
class RecurringViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val repo = RecurringRepository(c.db)

    val items: StateFlow<List<RecurringWithNames>> =
        c.db.recurring().observeVisible().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totals: StateFlow<RecurringTotals> =
        items.map { list -> RecurringSummary.totals(list.map { it.recurring }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RecurringTotals(0, 0, 0))

    /** Списания на месяц вперёд. */
    val upcoming: StateFlow<List<RecurringCharge>> =
        items.map { list -> RecurringSchedule.upcoming(list.map { it.recurring }, LocalDate.now()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeActiveWithSubs().map { it.selectable() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Предложения пересчитываются при любом изменении трат или списка платежей. */
    val suggestions: StateFlow<List<RecurringCandidate>> =
        combine(
            c.db.expenses().observeAllWithCategory(),
            c.db.recurring().observeAll(),
            c.db.categories().observeActiveWithSubs(),
            c.debts.observePayments(),
        ) { expenses, recurring, cats, debtPayments ->
            RecurringRepository.suggest(expenses, recurring, cats, debtExpenseIds = debtPayments.map { it.expenseId }.toSet())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun accept(candidate: RecurringCandidate) = viewModelScope.launch { repo.accept(candidate) }

    fun dismiss(candidate: RecurringCandidate) = viewModelScope.launch { repo.dismiss(candidate) }

    fun save(item: Recurring) = viewModelScope.launch { repo.save(item) }

    fun setPaused(item: Recurring, paused: Boolean) = viewModelScope.launch { repo.setPaused(item, paused) }

    /** «Списалось»: трата создаётся, платёж переезжает на следующий период. */
    fun markPaid(item: Recurring, amountKopecks: Long? = null) =
        viewModelScope.launch { repo.markPaid(item.id, amountKopecks = amountKopecks) }

    fun remove(item: Recurring) = viewModelScope.launch { repo.remove(item) }
}
