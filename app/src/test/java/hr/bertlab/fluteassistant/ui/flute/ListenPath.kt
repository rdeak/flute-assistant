// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.audio.tuner.FrameLevels
import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.audio.tuner.YinPitchDetector
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneCheck
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.ui.tuner.FrameDecision
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker

/**
 * "Your turn" on the JVM: frames audio as [AudioRecordTunerEngine] frames the microphone (a
 * [AudioRecordTunerEngine.WINDOW_SIZE] window advanced by [AudioRecordTunerEngine.HOP_SIZE],
 * zero-filled before the first hop) and runs the real [YinPitchDetector], a [NoteOnsetTracker]
 * with the given [NoteOnsetTracker.Rules] and room floor, and a [TuneCheck] over the tune, judging
 * an onset only while the check still waits on a note, as [FluteViewModel] does. The synthetic
 * cases of [ListenPathTest] and the replay of a recorded session (`debug/session`) both run on it.
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
        rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules(),
        floor: Float? = null,
    ): Run {
        val window = AudioRecordTunerEngine.WINDOW_SIZE
        val hop = AudioRecordTunerEngine.HOP_SIZE
        val detector = YinPitchDetector.forFlute(rate, bufferSize = window)
        val tracker = NoteOnsetTracker(rules = rules, floor = floor)
        val check = TuneCheck(tune)
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
            val pitch = PitchFrame(estimate?.frequency, estimate?.probability ?: 0.0, FrameLevels.level(w), FrameLevels.minLevel(w, hop))
            val result = tracker.onFrame(pitch)
            val waited = check.index
            val verdict = result.onset?.let { onset ->
                heard += Heard(waited, onset, frame * hop * 1000L / rate)
                if (waited != null) check.judge(onset.midi, onset.fresh) else null
            }
            steps += Step(
                frame, pitch, result.decision, tracker.sounding, result.onset, tracker.floor, verdict,
                waited, waited?.let { (tune[it] as TuneNote).midi },
            )
        }
        return Run(steps, heard, check.marks)
    }
}
