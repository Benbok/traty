package ru.my.traty.speech

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ModelManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val body = "hello gigaam".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
    private val manifest = ModelManifest(
        "test", listOf(ModelFile("model.onnx", "https://example.test/model.onnx", body.size.toLong(), sha)),
    )

    private fun client(code: Int = 200, payload: ByteArray = body) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(payload.toResponseBody()).build()
        }).build()

    private fun manager(vararg dirs: File, http: OkHttpClient = client()) = ModelManager(dirs.toList(), manifest, http)

    @Test
    fun missingWhenNoFiles() {
        val m = manager(tmp.newFolder("a"))
        assertEquals(ModelState.Missing, m.state.value)
    }

    @Test
    fun readyWhenFileWithRightSizeLivesInAnySearchDir() {
        val a = tmp.newFolder("a")
        val b = tmp.newFolder("b")
        File(b, "model.onnx").writeBytes(body)
        val m = manager(a, b)
        assertEquals(ModelState.Ready(b), m.state.value)
    }

    @Test
    fun refreshPicksUpFileAppearingAfterConstruction() {
        val a = tmp.newFolder("a")
        val m = manager(a)
        assertEquals(ModelState.Missing, m.state.value)
        File(a, "model.onnx").writeBytes(body)
        m.refresh()
        assertEquals(ModelState.Ready(a), m.state.value)
    }

    @Test
    fun wrongSizeCountsAsMissing() {
        val a = tmp.newFolder("a")
        File(a, "model.onnx").writeBytes(body.copyOf(3))
        assertEquals(ModelState.Missing, manager(a).state.value)
    }

    @Test
    fun downloadWritesPartThenRenamesAndVerifiesHash() = runTest {
        val a = tmp.newFolder("a")
        val m = manager(a)
        m.download()
        assertEquals(ModelState.Ready(a), m.state.value)
        assertTrue(File(a, "model.onnx").readBytes().contentEquals(body))
        assertFalse(File(a, "model.onnx.part").exists())
    }

    @Test
    fun badHashFailsAndLeavesNoFiles() = runTest {
        val a = tmp.newFolder("a")
        val m = manager(a, http = client(payload = "HELLO gigaam".toByteArray()))
        m.download()
        assertTrue(m.state.value is ModelState.Failed)
        assertFalse(File(a, "model.onnx").exists())
        assertFalse(File(a, "model.onnx.part").exists())
    }

    @Test
    fun httpErrorFails() = runTest {
        val a = tmp.newFolder("a")
        val m = manager(a, http = client(code = 404, payload = ByteArray(0)))
        m.download()
        val s = m.state.value as ModelState.Failed
        assertTrue(s.message.contains("404"))
    }

    @Test
    fun deleteRemovesFilesInDownloadDirAndGoesMissing() = runTest {
        val a = tmp.newFolder("a")
        val m = manager(a)
        m.download()
        m.delete()
        assertEquals(ModelState.Missing, m.state.value)
        assertFalse(File(a, "model.onnx").exists())
    }
}
