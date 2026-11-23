package ru.my.traty.receiptsmoke

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Base64
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import ru.my.traty.AppContainer
import ru.my.traty.TratyApp
import ru.my.traty.data.AppDatabase
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Opt-in check of the documented API using the installed app's settings and a synthetic receipt. */
class DeepSeekReceiptSmoke : Instrumentation() {
    private var mode: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        mode = arguments?.getString("runReceiptSmoke")
    }

    override fun callApplicationOnCreate(app: Application) {
        super.callApplicationOnCreate(app)
        start()
    }

    override fun onStart() {
        val status = try {
            when (mode) {
                "true" -> verify()
                "pipeline" -> verifyPipeline()
                else -> "NOT_REQUESTED"
            }
        } catch (e: Exception) {
            "FAILED_" + e.javaClass.simpleName
        }
        finish(
            if (status == "VERIFIED" || status == "NOT_REQUESTED") Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("receiptSmoke", status) },
        )
    }

    private fun verify(): String {
        val key = runBlocking { (targetContext.applicationContext as TratyApp).container.settings.current().deepseekKey }
        if (key.isBlank()) return "KEY_NOT_CONFIGURED"

        val body = JSONObject()
            .put("model", "deepseek-flash")
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("max_tokens", 256)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
                .put(JSONObject().put("type", "text").put("text",
                    "Read the receipt in the attached image. Return JSON {\"item\":\"name\",\"total\":0.00}. Copy the item and the printed total."))
                .put(JSONObject().put("type", "image_url").put("image_url",
                    JSONObject().put("url", "data:image/jpeg;base64," + Base64.encodeToString(
                        image(listOf("SYNTHETIC STORE", "MANGO    148.73", "TOTAL    148.73")), Base64.NO_WRAP,
                    )))))))
        val url = URL("https://api.deepseek.com/chat/completions")
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != 200) return "HTTP_$status"
            val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val choice = response.getJSONArray("choices").getJSONObject(0)
            if (choice.optString("finish_reason") != "stop") return "INCOMPLETE_RESPONSE"
            val result = JSONObject(choice.getJSONObject("message").getString("content"))
            val item = result.optString("item").trim()
            val total = result.optString("total").toBigDecimalOrNull()
            return if (item.equals("MANGO", ignoreCase = true) && total?.compareTo("148.73".toBigDecimal()) == 0) {
                "VERIFIED"
            } else {
                "VALUES_DO_NOT_MATCH"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyPipeline(): String = runBlocking {
        val app = targetContext.applicationContext as TratyApp
        if (app.container.settings.current().deepseekKey.isBlank()) return@runBlocking "KEY_NOT_CONFIGURED"
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        try {
            val testContainer = AppContainer(app, db)
            val frames = listOf(
                image(listOf("SYNTHETIC STORE", "23.11.2026", "MANGO     1 x 148.73     148.73",
                    "APPLE     2 x 19.99      39.98", "TEA       1 x 90.00      90.00")),
                image(listOf("APPLE     2 x 19.99      39.98", "TEA       1 x 90.00      90.00",
                    "MANGO     1 x 148.73     148.73", "TOTAL                    427.44")),
            )
            val row = testContainer.receipts.scanPhotos(frames).draft.rows.single()
            val linePrices = row.items.map { it.priceKopecks() }
            val expected = listOf(14_873L, 3_998L, 9_000L, 14_873L)
            if (row.kopecks() == 42_744L && linePrices == expected && row.dateText == "2026-11-23") {
                "VERIFIED"
            } else {
                "PIPELINE_VALUES_DO_NOT_MATCH"
            }
        } finally {
            db.close()
        }
    }

    private fun image(lines: List<String>): ByteArray {
        val bitmap = Bitmap.createBitmap(820, 100 + lines.size * 75, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 40f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            lines.forEachIndexed { index, line -> canvas.drawText(line, 35f, 80f + index * 75f, paint) }
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
