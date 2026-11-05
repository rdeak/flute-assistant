package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.tuner.FrameLevels
import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.audio.tuner.YinPitchDetector
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneCheck
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * "Your turn" end to end on synthetic sound: a flute-like tone in a room, framed as
 * `AudioRecordTunerEngine` frames the microphone (48 kHz, a 4096 window advanced by 2048), run
 * through the real [YinPitchDetector], [NoteOnsetTracker] and [TuneCheck]. These are the cases
 * a phone test with a real flute found or would find (2026-11-04: a child's "h1 h1 long h1" lost
 * its tongued repeat, so the long note was counted as the second and the run waited for a third);
 * the emulator has no microphone, so this is where the whole path is checked.
 */
class ListenPathTest {

    private val rate = 48_000
    private val window = 4096
    private val hop = 2048

    /** The tune written: three b1 / B4, the last one long. */
    private val tune = listOf(TuneNote(71, NoteLength.QUARTER), TuneNote(71, NoteLength.QUARTER), TuneNote(71, NoteLength.HALF))

    private data class Heard(val index: Int?, val onset: NoteOnsetTracker.Onset, val atMs: Long)

    private class Run(val heard: List<Heard>, val marks: Map<Int, StepMark>)

    @Test
    fun `three repeated notes with tongued gaps of 50 ms are all counted`() {
        // "ta ta taaa": the tongue stops the air for about 50 ms between notes of the same pitch.
        val run = listen(sound(gapsMs = 50, roomDb = -10.0, floorDb = -50.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `the quickest tongue, 40 ms, is still heard in a lively room`() {
        val run = listen(sound(gapsMs = 40, roomDb = -6.0, rt60 = 0.6, floorDb = -42.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `a repeat played softer than the note before it is still counted`() {
        val run = listen(sound(gapsMs = 50, secondDb = -30.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `a tone wobbling two decibels is not heard as notes started again`() {
        val run = listen(sound(gapsMs = 400, wobbleDb = 2.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    @Test
    fun `a long note fading away as the breath runs out is one note`() {
        val run = listen(sound(gapsMs = 400, fadeDb = -14.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    @Test
    fun `three repeated notes with breath-sized pauses are all counted in a lively room`() {
        val run = listen(sound(gapsMs = 400, roomDb = -6.0, rt60 = 0.6, floorDb = -46.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `notes not far over the room's floor are all counted after a short pause back at the floor`() {
        // A note at -22 dBFS over a room at -38: half the note's level (-41 dBFS) lies under the
        // floor, so a pause of 400 ms (too short for the hold count) is a gap only by the floor.
        val run = listen(sound(gapsMs = 400, toneDb = -22.0, floorDb = -38.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `notes parted by long noisy breaths are all counted once the hold count runs out`() {
        // The child inhales loudly between notes: the pauses stay over half the note's level and
        // over the floor the quiet lead-in taught, so only their length ends the note.
        val run = listen(sound(gapsMs = 1500, toneDb = -22.0, floorDb = -46.0, pauseDb = -38.0))
        assertEquals(run.heard.toString(), mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), run.marks)
        assertEquals(3, run.heard.size)
    }

    @Test
    fun `one long note sagging flat as the breath runs out is one note`() {
        val run = listen(sound(gapsMs = 400, sagCents = -60.0, floorDb = -50.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    @Test
    fun `a held note the gate blinks on is one note`() {
        // Breath noise over the tone for two frames: the pitch is lost but the loudness stays.
        val run = listen(sound(gapsMs = 400, blinkDb = -24.0, floorDb = -50.0))
        assertEquals(run.heard.toString(), 3, run.heard.size)
    }

    // --- the child's sound ---

    /**
     * Three B4s ([tune]), the first two 400 ms, the third 1500 ms, [gapsMs] of silence between
     * them, in a room: a tone at [toneDb], a reverberant copy [roomDb] under it decaying over
     * [rt60] seconds, and breath-like noise at [floorDb] (RMS), louder at [pauseDb] in the gaps
     * when given (a noisy inhale). The second note is at [secondDb] when given. Every note
     * wobbles by [wobbleDb] either way at 5 Hz; the last note sags by [sagCents] and fades by
     * [fadeDb] over its length; a burst of noise at [blinkDb] covers two frames in the middle of
     * the first note.
     */
    private fun sound(
        gapsMs: Int,
        toneDb: Double = -22.0,
        secondDb: Double = toneDb,
        roomDb: Double = -10.0,
        rt60: Double = 0.4,
        floorDb: Double = -50.0,
        pauseDb: Double = floorDb,
        wobbleDb: Double = 0.0,
        sagCents: Double = 0.0,
        fadeDb: Double = 0.0,
        blinkDb: Double? = null,
    ): DoubleArray {
        val leadMs = 500 // the room alone, as the child picks up the flute
        val noteMs = intArrayOf(400, 400, 1500)
        val total = (leadMs + noteMs.sum() + 2 * gapsMs + 400) * rate / 1000
        val dry = DoubleArray(total)
        val starts = IntArray(3)
        var at = leadMs * rate / 1000
        for ((n, ms) in noteMs.withIndex()) {
            starts[n] = at
            val db = if (n == 1) secondDb else toneDb
            note(dry, at, ms, 10.0.pow(db / 20.0), wobbleDb, if (n == 2) sagCents else 0.0, if (n == 2) fadeDb else 0.0)
            at += (ms + gapsMs) * rate / 1000
        }
        val wet = reverb(dry, roomDb, rt60)
        val noise = Random(7)
        var lp = 0.0
        // The lowpass below leaves the noise at 0.229 RMS, so this scale gives the dB asked for.
        fun rms(db: Double) = 10.0.pow(db / 20.0) / 0.229
        val floor = rms(floorDb)
        val pause = rms(pauseDb)
        val blink = blinkDb?.let(::rms)
        val blinkFrom = starts[0] + 180 * rate / 1000
        val blinkTo = blinkFrom + 2 * hop
        val firstGap = starts[0] + noteMs[0] * rate / 1000 until starts[1]
        val secondGap = starts[1] + noteMs[1] * rate / 1000 until starts[2]
        return DoubleArray(total) { i ->
            lp = 0.9 * lp + 0.1 * noise.nextGaussian() // lowpassed: breath, not hiss
            val a = when {
                blink != null && i in blinkFrom until blinkTo -> blink
                i in firstGap || i in secondGap -> pause
                else -> floor
            }
            wet[i] + a * lp
        }
    }

    /**
     * A flute-ish tone: the fundamental with weaker harmonics, a 30 ms swell, a 10 ms stop,
     * wobbling [wobbleDb] either way at 5 Hz, drifting by [sagCents] and fading by [fadeDb] over
     * its length.
     */
    private fun note(out: DoubleArray, at: Int, ms: Int, amp: Double, wobbleDb: Double, sagCents: Double, fadeDb: Double) {
        val n = ms * rate / 1000
        var phase = 0.0
        for (i in 0 until n) {
            val f = NoteMath.midiToFrequency(71) * 2.0.pow(sagCents * i / n / 1200.0)
            phase += 2 * PI * f / rate
            val env = min(1.0, i / (0.030 * rate)) * min(1.0, (n - i) / (0.010 * rate))
            val loudness = 10.0.pow((wobbleDb * sin(2 * PI * 5 * i / rate) + fadeDb * i / n) / 20.0)
            val v = sin(phase) + 0.32 * sin(2 * phase) + 0.13 * sin(3 * phase) + 0.05 * sin(4 * phase)
            if (at + i < out.size) out[at + i] += amp * env * loudness * v / 1.5
        }
    }

    /** The room: early reflections and a feedback comb [roomDb] under the direct sound, decaying to -60 dB in [rt60] seconds. */
    private fun reverb(dry: DoubleArray, roomDb: Double, rt60: Double): DoubleArray {
        val out = dry.copyOf()
        val gain = 10.0.pow(roomDb / 20.0)
        val delays = intArrayOf(11, 17, 23, 31, 41).map { it * rate / 1000 }
        for (d in delays) {
            val feedback = 10.0.pow(-3.0 * d / rate / rt60)
            for (i in d until out.size) out[i] += gain / delays.size * dry[i - d] + feedback * (out[i - d] - dry[i - d]) / delays.size
        }
        return out
    }

    // --- the phone ---

    /** Frames [samples] as the engine does and runs the flute's listening path over them. */
    private fun listen(samples: DoubleArray): Run {
        val detector = YinPitchDetector.forFlute(rate, bufferSize = window)
        val tracker = NoteOnsetTracker()
        val check = TuneCheck(tune)
        val w = FloatArray(window)
        val heard = mutableListOf<Heard>()
        var pos = 0
        var frames = 0
        while (pos + hop <= samples.size) {
            System.arraycopy(w, hop, w, 0, window - hop)
            for (i in 0 until hop) w[window - hop + i] = samples[pos + i].toFloat()
            pos += hop
            frames++
            val estimate = detector.detect(w)
            val frame = PitchFrame(estimate?.frequency, estimate?.probability ?: 0.0, FrameLevels.level(w), FrameLevels.minLevel(w, hop))
            tracker.onFrame(frame).onset?.let { onset ->
                heard += Heard(check.index, onset, frames * hop * 1000L / rate)
                check.judge(onset.midi, onset.fresh)
            }
        }
        return Run(heard, check.marks)
    }
}
