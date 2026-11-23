package ru.my.traty.ai.receipt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.my.traty.ai.LlmHttpException
import ru.my.traty.ai.http
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

internal const val RECEIPT_VISION_MODEL = "deepseek-flash"
private const val LOG_TAG = "TratyReceipt"
private val jsonType = "application/json; charset=utf-8".toMediaType()

private val receiptHttp = http.newBuilder()
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

internal suspend fun requestReceiptVision(key: String, system: String, user: String, photos: List<ByteArray>): String =
    withContext(Dispatchers.IO) {
        val body = receiptVisionBody(system, user, photos).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        suspendCancellableCoroutine { continuation ->
            val startedAt = System.nanoTime()
            val call = receiptHttp.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    receiptDiagnostic(startedAt, if (call.isCanceled()) "cancelled" else "network_error")
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use { receiptAnswer(it, startedAt) } })
                }
            })
        }
    }

internal fun receiptVisionBody(system: String, user: String, photos: List<ByteArray>): JsonObject =
    buildJsonObject {
        put("model", RECEIPT_VISION_MODEL)
        put("temperature", 0.1)
        put("max_tokens", 16_384)
        putJsonObject("thinking") { put("type", "disabled") }
        putJsonObject("response_format") { put("type", "json_object") }
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", system)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject { put("type", "text"); put("text", user) }
                    for (photo in photos) {
                        val encoded = Base64.getEncoder().encodeToString(photo)
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$encoded") }
                        }
                    }
                }
            }
        }
    }

private fun receiptAnswer(response: Response, startedAt: Long): String {
    if (!response.isSuccessful) {
        receiptDiagnostic(startedAt, "http_${response.code}")
        throw LlmHttpException(response.code, "HTTP ${response.code}")
    }
    val answer = response.body?.string().orEmpty()
    val payload = runCatching { Json.parseToJsonElement(answer).jsonObject }.getOrNull()
    val content = runCatching {
        val choice = payload?.getValue("choices")?.jsonArray?.first()?.jsonObject ?: error("Missing choice")
        check(choice.getValue("finish_reason").jsonPrimitive.content == "stop")
        choice.getValue("message").jsonObject.getValue("content").jsonPrimitive.content.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    receiptDiagnostic(startedAt, if (content == null) "invalid_response" else "response_received", payload?.get("usage") as? JsonObject)
    return content ?: throw ReceiptException(BAD_RECEIPT_ANSWER)
}

/** Локальная диагностика, включаемая только через системный уровень логирования для тега. */
private fun receiptDiagnostic(startedAt: Long, status: String, usage: JsonObject? = null) {
    if (!Log.isLoggable(LOG_TAG, Log.DEBUG)) return
    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
    fun tokens(key: String) = (usage?.get(key) as? JsonPrimitive)?.intOrNull
    Log.d(LOG_TAG, "status=$status duration_ms=$elapsed prompt_tokens=${tokens("prompt_tokens")} " +
        "cache_hit_tokens=${tokens("prompt_cache_hit_tokens")} completion_tokens=${tokens("completion_tokens")}")
}
