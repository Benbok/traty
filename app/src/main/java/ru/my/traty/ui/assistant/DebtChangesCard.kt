package ru.my.traty.ui.assistant

import androidx.compose.runtime.Composable
import ru.my.traty.assistant.DebtAction
import ru.my.traty.assistant.DebtChange

/** Удаление, закрытие или отмена покупок частями: отметить нужные и подтвердить. Без нажатия ничего не меняется. */
@Composable
internal fun DebtChanges(m: ChatMessage, onApply: (List<DebtChange>) -> Unit, onOpenDebts: () -> Unit) {
    val changes = m.debtChanges
    ChangeChecklist(
        key = m.id,
        rows = changes.map { ChangeRow(it.title, it.summary, it.amountKopecks, it.preselected) },
        applied = m.debtsApplied,
        texts = when (changes.first().action) {
            DebtAction.DELETE -> ChangeTexts("Удалить", "Удалено покупок", "Покупки частями")
            DebtAction.CLOSE -> ChangeTexts("Отметить выплаченной", "Отмечено выплаченными", "Покупки частями")
            DebtAction.CANCEL -> ChangeTexts(
                "Отменить покупку", "Отменено покупок", "Покупки частями",
                afterNote = "Если деньги вернули, удалите и внесённые платежи в ленте трат.",
            )
        },
        onApply = { picked -> onApply(picked.map { changes[it] }) },
        onOpen = onOpenDebts,
    )
}
