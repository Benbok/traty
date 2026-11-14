package ru.my.traty.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.speech.SpeechEngine
import ru.my.traty.speech.SpeechListener

private class FakeEngine : SpeechEngine {
    var listener: SpeechListener? = null
    var started = 0
    var stopped = 0
    var cancelled = 0
    var released = 0
    override fun start(listener: SpeechListener) { this.listener = listener; started++ }
    override fun stop() { stopped++ }
    override fun cancel() { cancelled++ }
    override fun release() { released++ }
}

class RecordControllerTest {
    private val texts = mutableListOf<String>()
    private val engines = mutableListOf<FakeEngine>()
    private fun controller() = RecordController({ FakeEngine().also { engines += it } }) { texts += it }

    @Test
    fun toggleFromIdleStartsListening() {
        val c = controller()
        c.toggle()
        assertEquals(RecordState.Listening(), c.state.value)
        assertEquals(1, engines.single().started)
    }

    @Test
    fun partialAndLevelUpdateListeningState() {
        val c = controller()
        c.toggle()
        engines.single().listener!!.onPartial("кофе 250")
        engines.single().listener!!.onLevel(0.5f)
        assertEquals(RecordState.Listening("кофе 250", 0.5f), c.state.value)
    }

    @Test
    fun finalDeliversTextReleasesEngineAndGoesIdle() {
        val c = controller()
        c.toggle()
        engines.single().listener!!.onFinal("кофе 250")
        assertEquals(listOf("кофе 250"), texts)
        assertEquals(RecordState.Idle, c.state.value)
        assertEquals(1, engines.single().released)
    }

    @Test
    fun blankFinalIsAnErrorAndDeliversNothing() {
        val c = controller()
        c.toggle()
        engines.single().listener!!.onFinal("   ")
        assertTrue(texts.isEmpty())
        assertEquals(RecordState.Error("Не расслышал, повторите"), c.state.value)
    }

    @Test
    fun toggleWhileListeningStopsEngine() {
        val c = controller()
        c.toggle()
        c.toggle()
        assertEquals(1, engines.single().stopped)
        assertEquals(1, engines.size)
    }

    @Test
    fun errorShowsMessageAndNextToggleStartsFreshEngine() {
        val c = controller()
        c.toggle()
        engines[0].listener!!.onError(IllegalStateException("нет сервиса"))
        assertEquals(RecordState.Error("нет сервиса"), c.state.value)
        assertEquals(1, engines[0].released)
        c.toggle()
        assertEquals(2, engines.size)
        assertEquals(RecordState.Listening(), c.state.value)
    }

    @Test
    fun cancelReturnsToIdleWithoutDeliveringText() {
        val c = controller()
        c.toggle()
        c.cancel()
        assertEquals(RecordState.Idle, c.state.value)
        assertEquals(1, engines.single().cancelled)
        assertEquals(1, engines.single().released)
        assertTrue(texts.isEmpty())
    }

    @Test
    fun lateCallbacksAfterCancelAreIgnored() {
        val c = controller()
        c.toggle()
        val l = engines.single().listener!!
        c.cancel()
        l.onFinal("кофе 250")
        l.onPartial("кофе")
        assertTrue(texts.isEmpty())
        assertEquals(RecordState.Idle, c.state.value)
    }

    private class FakeCues : RecordCues {
        var startDone: (() -> Unit)? = null
        var ended = 0
        override fun playStart(onDone: () -> Unit) { startDone = onDone }
        override fun playEnd() { ended++ }
    }

    @Test
    fun engineStartsOnlyAfterStartCueFinishes() {
        val cues = FakeCues()
        val c = RecordController({ FakeEngine().also { engines += it } }, cues) { texts += it }
        c.toggle()
        assertEquals(0, engines.single().started)
        cues.startDone!!.invoke()
        assertEquals(1, engines.single().started)
    }

    @Test
    fun cancelDuringStartCueNeverStartsEngine() {
        val cues = FakeCues()
        val c = RecordController({ FakeEngine().also { engines += it } }, cues) { texts += it }
        c.toggle()
        c.toggle() // второе нажатие, пока играет сигнал
        cues.startDone!!.invoke()
        assertEquals(0, engines.single().started)
        assertEquals(RecordState.Idle, c.state.value)
    }

    @Test
    fun endCuePlaysOnlyWhenTextRecognized() {
        val cues = FakeCues()
        val c = RecordController({ FakeEngine().also { engines += it } }, cues) { texts += it }
        c.toggle(); cues.startDone!!.invoke()
        engines.last().listener!!.onFinal("кофе 250")
        assertEquals(1, cues.ended)
        c.toggle(); cues.startDone!!.invoke()
        engines.last().listener!!.onFinal("")
        assertEquals(1, cues.ended)
    }
}
