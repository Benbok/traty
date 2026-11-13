package ru.my.traty.backup

import androidx.room.withTransaction
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource

data class ImportResult(val imported: Int, val duplicates: Int, val badLines: List<Int>)

/** Записывает разобранный CSV в базу, создавая недостающие категории и пропуская уже существующие траты. */
class CsvImporter(private val db: AppDatabase, private val categories: CategoryRepository) {

    suspend fun import(parsed: CsvParseResult): ImportResult = db.withTransaction {
        var duplicates = 0
        // Дубли ищем по состоянию базы до импорта: две одинаковые строки внутри одного файла — две траты
        val toInsert = parsed.rows.mapNotNull { r ->
            val ids = categories.resolveForSave(r.category, null, r.subcategory)
            val near = db.expenses().countNear(
                ids.categoryId, r.amountKopecks, r.timestamp - DUPLICATE_WINDOW_MS, r.timestamp + DUPLICATE_WINDOW_MS,
            )
            if (near > 0) {
                duplicates++
                null
            } else {
                Expense(
                    amountKopecks = r.amountKopecks,
                    currency = r.currency,
                    categoryId = ids.categoryId,
                    subcategoryId = ids.subcategoryId,
                    place = r.place,
                    note = r.note,
                    timestamp = r.timestamp,
                    rawText = r.rawText,
                    source = ExpenseSource.IMPORT,
                )
            }
        }
        db.expenses().insertAll(toInsert)
        ImportResult(toInsert.size, duplicates, parsed.badLines)
    }

    private companion object {
        /** Excel при пересохранении обрезает секунды — считаем дублем трату в пределах минуты. */
        const val DUPLICATE_WINDOW_MS = 60_000L
    }
}
