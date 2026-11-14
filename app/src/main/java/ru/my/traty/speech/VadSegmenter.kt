package ru.my.traty.speech

enum class VadAction { CONTINUE, STOP }

/**
 * Решает, когда пора закончить запись, по флагам «речь / тишина» от детектора пауз (кадр за кадром).
 * Останавливает: паузу после речи ([silenceToStopMs]), молчание в самом начале ([noSpeechTimeoutMs])
 * и слишком долгую запись ([maxDurationMs]).
 */
class VadSegmenter(
    private val silenceToStopMs: Int = 1_500,
    private val maxDurationMs: Int = 30_000,
    private val noSpeechTimeoutMs: Int = 8_000,
) {
    private var totalMs = 0
    private var silenceMs = 0
    private var heardSpeech = false

    fun onFrame(isSpeech: Boolean, frameMs: Int): VadAction {
        totalMs += frameMs
        if (isSpeech) {
            heardSpeech = true
            silenceMs = 0
        } else {
            silenceMs += frameMs
        }
        return when {
            totalMs >= maxDurationMs -> VadAction.STOP
            heardSpeech && silenceMs >= silenceToStopMs -> VadAction.STOP
            !heardSpeech && silenceMs >= noSpeechTimeoutMs -> VadAction.STOP
            else -> VadAction.CONTINUE
        }
    }
}
