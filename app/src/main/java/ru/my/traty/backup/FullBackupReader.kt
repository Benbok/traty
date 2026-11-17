package ru.my.traty.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Файл → [FullBackup] с проверкой версий. Чистая логика без Android.
 * Ошибки — [FullBackupException] с текстом для пользователя; в базу на этом шаге ничего не пишется.
 */
object FullBackupReader {
    /** Полная копия больше CSV (в ней всё приложение), но не бывает больше этого; защищает от нехватки памяти. */
    const val MAX_BYTES = 64 * 1024 * 1024

    const val NOT_A_BACKUP = "Этот файл не похож на полную копию «Трат». Выберите файл, сохранённый кнопкой «Сохранить полную копию»."
    const val DAMAGED = "Файл копии повреждён или сохранён не полностью. Данные на телефоне не изменились."
    const val TOO_NEW = "Эта копия сделана более новой версией приложения. Обновите «Траты» и попробуйте снова."

    private val json = Json { ignoreUnknownKeys = true }

    fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw FullBackupException("Файл слишком большой для копии «Трат».")
        }
        return out.toByteArray()
    }

    /**
     * [maxSchemaVersion] — версия базы этого приложения: копия с более новой схемой отклоняется,
     * со старой читается (недостающие поля получают значения по умолчанию).
     */
    fun decode(bytes: ByteArray, maxSchemaVersion: Int): FullBackup {
        val text = utf8(bytes).removePrefix("﻿").trim()
        if (!text.startsWith("{")) throw FullBackupException(NOT_A_BACKUP)
        val root = parse(text) as? JsonObject ?: throw FullBackupException(DAMAGED)
        val format = root.int("formatVersion")
        val schema = root.int("schemaVersion")
        if (format == null || schema == null || root["createdAt"] == null) throw FullBackupException(NOT_A_BACKUP)
        if (format > FULL_BACKUP_FORMAT_VERSION || schema > maxSchemaVersion) throw FullBackupException(TOO_NEW)
        if (format < 1 || schema < 1) throw FullBackupException(NOT_A_BACKUP)
        val backup = try {
            json.decodeFromJsonElement(FullBackup.serializer(), root)
        } catch (e: SerializationException) {
            throw FullBackupException(DAMAGED)
        } catch (e: IllegalArgumentException) {
            throw FullBackupException(DAMAGED)
        }
        // Приложение всегда хранит хотя бы «Прочее»: копия без категорий стёрла бы всё, ничего не вернув
        if (backup.categories.isEmpty()) throw FullBackupException(NOT_A_BACKUP)
        return backup
    }

    private fun parse(text: String): JsonElement = try {
        json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw FullBackupException(DAMAGED)
    } catch (e: IllegalArgumentException) {
        throw FullBackupException(DAMAGED)
    }

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        throw FullBackupException(NOT_A_BACKUP)
    }
}
