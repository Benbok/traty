package ru.my.traty.capture

import ru.my.traty.ai.EntryType
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Rule
import ru.my.traty.data.sameName

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
            val text = fold(listOfNotNull(e.place, e.note).joinToString(" "))
            val rule = rules.firstOrNull { mentions(text, fold(it.pattern.trim())) }
                ?: return@map RuledEntry(e, null)
            // Правило на архивную/удалённую категорию не считается совпадением: иначе трата автосохранилась бы в категорию от ИИ
            val cat = categories.firstOrNull { it.category.id == rule.categoryId }
                ?: return@map RuledEntry(e, null)
            val ruleSub = rule.subcategoryId?.let { sid -> categories.flatMap { it.subs }.firstOrNull { it.id == sid }?.name }
            // Подкатегория от ИИ остаётся, только если она из категории правила: иначе при сохранении
            // в «Продуктах» молча создалась бы «Бытовая химия» из «Дома и быта»
            val aiSub = e.subcategory?.takeIf { s -> sameName(e.category, cat.category.name) || cat.subs.any { sameName(it.name, s) } }
            RuledEntry(e.copy(category = cat.category.name, subcategory = ruleSub ?: aiSub), rule)
        }

    /** «ё» и «е» не различаем: правило «пятёрочка» срабатывает и на «Пятерочка». */
    private fun fold(s: String) = s.lowercase().replace('ё', 'е')

    /** Правило ищется с начала слова: «лента» есть в «Лента» и «лента у дома», но не в «изолента». */
    private fun mentions(text: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        var i = text.indexOf(pattern)
        while (i >= 0) {
            if (i == 0 || !text[i - 1].isLetterOrDigit()) return true
            i = text.indexOf(pattern, i + 1)
        }
        return false
    }
}
