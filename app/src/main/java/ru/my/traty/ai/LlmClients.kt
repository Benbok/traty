package ru.my.traty.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.my.traty.data.AppSettings
import ru.my.traty.data.LlmProvider
import java.util.concurrent.TimeUnit

/** Общий интерфейс для любой языковой модели. */
interface LlmClient {
    val name: String
    suspend fun complete(system: String, user: String, jsonMode: Boolean): String
}

internal val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .build()

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

private fun postJson(url: String, body: JsonObject, headers: Map<String, String>): JsonObject {
    val req = Request.Builder().url(url).apply {
        headers.forEach { (k, v) -> header(k, v) }
    }.post(body.toString().toRequestBody(JSON_MEDIA)).build()
    http.newCall(req).execute().use { r ->
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw LlmHttpException(r.code, "HTTP ${r.code}: ${text.take(300)}")
        return lenientJson.parseToJsonElement(text).jsonObject
    }
}

/** DeepSeek — OpenAI-совместимый API. */
class DeepSeekClient(private val apiKey: String, private val model: String) : LlmClient {
    override val name = "DeepSeek"

    override suspend fun complete(system: String, user: String, jsonMode: Boolean): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Не указан ключ DeepSeek (Настройки)" }
            val body = buildJsonObject {
                put("model", model)
                put("temperature", 0.1)
                if (jsonMode) putJsonObject("response_format") { put("type", "json_object") }
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", system) }
                    addJsonObject { put("role", "user"); put("content", user) }
                }
            }
            val resp = postJson(
                "https://api.deepseek.com/chat/completions",
                body,
                mapOf("Authorization" to "Bearer $apiKey"),
            )
            resp["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }
}

/** YandexGPT через Foundation Models API (Yandex Cloud / AI Studio). */
class YandexGptClient(
    private val apiKey: String,
    private val folderId: String,
    private val model: String,
) : LlmClient {
    override val name = "YandexGPT"

    override suspend fun complete(system: String, user: String, jsonMode: Boolean): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank() && folderId.isNotBlank()) {
                "Не указан API-ключ или ID каталога Yandex Cloud (Настройки)"
            }
            val body = buildJsonObject {
                put("modelUri", "gpt://$folderId/$model")
                putJsonObject("completionOptions") {
                    put("stream", false)
                    put("temperature", 0.1)
                    put("maxTokens", "800")
                }
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("text", system) }
                    addJsonObject { put("role", "user"); put("text", user) }
                }
            }
            val resp = postJson(
                "https://llm.api.cloud.yandex.net/foundationModels/v1/completion",
                body,
                mapOf("Authorization" to "Api-Key $apiKey", "x-folder-id" to folderId),
            )
            resp["result"]!!.jsonObject["alternatives"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["text"]!!.jsonPrimitive.content
        }
}

/** null — значит выбран режим без ИИ. */
fun clientFor(s: AppSettings): LlmClient? = when (s.provider) {
    LlmProvider.DEEPSEEK -> DeepSeekClient(s.deepseekKey, s.deepseekModel)
    LlmProvider.YANDEX -> YandexGptClient(s.yandexKey, s.yandexFolderId, s.yandexModel)
    LlmProvider.OFFLINE -> null
}
