package ru.my.traty.backup

import androidx.room.withTransaction
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.IncomeSources

data class ImportResult(val imported: Int, val duplicates: Int, val badLines: List<Int>)

/** Записывает разобранный CSV в базу, создавая недостающие категории и пропуская уже существующие траты. */
class CsvImporter(private val db: AppDatabase, private val categories: CategoryRepository) {

    /** @throws CsvFormatException если в файле нет строки заголовка — тогда в базу ничего не пишется. */
    suspend fun import(parsed: CsvParseResult): ImportResult {
        if (!parsed.hasHeader) {
            throw CsvFormatException("файл не похож на резервную копию «Трат» (нет строки «дата;сумма;…»)")
        }
        return insert(parsed)
    }

    private suspend fun insert(parsed: CsvParseResult): ImportResult = db.withTransaction {
        var duplicates = 0
        // Дубли ищем по состоянию базы до импорта: две одинаковые строки внутри одного файла — две траты
        val incomesToInsert = mutableListOf<Income>()
        val toInsert = parsed.rows.mapNotNull { r ->
            if (r.type == CsvRow.INCOME) {
                val near = db.incomes().countNear(
                    r.amountKopecks, r.timestamp - DUPLICATE_WINDOW_MS, r.timestamp + DUPLICATE_WINDOW_MS,
                )
                if (near > 0) {
                    duplicates++
                } else {
                    incomesToInsert += Income(
                        amountKopecks = r.amountKopecks,
                        currency = r.currency,
                        source = IncomeSources.normalize(r.category),
                        note = r.note,
                        timestamp = r.timestamp,
                        origin = IncomeOrigin.MANUAL,
                    )
                }
                return@mapNotNull null
            }
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
        db.incomes().insertAll(incomesToInsert)
        ImportResult(toInsert.size + incomesToInsert.size, duplicates, parsed.badLines)
    }

    private companion object {
        /** Excel при пересохранении обрезает секунды — считаем дублем трату в пределах минуты. */
        const val DUPLICATE_WINDOW_MS = 60_000L
    }
}
