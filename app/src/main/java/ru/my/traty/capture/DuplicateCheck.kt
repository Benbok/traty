package ru.my.traty.capture

import kotlinx.serialization.Serializable
import ru.my.traty.ai.EntryType
import ru.my.traty.data.Places
import ru.my.traty.data.sameName
import ru.my.traty.data.search.SearchText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Уже записанная трата, похожая на новую строку: показывается в окне проверки с вопросом «повтор или новая?». */
@Serializable
data class DuplicateHint(
    val expenseId: Long,
    val amountKopecks: Long,
    val timestamp: Long,
    /** Как назвать трату: магазин, заметка или категория. */
    val label: String,
    /** Записана меньше [DuplicateCheck.RECENT_MS] назад — почти наверняка та же покупка. */
    val recent: Boolean,
    /** Похожая запись — пополнение, а не трата: окно проверки говорит «пополнение». */
    val income: Boolean = false,
)

/** Трата из базы в том виде, который нужен для сравнения. */
data class ExistingExpense(
    val id: Long,
    val amountKopecks: Long,
    val timestamp: Long,
    val place: String?,
    val note: String?,
    val categoryName: String,
    /** Пополнение: в [categoryName] лежит источник, магазина нет. */
    val income: Boolean = false,
)

/**
 * Повторная диктовка той же покупки («сегодня Магнит 250» дважды). Трата считается похожей, если совпадают
 * сумма и день, а также магазин; без магазина — заметка; без магазина и заметки — категория.
 * Если магазин назван только в одной из записей, это та же покупка, когда совпала категория и прошло не больше
 * [RECENT_MS]: «Магнит 250» и тут же «250 на продукты». Пополнения сверяются так же по источнику и заметке.
 * Одна трата из базы подходит только одной строке фразы.
 */
object DuplicateCheck {
    const val RECENT_MS = 30 * 60_000L

    fun find(rows: List<EntryRow>, existing: List<ExistingExpense>, zone: ZoneId, now: Long): List<DuplicateHint?> {
        val used = mutableSetOf<Long>()
        return rows.map { row ->
            val match = matchFor(row, existing.filter { it.id !in used }, zone, now) ?: return@map null
            used += match.id
            DuplicateHint(
                expenseId = match.id,
                amountKopecks = match.amountKopecks,
                timestamp = match.timestamp,
                label = Places.normalize(match.place) ?: match.note?.trim()?.takeIf { it.isNotEmpty() } ?: match.categoryName,
                recent = now - match.timestamp in 0..RECENT_MS,
                income = match.income,
            )
        }
    }

    /** Самая поздняя похожая трата или null. */
    private fun matchFor(row: EntryRow, existing: List<ExistingExpense>, zone: ZoneId, now: Long): ExistingExpense? {
        val rowIncome = row.type == EntryType.INCOME
        if (row.type != EntryType.EXPENSE && !rowIncome) return null
        val kopecks = row.kopecks() ?: return null
        val day = runCatching { LocalDate.parse(row.dateText.trim()) }.getOrNull() ?: return null
        return existing
            .filter { it.income == rowIncome && it.amountKopecks == kopecks && dayOf(it.timestamp, zone) == day && sameThing(row, it, now) }
            .maxByOrNull { it.timestamp }
    }

    private fun sameThing(row: EntryRow, e: ExistingExpense, now: Long): Boolean {
        if (e.income) {
            if (!sameName(row.source, e.categoryName)) return false
            val rowNote = noteKey(row.note)
            val oldNote = noteKey(e.note)
            return rowNote.isEmpty() || oldNote.isEmpty() || rowNote == oldNote
        }
        val rowPlace = Places.key(row.place)
        val oldPlace = Places.key(e.place)
        if (rowPlace.isNotEmpty() && oldPlace.isNotEmpty()) return rowPlace == oldPlace || SearchText.sameBrand(row.place, listOf(e.place))
        if (rowPlace.isNotEmpty() || oldPlace.isNotEmpty()) return now - e.timestamp in 0..RECENT_MS && sameName(row.category, e.categoryName)
        val rowNote = noteKey(row.note)
        val oldNote = noteKey(e.note)
        if (rowNote.isNotEmpty() || oldNote.isNotEmpty()) return rowNote == oldNote
        return sameName(row.category, e.categoryName)
    }

    private fun noteKey(s: String?): String =
        s.orEmpty().lowercase().replace('ё', 'е').replace(Regex("""[\s.,;:!?«»"]+"""), " ").trim()

    private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
