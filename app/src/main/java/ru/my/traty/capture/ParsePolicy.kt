package ru.my.traty.capture

import ru.my.traty.ai.EntryType

enum class EntryDecision { AUTO_SAVE, CONFIRM }

object ParsePolicy {
    /**
     * Автосохранение — только если все операции фразы траты с суммой и у каждой локально сработало правило.
     * Любое пополнение, неизвестный тип или трата без правила — подтверждение всей фразы.
     */
    fun decide(entries: List<RuledEntry>): EntryDecision =
        if (entries.isNotEmpty() && entries.all {
                it.entry.entryType == EntryType.EXPENSE && it.entry.amount > 0 && it.ruleMatched
            }
        ) EntryDecision.AUTO_SAVE else EntryDecision.CONFIRM
}
