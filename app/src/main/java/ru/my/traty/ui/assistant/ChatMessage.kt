package ru.my.traty.ui.assistant

import ru.my.traty.assistant.DebtChange
import ru.my.traty.assistant.ExpenseChange
import ru.my.traty.assistant.ItemSuggestion
import ru.my.traty.assistant.PlanProposal
import ru.my.traty.assistant.SubscriptionChange
import ru.my.traty.data.RememberedProduct

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    /** Крупная цифра под ответом. */
    val headline: String? = null,
    val caption: String? = null,
    /** Траты, на которых построен ответ, — для «Показать траты». */
    val expenseIds: List<Long> = emptyList(),
    /** Что именно ушло в ИИ на втором шаге — показывается по кнопке «Что отправлено». */
    val sentFacts: String? = null,
    val error: Boolean = false,
    /** Предложенные строки «Плана трат» — добавляются по кнопке. */
    val planProposals: List<PlanProposal> = emptyList(),
    val planMonth: Int? = null,
    /** Сколько строк уже добавлено в план; null — ещё не добавляли. */
    val planApplied: Int? = null,
    /** Предложенные изменения подписок — выполняются по кнопке. */
    val subscriptionChanges: List<SubscriptionChange> = emptyList(),
    /** Сколько изменений подписок уже выполнено; null — ещё нет. */
    val subscriptionsApplied: Int? = null,
    /** Предложенные изменения покупок частями — выполняются по кнопке. */
    val debtChanges: List<DebtChange> = emptyList(),
    /** Сколько изменений покупок уже выполнено; null — ещё нет. */
    val debtsApplied: Int? = null,
    /** Предложенные удаления и переносы трат — выполняются по кнопке и возвращаются кнопкой «Вернуть». */
    val expenseChanges: List<ExpenseChange> = emptyList(),
    val expensesApplied: Int? = null,
    val expensesUndone: Boolean = false,
    val itemSuggestionTerm: String? = null,
    val productToRemember: RememberedProduct? = null,
    val itemSuggestions: List<ItemSuggestion> = emptyList(),
    val combinedItemSuggestions: Map<Set<Int>, ItemSuggestion> = emptyMap(),
)
