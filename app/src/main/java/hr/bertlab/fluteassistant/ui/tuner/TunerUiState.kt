// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.runtime.Immutable
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.NoteReading
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import kotlin.math.abs

/** Immutable snapshot of everything the tuner screen renders. */
data class TunerUiState(
    val permissionGranted: Boolean = false,
    /** True once the system permission dialog has been shown, so it is not re-shown on every visit. */
    val permissionRequested: Boolean = false,
    /** True once the phone has said no and will not ask again: only then does the card offer Settings. */
    val micBlocked: Boolean = false,
    val isListening: Boolean = false,
    /** Smoothed reading of the note being played, or null during silence. */
    val reading: NoteReading? = null,
    /** The note the child chose to tune, as a MIDI number inside [FluteRange]; used in [TunerMode.TARGET]. */
    val targetMidi: Int = FluteRange.DEFAULT_TARGET,
    val mode: TunerMode = TunerMode.IDENTIFY,
    /** True while the reference tone for [targetMidi] sounds; readings pause so the speaker is not measured. */
    val isPlayingReference: Boolean = false,
    /**
     * In [TunerMode.IDENTIFY], the note whose home ring is shown while a note is heard: the
     * reading's own note, except that [TunerViewModel] holds it through a drift just past the
     * halfway point to a neighbouring note until the sound has stayed there for
     * [ListenRules.SWITCH_FRAMES], so the ring never flickers between two notes. Null means
     * the reading's own note.
     */
    val identifiedMidi: Int? = null,
    /** What the microphone makes of sound that yields no readable note; see [Hearing]. */
    val hearing: Hearing = Hearing.CLEAR,
    /** The microphone would not open or stopped: what the child sees is a string resource, the cause is in the log. */
    val micFailed: Boolean = false,
    /**
     * The card of the guide to tuning the flute on screen in the tab's place, or null when the guide
     * is closed. Kept here, not in the screen, so a turn and a tab switch keep it, and so opening and
     * leaving it are logged (`tuner.guide`).
     */
    val guideCard: GuideCard? = null,
    /**
     * A fix on the flute the line still says in the silence after it, or [TunerAdvice.NONE]: `FixHold` decides it and
     * [TunerViewModel] feeds it. Kept here, not in the screen, so a turn keeps it.
     */
    val heldFix: TunerAdvice = TunerAdvice.NONE,
) {
    /** Distance in cents from [targetMidi] to the played note (positive = sharp), or null in silence. */
    val centsOffTarget: Double?
        get() = reading?.let { NoteMath.centsBetween(it.frequency, targetMidi) }

    /**
     * The note the pitch window's home ring stands for: the chosen note when tuning to one; when
     * identifying, the identified note while a note is heard and null (a grey ring) in silence.
     */
    val homeMidi: Int?
        get() = when (mode) {
            TunerMode.TARGET -> targetMidi
            TunerMode.IDENTIFY -> reading?.let { identifiedMidi ?: it.midi }
        }

    /** Whether the tab sounds or hears a note right now: the phone stays awake while it does (`KeepScreenAwake`). */
    val isBusy: Boolean get() = isPlayingReference || reading != null

    /** What the pitch window and the status measure: cents from [homeMidi] (positive = sharp), or null in silence. */
    val gaugeCents: Double?
        get() {
            val heard = reading ?: return null
            val home = homeMidi ?: return null
            return NoteMath.centsBetween(heard.frequency, home)
        }

    /**
     * The note in the pitch window's sound bubble, null in silence: the home note, or when
     * tuning to a chosen note another note or octave the child actually played ([mismatch]).
     */
    val heardMidi: Int?
        get() = reading?.let { if (mismatch == TargetMismatch.NONE) homeMidi else it.midi }

    /** Whether [gaugeCents] counts as in tune, and if not which way to go; [TuningStatus.NONE] in silence. */
    val status: TuningStatus
        get() {
            val cents = gaugeCents ?: return TuningStatus.NONE
            return when {
                abs(cents) <= IN_TUNE_CENTS -> TuningStatus.IN_TUNE
                cents < 0 -> TuningStatus.LOW
                else -> TuningStatus.HIGH
            }
        }

    /**
     * In [TunerMode.TARGET], how the heard note relates to the chosen one: the octave above
     * or below (a beginner's overblown or underblown note, cured by air speed rather than
     * fingers) is told apart from any other note. [TargetMismatch.NONE] when it is the chosen
     * note however sharp or flat (anything within [ListenRules.NEAR_CENTS] of it, even past the
     * halfway point to the neighbouring semitone), in silence, and when identifying. The same
     * band applies around the octave above and below (1200 cents), so an octave that is also
     * flat or sharp is still the octave.
     */
    val mismatch: TargetMismatch
        get() {
            if (mode != TunerMode.TARGET) return TargetMismatch.NONE
            val cents = centsOffTarget ?: return TargetMismatch.NONE
            return when {
                abs(cents) <= ListenRules.NEAR_CENTS -> TargetMismatch.NONE
                abs(cents - OCTAVE_CENTS) <= ListenRules.NEAR_CENTS -> TargetMismatch.OCTAVE_UP
                abs(cents + OCTAVE_CENTS) <= ListenRules.NEAR_CENTS -> TargetMismatch.OCTAVE_DOWN
                else -> TargetMismatch.OTHER_NOTE
            }
        }

    /**
     * What to change, which the tuner's one line says when there is something ([line]). A reading wins over
     * [hearing], and the reference tone wins over both.
     * A flat or sharp note gets rolling, except when the child tunes on [FluteRange.TUNING_NOTE]:
     * the head joint is set once, on that note, and every other note drifts by itself and is
     * fixed with the lips, so only there does it name the head joint.
     */
    val advice: TunerAdvice
        get() {
            if (isPlayingReference) return TunerAdvice.NONE
            return when (val status = status) {
                TuningStatus.NONE -> when (hearing) {
                    Hearing.CLEAR -> TunerAdvice.NONE
                    Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
                    Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
                }
                TuningStatus.IN_TUNE -> TunerAdvice.NONE
                TuningStatus.LOW, TuningStatus.HIGH -> when (mismatch) {
                    TargetMismatch.OCTAVE_DOWN -> TunerAdvice.FASTER_AIR
                    TargetMismatch.OCTAVE_UP -> TunerAdvice.GENTLER_AIR
                    TargetMismatch.OTHER_NOTE -> TunerAdvice.CHECK_FINGERS
                    TargetMismatch.NONE -> {
                        val low = status == TuningStatus.LOW
                        if (isTuningNote) {
                            if (low) TunerAdvice.HEAD_JOINT_IN else TunerAdvice.HEAD_JOINT_OUT
                        } else {
                            if (low) TunerAdvice.ROLL_OUT else TunerAdvice.ROLL_IN
                        }
                    }
                }
            }
        }

    /**
     * Whether the flute's pressed keys are in the note's colour (since 2026-11-18, the calmer Tune view): while it helps,
     * before a sound (how to play the note) and when another note or octave is heard; grey (a quiet one, readable at night too) while the right note sounds,
     * flat, sharp or in tune, so the eyes go to the window. In Which? nothing is ever another note, so it is lit only in
     * silence, where it has no note to show.
     */
    val fingeringLit: Boolean
        get() = status == TuningStatus.NONE || mismatch != TargetMismatch.NONE

    /**
     * Whether a tap on the window plays the chosen note (since 2026-11-18, when the Hear button went): in Tune mode, in
     * silence only. Not while the note already plays, and not while the child plays, as a finger on the bubble would
     * start the note and blank the reading for its two seconds (the user's call, the same day).
     */
    val tapPlaysNote: Boolean
        get() = mode == TunerMode.TARGET && !isPlayingReference && reading == null

    /**
     * Plain silence: nothing heard, nothing to say about the sound, no note playing and the microphone fine. Only it
     * keeps a fix on the line ([heldFix]).
     */
    val isPlainSilence: Boolean
        get() = status == TuningStatus.NONE && advice == TunerAdvice.NONE && !isPlayingReference && !micFailed

    /**
     * What the tuner's one line says, the first of these that holds: the microphone failed; what to change, as a flat
     * or sharp reading always carries [advice] (so "A little higher" and "That's E4" are never on the line: the
     * window's bubble and arrow say up or down and name another note); listen, while the chosen note plays; the cheer
     * in tune; a fix on the flute kept through the silence after it ([heldFix]); else the prompt to play.
     */
    val line: TunerLine
        get() = when {
            micFailed -> TunerLine.MicFailed
            advice != TunerAdvice.NONE -> TunerLine.Advice(advice)
            isPlayingReference -> TunerLine.Listen
            status == TuningStatus.IN_TUNE -> TunerLine.InTune
            heldFix != TunerAdvice.NONE -> TunerLine.Advice(heldFix)
            mode == TunerMode.TARGET -> TunerLine.PlayNote(targetMidi)
            else -> TunerLine.PlayAnyNote
        }

    /** Tuning the flute itself: Tune mode with the tuning note chosen. */
    private val isTuningNote: Boolean
        get() = mode == TunerMode.TARGET && targetMidi == FluteRange.TUNING_NOTE

    companion object {
        /**
         * In tune, shared by the status line and the pitch window (the sound bubble sits in its
         * home ring exactly then). A child's held note wobbles about this much from breath
         * alone, so a professional's 5 cents would rarely settle on "Perfect!".
         */
        const val IN_TUNE_CENTS = 10.0

        /** An octave, in cents. */
        private const val OCTAVE_CENTS = 1200.0
    }
}

/** What the tuner tells the child: nothing heard, in tune, or which way the note must go. */
enum class TuningStatus { NONE, IN_TUNE, LOW, HIGH }

/** Whether the heard note in target mode is the chosen one, its octave, or another note altogether. */
enum class TargetMismatch { NONE, OCTAVE_UP, OCTAVE_DOWN, OTHER_NOTE }

/**
 * What the tuner's one line says ([TunerUiState.line]): the screen only turns it into words. Immutable, so the line is
 * drawn again when what it says changes and not on every frame the microphone hears.
 */
@Immutable
sealed interface TunerLine {
    /** The microphone would not open or stopped. */
    data object MicFailed : TunerLine

    /** What to change. A fix on the flute ([TunerAdvice.isFluteFix]) opens the guide when tapped. */
    data class Advice(val advice: TunerAdvice) : TunerLine

    /** The chosen note is playing. */
    data object Listen : TunerLine

    data object InTune : TunerLine

    /** Silence in Tune mode: play the chosen note, [midi]. */
    data class PlayNote(val midi: Int) : TunerLine

    /** Silence in Which? mode: play any note. */
    data object PlayAnyNote : TunerLine
}
