package ru.my.traty.capture

import ru.my.traty.ai.EntryType
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Rule

/** Операция после применения правил; [matchedRule] — какое правило сработало (null — никакое). */
data class RuledEntry(val entry: ParsedEntry, val matchedRule: Rule?) {
    val ruleMatched: Boolean get() = matchedRule != null
}

object RuleMatcher {
    /**
     * Правила «место/слово → категория» применяются только к тратам и ищутся только в `place` и `note`,
     * без названия категории, выбранной ИИ: иначе правило «кафе» совпадало бы с любой тратой в категории «Кафе».
     * Правило побеждает ответ ИИ. Список [rules] должен идти в порядке приоритета.
     */
    fun apply(entries: List<ParsedEntry>, rules: List<Rule>, categories: List<CategoryWithSubs>): List<RuledEntry> =
        entries.map { e ->
            if (e.entryType != EntryType.EXPENSE) return@map RuledEntry(e, null)
            // «ё» и «е» не различаем: правило «пятёрочка» срабатывает и на «Пятерочка»
            val text = listOfNotNull(e.place, e.note).joinToString(" ").lowercase().replace('ё', 'е')
            val rule = rules.firstOrNull { it.pattern.isNotBlank() && text.contains(it.pattern.lowercase().replace('ё', 'е')) }
                ?: return@map RuledEntry(e, null)
            // Правило на архивную/удалённую категорию не считается совпадением: иначе трата автосохранилась бы в категорию от ИИ
            val catName = categories.firstOrNull { it.category.id == rule.categoryId }?.category?.name
                ?: return@map RuledEntry(e, null)
            val subName = rule.subcategoryId?.let { sid -> categories.flatMap { it.subs }.firstOrNull { it.id == sid }?.name }
            RuledEntry(e.copy(category = catName, subcategory = subName ?: e.subcategory), rule)
        }
}
