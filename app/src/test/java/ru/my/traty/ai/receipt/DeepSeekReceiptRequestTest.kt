package ru.my.traty.ai.receipt

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekReceiptRequestTest {
    @Test fun sendsFramesInOrderWithJsonModeAndVisionModel() {
        val body = receiptVisionBody("system", "user", listOf(byteArrayOf(1, 2), byteArrayOf(3)))
        assertEquals("deepseek-flash", body.getValue("model").jsonPrimitive.content)
        assertEquals("json_object", body.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content)
        val parts = body.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonArray
        assertEquals(listOf("text", "image_url", "image_url"), parts.map {
            it.jsonObject.getValue("type").jsonPrimitive.content
        })
        assertEquals(listOf("data:image/jpeg;base64,AQI=", "data:image/jpeg;base64,Aw=="), parts.drop(1).map {
            it.jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
        })
    }
}
