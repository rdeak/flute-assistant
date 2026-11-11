// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.audio.tuner.FrameLevels
import hr.bertlab.fluteassistant.audio.tuner.YinPitchDetector
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame

/**
 * "Your turn" on the JVM: frames audio as [AudioRecordTunerEngine] frames the microphone (a
 * [AudioRecordTunerEngine.WINDOW_SIZE] window advanced by [AudioRecordTunerEngine.HOP_SIZE],
 * zero-filled before the first hop) and runs the real [YinPitchDetector] and the [ListenRun]
 * the app runs, at the given [Pulse] with the given [NoteOnsetTracker.Rules], room floor and
 * length slack ([ListenRun.LENGTH_OFF]: pitch only, as before 2026-11-11), on past the end of the
 * tune, where a note is still heard but no longer judged. The synthetic cases of
 * [ListenPathTest] and the replay of a recorded session (`debug/session`) both run on it.
 */
object ListenPath {

    /** What one hop became: the frame, the tracker's decision, sounding note and onset, the floor after it, the verdict if judged, and the step waited on before it. */
    data class Step(
        val frame: Int,
        val pitch: PitchFrame,
        val decision: FrameDecision,
        val sounding: Int?,
        val onset: NoteOnsetTracker.Onset?,
        val floor: Float?,
        val verdict: Verdict?,
        val waited: Int?,
        val expected: Int?,
    )

    /** A note that began: the step it was judged against (null once the tune was done), the onset, and when. */
    data class Heard(val index: Int?, val onset: NoteOnsetTracker.Onset, val atMs: Long)

    class Run(val steps: List<Step>, val heard: List<Heard>, val marks: Map<Int, StepMark>)

    fun run(
        samples: FloatArray,
        rate: Int,
        tune: List<TuneItem>,
        pulse: Pulse,
        rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules(),
        floor: Float? = null,
        lengthSlack: Double = ListenRun.LENGTH_SLACK,
        maxNeedMs: Double = ListenRun.MAX_NEED_MS,
    ): Run {
        val window = AudioRecordTunerEngine.WINDOW_SIZE
        val hop = AudioRecordTunerEngine.HOP_SIZE
        val detector = YinPitchDetector.forFlute(rate, bufferSize = window)
        val listen = ListenRun(tune, pulse, rules, floor, lengthSlack, maxNeedMs)
        val w = FloatArray(window)
        val steps = mutableListOf<Step>()
        val heard = mutableListOf<Heard>()
        var pos = 0
        var frame = 0
        while (pos + hop <= samples.size) {
            System.arraycopy(w, hop, w, 0, window - hop)
            System.arraycopy(samples, pos, w, window - hop, hop)
            pos += hop
            frame++
            val estimate = detector.detect(w)
            val pitch = PitchFrame(estimate?.frequency, estimate?.probability ?: 0.0, FrameLevels.level(w), FrameLevels.minLevel(w, hop), hop * 1000.0 / rate)
            val row = listen.onFrame(pitch).row
            row.onset?.let { heard += Heard(row.waited, it, frame * hop * 1000L / rate) }
            steps += Step(frame, pitch, row.decision, row.sounding, row.onset, row.floor, row.verdict, row.waited, row.expected)
        }
        return Run(steps, heard, listen.marks)
    }
}
