package ru.my.traty.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Репозиторий, из которого берутся релизы. Ссылки из ответа принимаются только отсюда. */
internal const val RELEASES_BASE = "https://github.com/Benbok/traty/"

/** Страница с последним релизом: открывается, когда в ответе нет прямой ссылки на файл. */
internal const val RELEASES_PAGE = "${RELEASES_BASE}releases/latest"

/** Последний выпущенный релиз: номер версии и куда идти за файлом. */
data class ReleaseInfo(
    /** Без «v»: «0.1.1». */
    val version: String,
    /** Прямая ссылка на APK, а если файла нет в релизе — страница релиза. */
    val downloadUrl: String,
)

object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Разбирает ответ GitHub `releases/latest`. Возвращает null для черновиков, пробных релизов (pre-release),
     * неподходящего номера версии и непонятного формата: такой ответ просто игнорируется.
     */
    fun parse(body: String): ReleaseInfo? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (root.bool("draft") || root.bool("prerelease")) return null
        val version = AppVersion.clean(root.string("tag_name"))
        if (AppVersion.parse(version) == null) return null

        val apk = (root["assets"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("name").endsWith(".apk", ignoreCase = true) }
            ?.string("browser_download_url")
        val url = listOf(apk, root.string("html_url"))
            .firstOrNull { it != null && isTrusted(it) }
            ?: RELEASES_PAGE
        return ReleaseInfo(version, url)
    }

    /** Только HTTPS и только наш репозиторий: из ответа сети нельзя открыть произвольный адрес. */
    internal fun isTrusted(url: String): Boolean = url.startsWith(RELEASES_BASE)

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
}
