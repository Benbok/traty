package ru.my.traty.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.my.traty.data.AppSettings
import ru.my.traty.data.LlmProvider
import ru.my.traty.data.PortableSettings
import ru.my.traty.data.ThemeMode
import ru.my.traty.data.portable

/** Чистая логика чтения: без базы и без Android. */
class FullBackupReaderTest {
    private val schema = 15

    private fun minimal(format: Int = FULL_BACKUP_FORMAT_VERSION, schemaVersion: Int = schema) = FullBackup(
        formatVersion = format,
        schemaVersion = schemaVersion,
        createdAt = 1_700_000_000_000,
        appVersion = "1.2",
        categories = listOf(BackupCategory("Прочее", "📦")),
        expenses = listOf(BackupExpense(key = 1, amountKopecks = 12_345, category = "Прочее", timestamp = 5, source = "voice")),
        settings = PortableSettings(periodStartDay = 10, themeMode = "DARK"),
    )

    private fun decode(text: String) = FullBackupReader.decode(text.toByteArray(Charsets.UTF_8), schema)

    private fun assertRejected(text: String, expected: String) {
        try {
            decode(text)
            fail("Ожидалась ошибка")
        } catch (e: FullBackupException) {
            assertEquals(expected, e.message)
        }
    }

    @Test
    fun readsOwnFile() {
        val b = minimal()
        assertEquals(b, decode(FullBackupWriter.encode(b)))
    }

    @Test
    fun topLevelStartsWithVersions() {
        val text = FullBackupWriter.encode(minimal())
        val order = listOf("formatVersion", "schemaVersion", "createdAt", "appVersion").map { text.indexOf("\"$it\"") }
        assertEquals(order.sorted(), order)
        assertTrue(text.contains("\"amountKopecks\": 12345"))
    }

    @Test
    fun newerFormatOrSchemaIsRejected() {
        assertRejected(FullBackupWriter.encode(minimal(format = FULL_BACKUP_FORMAT_VERSION + 1)), FullBackupReader.TOO_NEW)
        assertRejected(FullBackupWriter.encode(minimal(schemaVersion = schema + 1)), FullBackupReader.TOO_NEW)
        // Даже если новая версия поменяла поля до неузнаваемости, сообщение о версии, а не о повреждении
        assertRejected("""{"formatVersion": 9, "schemaVersion": 99, "createdAt": 1, "categories": "иначе"}""", FullBackupReader.TOO_NEW)
    }

    @Test
    fun olderSchemaWithMissingSectionsIsRead() {
        val old = """{"formatVersion":1,"schemaVersion":11,"createdAt":1,"categories":[{"name":"Прочее","emoji":"📦"}],
            |"expenses":[{"key":1,"amountKopecks":100,"category":"Прочее","timestamp":1,"source":"text"}],"somethingOld":true}""".trimMargin()
        val b = decode(old)
        assertEquals(11, b.schemaVersion)
        assertTrue(b.debts.isEmpty())
        assertEquals(PortableSettings(), b.settings)
    }

    @Test
    fun garbageIsNotABackup() {
        assertRejected("дата;сумма;валюта", FullBackupReader.NOT_A_BACKUP)
        assertRejected("""{"hello": "world"}""", FullBackupReader.NOT_A_BACKUP)
        assertRejected("""{"formatVersion":"1","schemaVersion":15,"createdAt":1}""", FullBackupReader.NOT_A_BACKUP)
        try {
            FullBackupReader.decode(byteArrayOf(0x7B, -1, -2, -3), schema)
            fail()
        } catch (e: FullBackupException) {
            assertEquals(FullBackupReader.NOT_A_BACKUP, e.message)
        }
    }

    @Test
    fun truncatedOrBrokenJsonIsDamaged() {
        val text = FullBackupWriter.encode(minimal())
        assertRejected(text.substring(0, text.length - 20), FullBackupReader.DAMAGED)
        assertRejected(text.replace("12345", "\"двенадцать\""), FullBackupReader.DAMAGED)
    }

    @Test
    fun byteOrderMarkIsAccepted() {
        assertEquals(minimal(), decode("﻿" + FullBackupWriter.encode(minimal())))
    }

    @Test
    fun sizeIsLimited() {
        val huge = object : java.io.InputStream() {
            override fun read(): Int = 'a'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { b.fill('a'.code.toByte(), off, off + len) }
        }
        try {
            FullBackupReader.readLimited(huge)
            fail()
        } catch (_: FullBackupException) {
        }
    }

    @Test
    fun portableSettingsHaveNoSecrets() {
        val s = AppSettings(
            themeMode = ThemeMode.DARK, provider = LlmProvider.DEEPSEEK, periodStartDay = 25,
            deepseekKey = "sk-SECRET",
        ).portable()
        assertEquals("DARK", s.themeMode)
        assertEquals("DEEPSEEK", s.provider)
        assertEquals(25, s.periodStartDay)
        assertFalse(FullBackupWriter.json.encodeToString(PortableSettings.serializer(), s).contains("SECRET"))
    }
}
