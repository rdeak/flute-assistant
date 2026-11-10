// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import kotlin.math.max
import kotlin.math.min

/**
 * Counts per [FrameDecision] and probability / level statistics since the last summary: the
 * body of the tuner's `tuner.summary` line and the flute's `flute.listen_summary`, the one
 * line that says, on a real phone, whether a child's tone passed the gate at all.
 */
internal class FrameSummary {
    var frames = 0
        private set
    private val counts = IntArray(FrameDecision.entries.size)
    private var pitched = 0
    private var pMin = 1.0
    private var pMax = 0.0
    private var pSum = 0.0
    private var levelSum = 0.0

    fun add(frame: PitchFrame, decision: FrameDecision) {
        frames++
        counts[decision.ordinal]++
        levelSum += frame.level
        if (frame.frequency != null) {
            pitched++
            pMin = min(pMin, frame.probability)
            pMax = max(pMax, frame.probability)
            pSum += frame.probability
        }
    }

    fun fields(): Array<Pair<String, Any?>> = arrayOf(
        "frames" to frames,
        "shown" to counts[FrameDecision.SHOWN.ordinal],
        "unsteady" to counts[FrameDecision.UNSTEADY.ordinal],
        "lowProb" to counts[FrameDecision.LOW_PROBABILITY.ordinal],
        "faint" to counts[FrameDecision.FAINT.ordinal],
        "silent" to counts[FrameDecision.SILENT.ordinal],
        "ignored" to counts[FrameDecision.IGNORED.ordinal],
        "pitched" to pitched,
        "pMin" to pMin.takeIf { pitched > 0 },
        "pMax" to pMax.takeIf { pitched > 0 },
        "pMean" to (pSum / pitched).takeIf { pitched > 0 },
        "levelMean" to (levelSum / frames).takeIf { frames > 0 },
    )

    fun reset() {
        frames = 0
        counts.fill(0)
        pitched = 0
        pMin = 1.0
        pMax = 0.0
        pSum = 0.0
        levelSum = 0.0
    }
}

/**
 * What the tuner did with one microphone frame; logged per frame when verbose and counted in the summary.
 * [FAINT] is the flute's alone: a clear pitch under [NoteOnsetTracker.MIN_LEVEL], too quiet to be the flute at
 * the phone (the tuner never decides it, so its summary's `faint` is 0).
 */
enum class FrameDecision { IGNORED, SILENT, LOW_PROBABILITY, FAINT, UNSTEADY, SHOWN }
