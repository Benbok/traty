package ru.my.traty.ui.assistant

import androidx.compose.runtime.Composable
import ru.my.traty.assistant.SubAction
import ru.my.traty.assistant.SubscriptionChange

/** Изменения подписок: отметить нужные и подтвердить. Без нажатия ничего не меняется. */
@Composable
internal fun SubscriptionChanges(m: ChatMessage, onApply: (List<SubscriptionChange>) -> Unit, onOpenRecurring: () -> Unit) {
    val changes = m.subscriptionChanges
    val action = changes.first().action
    ChangeChecklist(
        key = m.id,
        rows = changes.map { ChangeRow(it.title, it.summary, it.newAmountKopecks ?: it.amountKopecks) },
        applied = m.subscriptionsApplied,
        texts = when (action) {
            SubAction.CANCEL -> ChangeTexts(
                "Отменить подписку", "Убрано из подписок", "Открыть подписки",
                afterNote = "Отпишитесь и в самом сервисе, иначе списания продолжатся.",
            )
            SubAction.PAUSE -> ChangeTexts("Поставить на паузу", "Поставлено на паузу", "Открыть подписки")
            SubAction.RESUME -> ChangeTexts("Возобновить", "Возобновлено", "Открыть подписки")
            SubAction.EDIT -> ChangeTexts("Сохранить изменения", "Изменено", "Открыть подписки")
        },
        onApply = { picked -> onApply(picked.map { changes[it] }) },
        onOpen = onOpenRecurring,
    )
}
