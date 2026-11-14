package ru.my.traty.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class VadSegmenterTest {
    /** Прогоняет [ms] миллисекунд кадрами по 100 мс; возвращает действие на последнем кадре. */
    private fun VadSegmenter.feed(isSpeech: Boolean, ms: Int): VadAction {
        var last = VadAction.CONTINUE
        repeat(ms / 100) { last = onFrame(isSpeech, 100) }
        return last
    }

    @Test
    fun pauseAfterSpeechStopsAfterSilenceThreshold() {
        val s = VadSegmenter(silenceToStopMs = 1500)
        assertEquals(VadAction.CONTINUE, s.feed(true, 1000))
        assertEquals(VadAction.CONTINUE, s.feed(false, 1400))
        assertEquals(VadAction.STOP, s.feed(false, 100))
    }

    @Test
    fun shortPausesBetweenWordsDoNotStop() {
        val s = VadSegmenter(silenceToStopMs = 1500)
        s.feed(true, 500)
        assertEquals(VadAction.CONTINUE, s.feed(false, 800))
        assertEquals(VadAction.CONTINUE, s.feed(true, 300))
        assertEquals(VadAction.CONTINUE, s.feed(false, 800))
    }

    @Test
    fun silenceBeforeAnySpeechDoesNotUseThePauseThreshold() {
        val s = VadSegmenter(silenceToStopMs = 1500, noSpeechTimeoutMs = 8000)
        assertEquals(VadAction.CONTINUE, s.feed(false, 7900))
        assertEquals(VadAction.STOP, s.feed(false, 100))
    }

    @Test
    fun continuousSpeechStopsAtMaxDuration() {
        val s = VadSegmenter(maxDurationMs = 30_000)
        assertEquals(VadAction.CONTINUE, s.feed(true, 29_900))
        assertEquals(VadAction.STOP, s.feed(true, 100))
    }
}
