package ru.my.traty.backup

import ru.my.traty.data.ExpenseWithCategory
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
)

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
    const val HEADER = "дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза"

    fun write(rows: List<ExpenseWithCategory>, zone: ZoneId, out: Writer) {
        out.write("﻿")
        out.write(HEADER + "\n")
        rows.forEach { row ->
            val e = row.expense
            val cols = listOf(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(e.timestamp), zone).toString(),
                BigDecimal.valueOf(e.amountKopecks).movePointLeft(2).toPlainString().replace('.', ','),
                e.currency, row.categoryName, row.subcategoryName, e.place, e.note, e.rawText,
            )
            out.write(cols.joinToString(";") { v -> "\"" + (v ?: "").replace("\"", "\"\"") + "\"" } + "\n")
        }
    }

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
        fun at(i: Int) = f.getOrNull(i)?.trim()?.ifEmpty { null }
        val ts = at(0)?.let { parseTimestamp(it, zone) } ?: return null
        val amount = at(1)?.let(::parseKopecks) ?: return null
        val category = at(3) ?: return null
        return CsvRow(ts, amount, at(2) ?: "RUB", category, at(4), at(5), at(6), at(7))
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
