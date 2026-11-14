package ru.my.traty.backup

import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeSources
import java.io.Writer
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CsvRow(
    val timestamp: Long,
    val amountKopecks: Long,
    val currency: String,
    val category: String,
    val subcategory: String?,
    val place: String?,
    val note: String?,
    val rawText: String?,
    /** [EXPENSE] или [INCOME]; для пополнения [category] — вид пополнения. */
    val type: String = EXPENSE,
) {
    companion object {
        const val EXPENSE = "трата"
        const val INCOME = "пополнение"
    }
}

/**
 * [badLines] — номера строк файла (с 1), которые не удалось разобрать.
 * [hasHeader] — найдена строка заголовка «дата;сумма;…»: без неё файл не считается резервной копией.
 */
data class CsvParseResult(val rows: List<CsvRow>, val badLines: List<Int>, val hasHeader: Boolean = true)

/** Файл не похож на резервную копию «Трат»; в базу ничего не записано. */
class CsvFormatException(message: String) : Exception(message)

/**
 * Резервная копия трат в CSV: `;`, поля в кавычках, сумма с запятой, BOM для Excel.
 * Разбор понимает и собственный формат, и файл, пересохранённый в Excel.
 */
object CsvBackup {
    const val HEADER = "дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза;тип"

    /** Траты и пополнения вперемешку по времени (от старых к новым). */
    fun write(rows: List<ExpenseWithCategory>, incomes: List<Income>, zone: ZoneId, out: Writer) {
        out.write("﻿")
        out.write(HEADER + "\n")
        val all = rows.map { it.expense.timestamp to expenseColumns(it, zone) } +
            incomes.map { it.timestamp to incomeColumns(it, zone) }
        all.sortedBy { it.first }.forEach { (_, cols) ->
            val safe = cols.mapIndexed { i, v -> if (i in TEXT_COLUMNS) guardFormula(v) else v }
            out.write(safe.joinToString(";") { v -> "\"" + (v ?: "").replace("\"", "\"\"") + "\"" } + "\n")
        }
    }

    /** Категория, подкатегория, место, заметка, исходная фраза — то, что пишет пользователь или ИИ. */
    private val TEXT_COLUMNS = 3..7
    private val FORMULA_START = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Excel и Google Таблицы выполняют ячейку, начинающуюся с `=`, `+`, `-`, `@`, как формулу.
     * Такой текст пишем с апострофом впереди (стандартная защита от CSV-инъекции); при импорте апостроф снимается.
     */
    internal fun guardFormula(v: String?): String? =
        if (v != null && v.isNotEmpty() && v[0] in FORMULA_START) "'$v" else v

    internal fun unguardFormula(v: String?): String? =
        if (v != null && v.length > 1 && v[0] == '\'' && v[1] in FORMULA_START) v.substring(1) else v

    /** Больше резервная копия не бывает (десятки тысяч записей — единицы мегабайт); защищает от нехватки памяти. */
    const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

    /** Читает файл импорта целиком, но не больше [MAX_IMPORT_BYTES]. */
    fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_IMPORT_BYTES) throw CsvFormatException("файл больше 20 МБ — это не резервная копия «Трат»")
        }
        return out.toByteArray()
    }

    private fun dateText(ts: Long, zone: ZoneId) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone).toString()

    private fun amountText(kopecks: Long) = BigDecimal.valueOf(kopecks).movePointLeft(2).toPlainString().replace('.', ',')

    private fun expenseColumns(row: ExpenseWithCategory, zone: ZoneId): List<String?> {
        val e = row.expense
        return listOf(
            dateText(e.timestamp, zone), amountText(e.amountKopecks), e.currency, row.categoryName, row.subcategoryName,
            e.place, e.note, e.rawText, CsvRow.EXPENSE,
        )
    }

    private fun incomeColumns(i: Income, zone: ZoneId): List<String?> = listOf(
        dateText(i.timestamp, zone), amountText(i.amountKopecks), i.currency, IncomeSources.normalize(i.source), null,
        null, i.note, null, CsvRow.INCOME,
    )

    /**
     * Байты файла → текст. BOM — UTF-8; иначе строгий UTF-8, а если байты в нём невалидны —
     * windows-1251 (так «Сохранить как CSV» пишет русский Excel).
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    fun parse(text: String, zone: ZoneId): CsvParseResult {
        val rows = mutableListOf<CsvRow>()
        val bad = mutableListOf<Int>()
        var hasHeader = false
        var first = true
        for ((line, fields) in records(text.removePrefix("﻿"))) {
            if (fields.all { it.isBlank() }) continue
            val isHeader = fields.first().trim().lowercase() == "дата" && fields.getOrNull(1)?.trim()?.lowercase() == "сумма"
            if (first) { hasHeader = isHeader; first = false }
            if (isHeader) continue
            val row = toRow(fields, zone)
            if (row == null) bad += line else rows += row
        }
        return CsvParseResult(rows, bad, hasHeader)
    }

    private fun toRow(f: List<String>, zone: ZoneId): CsvRow? {
        fun at(i: Int) = f.getOrNull(i)?.trim()?.ifEmpty { null }?.let { if (i in TEXT_COLUMNS) unguardFormula(it) else it }
        val ts = at(0)?.let { parseTimestamp(it, zone) } ?: return null
        val amount = at(1)?.let(::parseKopecks) ?: return null
        val currency = at(2) ?: "RUB"
        // Нет колонки «тип» (старые файлы), пустой или незнакомый тип — трата
        if (at(8)?.lowercase() == CsvRow.INCOME) {
            return CsvRow(ts, amount, currency, IncomeSources.normalize(at(3)), null, null, at(6), null, CsvRow.INCOME)
        }
        val category = at(3) ?: return null
        return CsvRow(ts, amount, currency, category, at(4), at(5), at(6), at(7), CsvRow.EXPENSE)
    }

    private val dateTimeFormats = listOf("dd.MM.yyyy H:mm:ss", "dd.MM.yyyy H:mm", "yyyy-MM-dd H:mm:ss", "yyyy-MM-dd H:mm")
        .map { DateTimeFormatter.ofPattern(it) }
    private val dateFormats = listOf("dd.MM.yyyy", "yyyy-MM-dd").map { DateTimeFormatter.ofPattern(it) }

    private fun parseTimestamp(s: String, zone: ZoneId): Long? {
        val dt = runCatching { LocalDateTime.parse(s) }.getOrNull()
            ?: dateTimeFormats.firstNotNullOfOrNull { runCatching { LocalDateTime.parse(s, it) }.getOrNull() }
            // Дата без времени — полдень, как у трат за прошлые дни
            ?: dateFormats.firstNotNullOfOrNull { runCatching { LocalDate.parse(s, it).atTime(LocalTime.NOON) }.getOrNull() }
            ?: return null
        return dt.atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseKopecks(s: String): Long? {
        val clean = s.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }.replace(',', '.')
        val v = clean.toBigDecimalOrNull() ?: return null
        if (v.signum() <= 0) return null
        return runCatching { v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    /** Записи CSV с номером физической строки, на которой запись начинается. Кавычки могут содержать `;` и переводы строк. */
    private fun records(text: String): List<Pair<Int, List<String>>> {
        val out = mutableListOf<Pair<Int, List<String>>>()
        val fields = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordStart = 1
        var i = 0
        fun endRecord() {
            fields += cur.toString(); cur.clear()
            out += recordStart to fields.toList(); fields.clear()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && text.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                inQuotes -> { if (ch == '\n') line++; if (ch != '\r') cur.append(ch) }
                ch == ';' -> { fields += cur.toString(); cur.clear() }
                ch == '\r' -> Unit
                ch == '\n' -> { endRecord(); line++; recordStart = line }
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return out
    }
}
