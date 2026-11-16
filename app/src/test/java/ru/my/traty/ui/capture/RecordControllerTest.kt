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
    /** В тестах «главный поток» выполняет сразу; там, где важен порядок, блоки копятся в очереди. */
    private val immediate: (() -> Unit) -> Unit = { it() }

    private fun controller(mainThread: (() -> Unit) -> Unit = immediate) =
        RecordController({ FakeEngine().also { engines += it } }, mainThread = mainThread) { texts += it }

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
    fun stopSwitchesToRecognizingRightAway() {
        val c = controller()
        c.toggle()
        engines.single().listener!!.onPartial("кофе 250")
        c.toggle()
        // Кнопка нажата: запись уже не идёт, ждём только итог распознавания
        assertEquals(RecordState.Recognizing("кофе 250"), c.state.value)
    }

    @Test
    fun tapsWhileRecognizingDoNothing() {
        val c = controller()
        c.toggle()
        c.toggle()
        c.toggle()
        c.toggle()
        assertEquals(1, engines.size)
        assertEquals(1, engines.single().stopped)
        assertTrue(c.state.value is RecordState.Recognizing)
    }

    @Test
    fun engineEndOfSpeechAlsoMeansRecognizing() {
        val c = controller()
        c.toggle()
        val l = engines.single().listener!!
        l.onPartial("такси")
        l.onRecognizing()
        assertEquals(RecordState.Recognizing("такси"), c.state.value)
        // Дораспознанный хвост обновляет текст, но не возвращает «запись»
        l.onPartial("такси 400")
        l.onLevel(0.7f)
        assertEquals(RecordState.Recognizing("такси 400"), c.state.value)
    }

    @Test
    fun cancelWhileRecognizingDropsTheResult() {
        val c = controller()
        c.toggle()
        val l = engines.single().listener!!
        c.toggle()
        c.cancel()
        l.onFinal("кофе 250")
        assertEquals(RecordState.Idle, c.state.value)
        assertTrue(texts.isEmpty())
    }

    @Test
    fun finalIsHandedOverOnTheMainThreadInOneStep() {
        val queue = mutableListOf<() -> Unit>()
        val c = controller(mainThread = { queue += it })
        c.toggle()
        c.toggle()
        // Движок отдал итог со своего потока: пока главный поток его не принял, кнопка не становится свободной
        engines.single().listener!!.onFinal("кофе 250")
        assertTrue(c.state.value is RecordState.Recognizing)
        assertTrue(texts.isEmpty())
        c.toggle()
        assertEquals(1, engines.size)
        // В одном шаге главного потока: текст уходит на разбор и только потом запись считается законченной
        queue.removeAt(0).invoke()
        assertEquals(listOf("кофе 250"), texts)
        assertEquals(RecordState.Idle, c.state.value)
    }

    @Test
    fun errorIsAlsoHandedOverOnTheMainThread() {
        val queue = mutableListOf<() -> Unit>()
        val c = controller(mainThread = { queue += it })
        c.toggle()
        engines.single().listener!!.onError(IllegalStateException("нет сервиса"))
        assertTrue(c.state.value is RecordState.Listening)
        queue.removeAt(0).invoke()
        assertEquals(RecordState.Error("нет сервиса"), c.state.value)
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
        val c = RecordController({ FakeEngine().also { engines += it } }, cues, immediate) { texts += it }
        c.toggle()
        assertEquals(0, engines.single().started)
        cues.startDone!!.invoke()
        assertEquals(1, engines.single().started)
    }

    @Test
    fun cancelDuringStartCueNeverStartsEngine() {
        val cues = FakeCues()
        val c = RecordController({ FakeEngine().also { engines += it } }, cues, immediate) { texts += it }
        c.toggle()
        c.toggle() // второе нажатие, пока играет сигнал
        cues.startDone!!.invoke()
        assertEquals(0, engines.single().started)
        assertEquals(RecordState.Idle, c.state.value)
    }

    @Test
    fun endCuePlaysOnlyWhenTextRecognized() {
        val cues = FakeCues()
        val c = RecordController({ FakeEngine().also { engines += it } }, cues, immediate) { texts += it }
        c.toggle(); cues.startDone!!.invoke()
        engines.last().listener!!.onFinal("кофе 250")
        assertEquals(1, cues.ended)
        c.toggle(); cues.startDone!!.invoke()
        engines.last().listener!!.onFinal("")
        assertEquals(1, cues.ended)
    }
}
