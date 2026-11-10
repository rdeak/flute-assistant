// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.abs
import kotlin.math.log2

/**
 * Turns microphone frames into notes the way the tuner's "Which?" mode does, and says when a
 * *new* note begins, which is what the flute's "Your turn" judges. Pure Kotlin, one frame at a
 * time, with the tuner's own thresholds: a frame counts only with a frequency and a probability
 * of at least [ListenRules.MIN_PROBABILITY]; a note is sounding once the last
 * [ListenRules.LOCK_FRAMES] counted frames agree within [ListenRules.LOCK_CENTS]; the median
 * of [ListenRules.SMOOTHING_WINDOW] frames names it; a sound that has only drifted past the
 * halfway point to a neighbouring note keeps the old note until it has lasted
 * [ListenRules.SWITCH_FRAMES], while a clear step (further than
 * [ListenRules.NEAR_CENTS]) switches at once.
 *
 * A new note begins when a note sounds after a gap, when the sounding note changes to another
 * by a clear step without a gap (a slur, a fingered change), or when the same note starts again
 * after a tongued stop ([noticeStop]). A gap is [ONSET_GAP_FRAMES] frames in a row that did not
 * count *and* lost the note's loudness (under [GAP_LEVEL_RATIO] of the note's mean level, or back
 * at the room's own level, [FLOOR_MARGIN] over the [floor] heard between notes): silence, a
 * breath. A frame that lost its pitch but kept its loudness is the probability gate blinking on a
 * breathy tone, not a gap; counting it would split one held note in two and count a written
 * repeat as played; but [ListenRules.HOLD_FRAMES] uncounted frames in a row are a pause at
 * any level. After a gap the window is emptied, so the next note needs fresh agreeing frames. A
 * tongued stop between two notes of one pitch (40 to 80 ms) is too short for any of that, the
 * window's level barely moving and the pitch often kept, so it is heard in the frame's
 * [PitchFrame.minLevel], the quietest short stretch of the newest hop: a stretch [STOP_DROP]
 * under the note's recent level is a stop, and the tone back [RESTART_RISE] over the stop's
 * floor within [RESTART_FRAMES] frames is the note started again, a fresh [Onset] of whatever
 * pitch locks then. The same note merely going on is not a new note; a slow drift past the
 * halfway point to a neighbour (a long note sagging as the breath runs out) moves the sounding
 * note, as the tuner's ring would move, but is no new note either: nobody played one. Unsteady
 * frames do not count towards the gap: a wobble inside a held note must not make it two.
 */
class NoteOnsetTracker(
    val rules: Rules = Rules(),
    floor: Float? = null,
) {

    /**
     * Every threshold the tracker applies, as a value whose defaults are the constants: the app
     * builds the tracker with the defaults, and a replay of a recorded session (debug builds
     * record every "Your turn" run; `ui/flute/CLAUDE.md`) builds one with a rule changed to see
     * what it would have heard. [fields] renders them for the session's log at full precision,
     * [with] sets one by its field name.
     */
    data class Rules(
        val gate: Double = ListenRules.MIN_PROBABILITY,
        val lockFrames: Int = ListenRules.LOCK_FRAMES,
        val lockCents: Double = ListenRules.LOCK_CENTS,
        val smoothing: Int = ListenRules.SMOOTHING_WINDOW,
        val switchFrames: Int = ListenRules.SWITCH_FRAMES,
        val holdFrames: Int = ListenRules.HOLD_FRAMES,
        val nearCents: Double = ListenRules.NEAR_CENTS,
        val onsetGapFrames: Int = ONSET_GAP_FRAMES,
        val gapLevelRatio: Float = GAP_LEVEL_RATIO,
        val floorMargin: Float = FLOOR_MARGIN,
        val floorRise: Float = FLOOR_RISE,
        val recentFrames: Int = RECENT_FRAMES,
        val attackFrames: Int = ATTACK_FRAMES,
        val stopDrop: Float = STOP_DROP,
        val restartRise: Float = RESTART_RISE,
        val restartFrames: Int = RESTART_FRAMES,
    ) {
        /** Name and value of every rule, in declaration order, the value as Kotlin prints it. */
        fun fields(): List<Pair<String, String>> = listOf(
            "gate" to gate.toString(), "lockFrames" to lockFrames.toString(), "lockCents" to lockCents.toString(),
            "smoothing" to smoothing.toString(), "switchFrames" to switchFrames.toString(), "holdFrames" to holdFrames.toString(),
            "nearCents" to nearCents.toString(), "onsetGapFrames" to onsetGapFrames.toString(),
            "gapLevelRatio" to gapLevelRatio.toString(), "floorMargin" to floorMargin.toString(), "floorRise" to floorRise.toString(),
            "recentFrames" to recentFrames.toString(), "attackFrames" to attackFrames.toString(), "stopDrop" to stopDrop.toString(),
            "restartRise" to restartRise.toString(), "restartFrames" to restartFrames.toString(),
        )

        /** These rules with the one named [name] set to [value] (rounded for a frame count); an unknown name is refused. */
        fun with(name: String, value: Double): Rules {
            val n = Math.round(value).toInt()
            val f = value.toFloat()
            return when (name) {
                "gate" -> copy(gate = value)
                "lockFrames" -> copy(lockFrames = n)
                "lockCents" -> copy(lockCents = value)
                "smoothing" -> copy(smoothing = n)
                "switchFrames" -> copy(switchFrames = n)
                "holdFrames" -> copy(holdFrames = n)
                "nearCents" -> copy(nearCents = value)
                "onsetGapFrames" -> copy(onsetGapFrames = n)
                "gapLevelRatio" -> copy(gapLevelRatio = f)
                "floorMargin" -> copy(floorMargin = f)
                "floorRise" -> copy(floorRise = f)
                "recentFrames" -> copy(recentFrames = n)
                "attackFrames" -> copy(attackFrames = n)
                "stopDrop" -> copy(stopDrop = f)
                "restartRise" -> copy(restartRise = f)
                "restartFrames" -> copy(restartFrames = n)
                else -> throw IllegalArgumentException("no rule named $name")
            }
        }
    }

    /** What became of one frame, and the note that began on it, if one did. */
    data class Result(val decision: FrameDecision, val onset: Onset?)

    /**
     * A note that began: its MIDI, and whether it was a [fresh] start after a gap (nothing was
     * sounding) rather than a step from the note that was sounding without a gap (a slur, a
     * fingered change, an octave pop). A written repeat can only be played by a fresh start.
     */
    data class Onset(val midi: Int, val fresh: Boolean)

    /** The note sounding now, as the tuner's "Which?" ring would show it; null between notes. */
    var sounding: Int? = null
        private set

    private val recent = ArrayDeque<Double>()
    /** Frames in a row that did not count and were quiet enough; [ONSET_GAP_FRAMES] of them end the sounding note. */
    private var gap = 0
    /** Frames in a row that did not count, at any level; [ListenRules.HOLD_FRAMES] of them end the sounding note too. */
    private var unheard = 0
    /**
     * The room's own level: the lowest level of the frames that did not count while no note
     * sounded, rising by [FLOOR_RISE] a frame so a louder room is learned again within seconds;
     * null until the room has been heard. A note's frame that falls back to it is a gap, however
     * soft the note. It survives [reset], which ends a note, not the room, so it is also what a
     * run starts with from the run before; a replay seeds it through the constructor.
     */
    var floor: Float? = floor
        private set
    /** Shown frames in a row whose note is a neighbour of [sounding] within reach; see [identify]. */
    private var driftFrames = 0
    /** Whether the last [identify] moved [sounding] by a drift rather than a clear step or a fresh start. */
    private var drifted = false
    /** The mean level of the counted frames of the sounding note, which a gap must fall well under. */
    private var levelSum = 0.0
    private var levelCount = 0
    /** The levels of the last [RECENT_FRAMES] counted frames of the sounding note: what a stop must fall under. */
    private val recentLevels = ArrayDeque<Float>()
    /** Counted frames since the sounding note began (0 between notes); a stop in its first ones is its attack. */
    var framesSinceOnset = 0
        private set
    /** Frames since a stop was heard inside the sounding note, or -1 when none is pending. */
    private var stopAge = -1
    /** The quietest stretch of the stop pending, which the tone must rise [RESTART_RISE] over. */
    private var stopFloor = 0f

    fun onFrame(frame: PitchFrame): Result {
        noticeStop(frame)
        val frequency = frame.frequency ?: return uncounted(FrameDecision.SILENT, frame.level)
        if (frame.probability < rules.gate) return uncounted(FrameDecision.LOW_PROBABILITY, frame.level)
        gap = 0
        unheard = 0
        levelSum += frame.level
        levelCount++
        recentLevels.addLast(frame.level)
        while (recentLevels.size > rules.recentFrames) recentLevels.removeFirst()
        if (sounding != null) framesSinceOnset++
        recent.addLast(frequency)
        while (recent.size > rules.smoothing) recent.removeFirst()
        if (!isSteady()) return Result(FrameDecision.UNSTEADY, onset = null).also { ageStop() }
        val median = recent.sorted()[recent.size / 2]
        val before = sounding
        val identified = identify(NoteMath.frequencyToNote(median).midi, median)
        // The tone is back after a stop: the note started again, whatever pitch locked.
        val restarted = stopAge in 1..rules.restartFrames && frame.minLevel >= stopFloor + rules.restartRise
        val onset = when {
            identified != before && !drifted -> Onset(identified, fresh = before == null || restarted)
            identified == before && restarted -> Onset(identified, fresh = true)
            else -> null
        }
        if (onset != null) {
            // The new note's loudness is its own; the frames that locked it are its first.
            levelSum = frame.level.toDouble()
            levelCount = 1
            recentLevels.clear()
            recentLevels.addLast(frame.level)
            framesSinceOnset = 0
            stopAge = -1
        }
        sounding = identified
        ageStop()
        return Result(FrameDecision.SHOWN, onset)
    }

    /** Back to silence: no note sounding, nothing remembered of it (the room's floor is kept). */
    fun reset() {
        sounding = null
        recent.clear()
        gap = 0
        unheard = 0
        driftFrames = 0
        drifted = false
        levelSum = 0.0
        levelCount = 0
        recentLevels.clear()
        framesSinceOnset = 0
        stopAge = -1
    }

    /**
     * Hears a tongued stop inside the sounding note: the quietest stretch of this frame's hop
     * ([PitchFrame.minLevel]) fell [STOP_DROP] under the note's recent level. Not in the note's
     * first frames, whose hops still hold its attack. A stop already pending only deepens.
     */
    private fun noticeStop(frame: PitchFrame) {
        if (sounding == null || framesSinceOnset < rules.attackFrames || recentLevels.isEmpty()) return
        val recentMean = recentLevels.sum() / recentLevels.size
        if (frame.minLevel < recentMean - rules.stopDrop) {
            stopFloor = if (stopAge < 0) frame.minLevel else minOf(stopFloor, frame.minLevel)
            stopAge = 0
        }
    }

    /** A frame has passed since the stop; past [RESTART_FRAMES] it was the end of the note, not a stop in it. */
    private fun ageStop() {
        if (stopAge >= 0 && ++stopAge > rules.restartFrames) stopAge = -1
    }

    /**
     * A frame that did not count. While a note sounds it counts towards the gap only when its
     * [level] fell well under the note's ([GAP_LEVEL_RATIO]) or back to the room's [floor]
     * (within [FLOOR_MARGIN]); between notes every such frame counts, and teaches the floor.
     * [ListenRules.HOLD_FRAMES] such frames in a row end the note at any level: that long is
     * a pause, not a blink, however soft the note was over the room.
     */
    private fun uncounted(decision: FrameDecision, level: Float): Result {
        val note = sounding
        if (note == null) {
            floor = floor?.let { minOf(level, it + rules.floorRise) } ?: level
        }
        val quiet = note == null || levelCount == 0 ||
            level < (levelSum / levelCount) * rules.gapLevelRatio ||
            floor?.let { level <= it + rules.floorMargin } == true
        if ((quiet && ++gap >= rules.onsetGapFrames) || ++unheard >= rules.holdFrames) reset()
        ageStop()
        return Result(decision, onset = null)
    }

    /** True once the last [ListenRules.LOCK_FRAMES] frames agree within [ListenRules.LOCK_CENTS]. */
    private fun isSteady(): Boolean {
        if (recent.size < rules.lockFrames) return false
        val last = recent.takeLast(rules.lockFrames)
        return 1200.0 * log2(last.max() / last.min()) <= rules.lockCents
    }

    /**
     * The note [heard] (the median's nearest note) becomes the sounding note at once when nothing
     * was sounding, when it is the sounding note, or when it is a clear step away; a drift that has
     * only passed the halfway point to a neighbour waits for [ListenRules.SWITCH_FRAMES] shown
     * frames in a row, one frame back on the sounding note starting the count again.
     */
    private fun identify(heard: Int, median: Double): Int {
        val current = sounding
        drifted = false
        if (current == null || heard == current) {
            driftFrames = 0
            return heard
        }
        val reach = abs(NoteMath.centsBetween(median, current))
        if (reach > rules.nearCents) {
            driftFrames = 0
            return heard
        }
        if (++driftFrames >= rules.switchFrames) {
            driftFrames = 0
            drifted = true
            return heard
        }
        return current
    }

    companion object {
        /**
         * Quiet frames in a row that did not count (about 90 ms) after which the next note is a new
         * one. One frame is too few: a single dropped frame inside a held note would split it in two.
         */
        const val ONSET_GAP_FRAMES = 2

        /**
         * While a note sounds, an uncounted frame is quiet enough to count towards the gap only
         * under this share of the note's mean level (the level is the engine's 0..1 dB-style
         * scale, so half of a note at -20 dBFS is about -40 dBFS, a 20 dB drop). A tongued gap
         * falls to the room's noise; the gate blinking on a breathy tone keeps the note's level.
         * A starting value, to be read against `flute.heard` and `flute.listen_summary` on a phone.
         */
        const val GAP_LEVEL_RATIO = 0.5f

        /**
         * How far over the room's floor an uncounted frame may be and still count as a gap: 0.05,
         * about 3 dB on the level's scale (0.25 is about -45 dBFS, 0.5 about -30). Half the level
         * of a soft note (0.4, about -36 dBFS) lies under a quiet room's floor (0.23, about
         * -46 dBFS), so by [GAP_LEVEL_RATIO] alone such a note would never end and its repeat
         * never count; a frame back at the floor is a gap for any note the gate could hear at all.
         */
        const val FLOOR_MARGIN = 0.05f

        /**
         * How much the learned floor rises per frame that did not count between notes (about
         * 3 dB a second): the floor tracks the quietest the room gets, and a room that got louder
         * is learned again within seconds rather than never.
         */
        const val FLOOR_RISE = 0.002f

        /** Counted frames whose levels make the note's recent level, which a stop must fall under: about 200 ms. */
        const val RECENT_FRAMES = 5

        /**
         * Counted frames after the onset before a stop can be heard: the onset frame's hop and
         * the next still hold the note's attack, whose swell from nothing looks like a stop.
         */
        const val ATTACK_FRAMES = 1

        /**
         * How far under the note's recent level the quietest stretch of a hop must fall to be a
         * stop: 0.12, about 7 dB. A beginner's breathy tone wobbles by a decibel or two over 21 ms;
         * a tongued stop falls 10 dB or more even with the room ringing on.
         */
        const val STOP_DROP = 0.12f

        /**
         * How far over the stop's quietest stretch the hop must be back for the note to have
         * started again: 0.1, about 6 dB, so a repeat played softer than the note before it still
         * counts, while a stop that sinks into silence (the end of the note) never does.
         */
        const val RESTART_RISE = 0.1f

        /**
         * Frames after a stop within which the tone's return is a restart (about 170 ms, a stop
         * of up to 80 ms plus the swell); later, the stop was the end of the note.
         */
        const val RESTART_FRAMES = 4
    }
}
