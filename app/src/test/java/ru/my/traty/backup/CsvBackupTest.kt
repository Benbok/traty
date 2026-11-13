package ru.my.traty.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.ZoneId

class CsvBackupTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun row(
        amount: Long = 25000,
        ts: Long = 1_700_000_000_123,
        category: String = "Продукты",
        sub: String? = "Рынок",
        place: String? = "Ашан",
        note: String? = null,
        raw: String? = null,
    ) = ExpenseWithCategory(
        Expense(amountKopecks = amount, categoryId = 1, timestamp = ts, place = place, note = note, rawText = raw),
        categoryName = category, categoryEmoji = "🛒", subcategoryName = sub,
    )

    private fun roundTrip(vararg rows: ExpenseWithCategory): CsvParseResult = roundTrip(rows.toList(), emptyList())

    private fun roundTrip(rows: List<ExpenseWithCategory>, incomes: List<Income>): CsvParseResult {
        val w = StringWriter()
        CsvBackup.write(rows, incomes, zone, w)
        return CsvBackup.parse(w.toString(), zone)
    }

    private fun ts(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun roundTripPreservesSpecialCharacters() {
        val r = roundTrip(row(note = "сказал \"да\"; потом\nушёл", raw = "кофе; 250"))
        assertTrue(r.badLines.isEmpty())
        val x = r.rows.single()
        assertEquals("сказал \"да\"; потом\nушёл", x.note)
        assertEquals("кофе; 250", x.rawText)
        assertEquals("Продукты", x.category)
        assertEquals("Рынок", x.subcategory)
        assertEquals("Ашан", x.place)
    }

    @Test
    fun roundTripPreservesAmountsAndTime() {
        val r = roundTrip(row(amount = 12345, ts = 1_700_000_042_123), row(amount = 100, ts = 1_700_000_100_000, sub = null))
        assertEquals(listOf(12345L, 100L), r.rows.map { it.amountKopecks })
        assertEquals(listOf(1_700_000_042_123L, 1_700_000_100_000L), r.rows.map { it.timestamp })
        assertEquals(null, r.rows[1].subcategory)
    }

    @Test
    fun parsesExcelResavedFormats() {
        val text = "дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза\r\n" +
            "13.11.2026 14:03;1 234,50;RUB;Продукты;;;;\r\n" +
            "13.11.2026;99.9;RUB;Кафе и рестораны;Кофе;;;\r\n" +
            "14.11.2026 09:05;1 000;RUB;Транспорт;;;;\r\n"
        val r = CsvBackup.parse(text, zone)
        assertTrue(r.badLines.isEmpty())
        assertEquals(listOf(123450L, 9990L, 100000L), r.rows.map { it.amountKopecks })
        assertEquals(ts(2026, 11, 13, 14, 3), r.rows[0].timestamp)
        assertEquals(ts(2026, 11, 13, 12, 0), r.rows[1].timestamp)
        assertEquals("Кофе", r.rows[1].subcategory)
    }

    @Test
    fun skipsHeaderAndBlankLines() {
        val text = "﻿" + CsvBackup.HEADER + "\n" +
            "\"2026-11-13T10:00\";\"250\";\"RUB\";\"Продукты\";\"\";\"\";\"\";\"\"\n\n\n"
        val r = CsvBackup.parse(text, zone)
        assertEquals(1, r.rows.size)
        assertTrue(r.badLines.isEmpty())
    }

    @Test
    fun reportsBadLinesAndKeepsGoodOnes() {
        val text = CsvBackup.HEADER + "\n" +
            "2026-11-13T10:00;250;RUB;Продукты;;;;\n" +
            "2026-11-13T11:00;abc;RUB;Продукты;;;;\n" +
            "2026-11-13T12:00;100;RUB;;;;;\n" +
            "2026-11-13T13:00;300;RUB;Транспорт;;;;\n"
        val r = CsvBackup.parse(text, zone)
        assertEquals(listOf(3, 4), r.badLines)
        assertEquals(listOf(25000L, 30000L), r.rows.map { it.amountKopecks })
    }

    @Test
    fun decodesCp1251WithoutBom() {
        // Русский Excel «Сохранить как CSV» пишет windows-1251 без BOM
        val bytes = (CsvBackup.HEADER + "\r\n13.11.2026 14:03;250;RUB;Продукты;Рынок;;морковь;\r\n")
            .toByteArray(charset("windows-1251"))
        val r = CsvBackup.parse(CsvBackup.decode(bytes), zone)
        assertTrue(r.hasHeader)
        assertTrue(r.badLines.isEmpty())
        assertEquals("Продукты", r.rows.single().category)
        assertEquals("морковь", r.rows.single().note)
    }

    @Test
    fun decodesUtf8WithBom() {
        val bytes = ("﻿" + CsvBackup.HEADER + "\n2026-11-13T10:00;250;RUB;Продукты;;;;\n").toByteArray(Charsets.UTF_8)
        val text = CsvBackup.decode(bytes)
        assertEquals("Продукты", CsvBackup.parse(text, zone).rows.single().category)
    }

    @Test
    fun detectsMissingHeader() {
        assertEquals(false, CsvBackup.parse("2026-11-13T10:00;250;RUB;Продукты;;;;\n", zone).hasHeader)
        // Excel с запятой вместо «;» — заголовок не распознаётся
        assertEquals(false, CsvBackup.parse(CsvBackup.HEADER.replace(';', ',') + "\n", zone).hasHeader)
        assertEquals(true, CsvBackup.parse("\n" + CsvBackup.HEADER + "\n", zone).hasHeader)
    }

    @Test
    fun roundTripWithIncomes() {
        val income = Income(amountKopecks = 5_000_000, source = "Зарплата", note = "за октябрь", timestamp = 1_700_000_000_000)
        val r = roundTrip(listOf(row(ts = 1_700_000_100_000)), listOf(income))
        assertTrue(r.badLines.isEmpty())
        // по времени: сначала пополнение, потом трата
        assertEquals(listOf(CsvRow.INCOME, CsvRow.EXPENSE), r.rows.map { it.type })
        val i = r.rows[0]
        assertEquals(5_000_000L, i.amountKopecks)
        assertEquals(1_700_000_000_000L, i.timestamp)
        assertEquals("Зарплата", i.category)
        assertEquals(null, i.subcategory)
        assertEquals(null, i.place)
        assertEquals("за октябрь", i.note)
        assertEquals(null, i.rawText)
        val e = r.rows[1]
        assertEquals("Продукты", e.category)
        assertEquals(25000L, e.amountKopecks)
    }

    @Test
    fun oldFormatWithoutTypeColumnParsesAsExpenses() {
        val oldHeader = "дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза"
        val r = CsvBackup.parse(oldHeader + "\n2026-11-13T10:00;250;RUB;Продукты;;;;\n", zone)
        assertTrue(r.hasHeader)
        assertTrue(r.badLines.isEmpty())
        assertEquals(CsvRow.EXPENSE, r.rows.single().type)
        // неизвестный или пустой тип — трата; регистр типа не важен
        val t = CsvBackup.parse(
            CsvBackup.HEADER + "\n2026-11-13T10:00;1;RUB;Продукты;;;;;\n2026-11-13T10:01;2;RUB;Продукты;;;;;что-то\n" +
                "2026-11-13T10:02;3;RUB;Зарплата;;;;;ПОПОЛНЕНИЕ\n",
            zone,
        )
        assertEquals(listOf(CsvRow.EXPENSE, CsvRow.EXPENSE, CsvRow.INCOME), t.rows.map { it.type })
    }

    @Test
    fun incomeRowWithoutCategoryIsAccepted() {
        val text = CsvBackup.HEADER + "\n" +
            "13.11.2026 14:03;1 234,50;RUB;;;;подарок;;пополнение\n" +
            "2026-11-13T12:00;100;RUB;;;;;;трата\n"
        val r = CsvBackup.parse(text, zone)
        // у траты категория обязательна, у пополнения — нет
        assertEquals(listOf(3), r.badLines)
        val i = r.rows.single()
        assertEquals(CsvRow.INCOME, i.type)
        assertEquals("Прочее", i.category)
        assertEquals(123450L, i.amountKopecks)
        assertEquals(ts(2026, 11, 13, 14, 3), i.timestamp)
        assertEquals("подарок", i.note)
    }

    @Test
    fun emptyCurrencyDefaultsToRub() {
        val r = CsvBackup.parse("2026-11-13T10:00;250;;Продукты;;;;\n", zone)
        assertEquals("RUB", r.rows.single().currency)
    }
}
