package ru.my.traty.ui.debt

import ru.my.traty.data.debt.DeletionImpact
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

/** Текст диалога «Удалить покупку?»: прямо говорит, какие траты исчезнут, а какие останутся. */
internal object DebtDeleteText {
    fun of(impact: DeletionImpact): String = buildString {
        append("Покупка будет удалена. ")
        if (impact.removedCount > 0) {
            val n = impact.removedCount
            append("Вместе с ней удалятся $n ${pluralRu(n.toLong(), "платёж", "платежа", "платежей")} ")
            append("на ${formatRub(impact.removedKopecks)}, внесённые кнопкой «Внести платёж»: ")
            append("они пропадут и из списка трат, и из статистики. ")
        } else {
            append("Внесённых через приложение платежей у неё нет. ")
        }
        if (impact.keptCount > 0) {
            val k = impact.keptCount
            append("В ленте останется $k ${pluralRu(k.toLong(), "трата", "траты", "трат")}, записанных вами.")
        } else {
            append("Трат, записанных вами, у этой покупки нет.")
        }
    }
}
