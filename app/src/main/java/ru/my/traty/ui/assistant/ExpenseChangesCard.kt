package ru.my.traty.ui.assistant

import androidx.compose.runtime.Composable
import ru.my.traty.assistant.ExpenseAction
import ru.my.traty.assistant.ExpenseChange

/** Удаление трат или перенос в другую категорию: отметить нужные и подтвердить. Без нажатия ничего не меняется. */
@Composable
internal fun ExpenseChanges(m: ChatMessage, onApply: (List<ExpenseChange>) -> Unit, onUndo: () -> Unit) {
    val changes = m.expenseChanges
    ChangeChecklist(
        key = m.id,
        rows = changes.map { ChangeRow(it.title, it.summary, it.amountKopecks, it.preselected) },
        applied = m.expensesApplied,
        texts = when (changes.first().action) {
            ExpenseAction.DELETE -> ChangeTexts("Удалить", "Удалено трат", open = "", undo = "Вернуть")
            ExpenseAction.MOVE -> ChangeTexts("Перенести", "Перенесено трат", open = "", undo = "Вернуть")
        },
        onApply = { picked -> onApply(picked.map { changes[it] }) },
        onOpen = {},
        undone = m.expensesUndone,
        onUndo = onUndo,
    )
}
