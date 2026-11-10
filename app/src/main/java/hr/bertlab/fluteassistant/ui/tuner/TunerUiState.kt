// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

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
                abs(cents - 1200.0) <= ListenRules.NEAR_CENTS -> TargetMismatch.OCTAVE_UP
                abs(cents + 1200.0) <= ListenRules.NEAR_CENTS -> TargetMismatch.OCTAVE_DOWN
                else -> TargetMismatch.OTHER_NOTE
            }
        }

    /**
     * The one line of advice under the status line, derived so the composable only maps an
     * enum to a string. A reading wins over [hearing], and the reference tone wins over both.
     */
    val advice: TunerAdvice
        get() = when {
            isPlayingReference -> TunerAdvice.NONE
            status == TuningStatus.NONE -> when (hearing) {
                Hearing.CLEAR -> TunerAdvice.NONE
                Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
                Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
            }
            status == TuningStatus.IN_TUNE -> TunerAdvice.NONE
            else -> when (mismatch) {
                TargetMismatch.OCTAVE_DOWN -> TunerAdvice.FASTER_AIR
                TargetMismatch.OCTAVE_UP -> TunerAdvice.GENTLER_AIR
                TargetMismatch.OTHER_NOTE -> TunerAdvice.CHECK_FINGERS
                TargetMismatch.NONE ->
                    if (status == TuningStatus.LOW) TunerAdvice.ROLL_OUT else TunerAdvice.ROLL_IN
            }
        }

    companion object {
        /**
         * In tune, shared by the status line and the pitch window (the sound bubble sits in its
         * home ring exactly then). A child's held note wobbles about this much from breath
         * alone, so a professional's 5 cents would rarely settle on "Perfect!".
         */
        const val IN_TUNE_CENTS = 10.0
    }
}

/** What the tuner tells the child: nothing heard, in tune, or which way the note must go. */
enum class TuningStatus { NONE, IN_TUNE, LOW, HIGH }

/** Whether the heard note in target mode is the chosen one, its octave, or another note altogether. */
enum class TargetMismatch { NONE, OCTAVE_UP, OCTAVE_DOWN, OTHER_NOTE }
