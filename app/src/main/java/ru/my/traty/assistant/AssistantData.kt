package ru.my.traty.assistant

/**
 * Результат расчёта.
 * [facts] уходят в ИИ, чтобы он сформулировал ответ: только посчитанные приложением цифры.
 * [headline] и [caption] — крупная цифра и подпись под ответом; [expenseIds] — для «Показать траты».
 * [direct] — готовый ответ без второго обращения к ИИ (когда вопрос не про деньги).
 */
data class AssistantData(
    val facts: String,
    val headline: String? = null,
    val caption: String? = null,
    val expenseIds: List<Long> = emptyList(),
    val direct: String? = null,
    /** Предложения в «План трат»: применяются только по кнопке пользователя. */
    val planProposals: List<PlanProposal> = emptyList(),
    /** Месяц плана (ГГГГММ), в который добавятся предложения. */
    val planMonth: Int? = null,
    /** Отмена, пауза, возобновление и правка подписок: тоже только по кнопке. */
    val subscriptionChanges: List<SubscriptionChange> = emptyList(),
    /** Удаление, закрытие и отмена покупок частями: только по кнопке. */
    val debtChanges: List<DebtChange> = emptyList(),
    /** Удаление и перенос трат в другую категорию: только по кнопке. */
    val expenseChanges: List<ExpenseChange> = emptyList(),
    val itemSuggestions: List<ItemSuggestion> = emptyList(),
    val candidateNames: List<String> = emptyList(),
    val calculateItems: ((List<String>) -> ItemSuggestion)? = null,
)
