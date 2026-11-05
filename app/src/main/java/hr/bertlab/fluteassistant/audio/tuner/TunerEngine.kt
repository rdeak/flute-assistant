package hr.bertlab.fluteassistant.audio.tuner

import kotlinx.coroutines.flow.Flow

/**
 * One analysis window of microphone input.
 * @property frequency detected fundamental in Hz, or null when nothing pitched was found.
 * @property probability confidence in [0, 1] (0 when [frequency] is null).
 * @property level input level in [0, 1] for a simple meter ([FrameLevels.level]).
 * @property minLevel the quietest short stretch inside the newest hop ([FrameLevels.minLevel]),
 * which a tongued stop between two notes of one pitch drops where [level] barely moves; the
 * window's [level] when nothing finer is known.
 */
data class PitchFrame(val frequency: Double?, val probability: Double, val level: Float, val minLevel: Float = level)

/** Microphone pitch-tracking engine. */
interface TunerEngine {
    /**
     * A cold flow: collecting it opens the microphone and emits a [PitchFrame] roughly every
     * 40 ms, every frame in order, buffered rather than conflated, because the flute's "Your turn"
     * counts frames (a gap is two in a row) and a busy main thread must not lose it one;
     * cancelling the collector releases the microphone.
     * Fails with [SecurityException] when RECORD_AUDIO has not been granted.
     */
    fun pitchFrames(): Flow<PitchFrame>
}
