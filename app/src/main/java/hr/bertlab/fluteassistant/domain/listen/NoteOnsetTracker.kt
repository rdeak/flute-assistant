// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.abs

/**
 * Turns microphone frames into notes the way the tuner's "Which?" mode does, and says when a
 * *new* note begins, which is what the flute's "Your turn" judges. Pure Kotlin, one frame at a
 * time, with the tuner's own thresholds: a frame counts only with a frequency and a probability
 * of at least [ListenRules.MIN_PROBABILITY], and, which is the flute's own, a level of at least
 * [MIN_LEVEL] (a clear pitch far under a flute at the phone is the room's: [FrameDecision.FAINT]);
 * a note is sounding once the last
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
        val minLevel: Float = MIN_LEVEL,
        val stepFromSounding: Boolean = true,
    ) {
        /** Name and value of every rule, in declaration order, the value as Kotlin prints it. */
        fun fields(): List<Pair<String, String>> = TABLE.fields(this)

        /** These rules with the one named [name] set to [value] (rounded for a frame count); an unknown name is refused. */
        fun with(name: String, value: Double): Rules = TABLE.with(this, name, value)

        companion object {
            /** Every rule once, in declaration order: a new rule is a constructor parameter and a line here. */
            private val TABLE = RuleTable<Rules>(
                numberRule("gate", { it.gate }) { r, v -> r.copy(gate = v) },
                countRule("lockFrames", { it.lockFrames }) { r, v -> r.copy(lockFrames = v) },
                numberRule("lockCents", { it.lockCents }) { r, v -> r.copy(lockCents = v) },
                countRule("smoothing", { it.smoothing }) { r, v -> r.copy(smoothing = v) },
                countRule("switchFrames", { it.switchFrames }) { r, v -> r.copy(switchFrames = v) },
                countRule("holdFrames", { it.holdFrames }) { r, v -> r.copy(holdFrames = v) },
                numberRule("nearCents", { it.nearCents }) { r, v -> r.copy(nearCents = v) },
                countRule("onsetGapFrames", { it.onsetGapFrames }) { r, v -> r.copy(onsetGapFrames = v) },
                levelRule("gapLevelRatio", { it.gapLevelRatio }) { r, v -> r.copy(gapLevelRatio = v) },
                levelRule("floorMargin", { it.floorMargin }) { r, v -> r.copy(floorMargin = v) },
                levelRule("floorRise", { it.floorRise }) { r, v -> r.copy(floorRise = v) },
                countRule("recentFrames", { it.recentFrames }) { r, v -> r.copy(recentFrames = v) },
                countRule("attackFrames", { it.attackFrames }) { r, v -> r.copy(attackFrames = v) },
                levelRule("stopDrop", { it.stopDrop }) { r, v -> r.copy(stopDrop = v) },
                levelRule("restartRise", { it.restartRise }) { r, v -> r.copy(restartRise = v) },
                countRule("restartFrames", { it.restartFrames }) { r, v -> r.copy(restartFrames = v) },
                levelRule("minLevel", { it.minLevel }) { r, v -> r.copy(minLevel = v) },
                flagRule("stepFromSounding", { it.stepFromSounding }) { r, v -> r.copy(stepFromSounding = v) },
            )

            /** The names [with] knows. */
            val NAMES: Set<String> = TABLE.names
        }
    }

    /**
     * What became of one frame, and the note that began on it, if one did. [leadFrames] are the
     * frames before this one that belong to that note, which began that many frames before the
     * phone was sure of it: the frames in a row before it that sounded near the pitch that locked
     * ([leadOf]), after silence as after another note (a step, an octave pop). Frames of other notes
     * before them are not its; nor is anything before a stop or, while a note sounded, before a
     * frame that did not count or that showed that note going on, so a tongued restart starts at its
     * onset and a slur starts after the last frame of the note before. 0 when no note began.
     * [lockHz] is the pitch the note locked at, the median that named it (how far it lies from a
     * written note says whether a wrong one was a near miss); null when no note began. [stopDrop]: on a frame whose
     * onset came by a restart after a stop ([noticeStop]), how deep the stop fell under the quieter of its two sides:
     * the lower of the note's recent level when the stop was first heard and this frame's quietest stretch, less the
     * stop's quietest stretch (since 2026-11-16: the flute's slur check tells a tongue from a breath dip by it; measured
     * against the note before alone, a softer note after it made a breath's dip a tongue's); null on every other frame,
     * an onset after a gap included.
     */
    data class Result(
        val decision: FrameDecision,
        val onset: Onset?,
        val leadFrames: Int = 0,
        val lockHz: Double? = null,
        val stopDrop: Float? = null,
    )

    /**
     * A note that began: its MIDI, and whether it was a [fresh] start after a gap (nothing was
     * sounding) rather than a step from the note that was sounding without a gap (a slur, a
     * fingered change, an octave pop). A written repeat can only be played by a fresh start.
     */
    data class Onset(val midi: Int, val fresh: Boolean)

    /** The note sounding now, as the tuner's "Which?" ring would show it; null between notes. */
    var sounding: Int? = null
        private set

    private val window = SmoothingWindow(rules.smoothing)
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
    /**
     * Where the sounding note was sounding: the median on the last frame whose median named it (since 2026-11-16). A move
     * measured from here as well as from the note's written pitch ([Rules.stepFromSounding]) is how a slurred semitone on
     * a flute blown sharp or flat is heard: G 40 cents sharp to F♯ 40 cents sharp lies 60 cents from G but 100 from
     * where G sounded. Null between notes.
     */
    private var soundingHz: Double? = null
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
    /**
     * The newest frames in a row that a note beginning now may have begun in, oldest first: each counted frame's
     * frequency, and null for a frame the gate missed while nothing sounded (a breathy blink inside a note that
     * is beginning). Emptied by a stop and, while a note sounds, by a frame that did not count or that showed the
     * note going on: what came before was the old note's. [leadOf] reads [Result.leadFrames] from it.
     */
    private val runFrames = ArrayDeque<Double?>()
    /**
     * A stop heard inside the sounding note and still pending: the note's [recent] level when it was first heard, its
     * quietest stretch so far ([floor], which the tone must rise [RESTART_RISE] over; how deep it fell is [recent] less
     * this), and the frames since it was last heard ([age]).
     */
    private class Stop(val recent: Float, var floor: Float) {
        var age = 0
    }

    /** The stop pending inside the sounding note; null when none is. */
    private var stop: Stop? = null

    fun onFrame(frame: PitchFrame): Result {
        noticeStop(frame)
        val frequency = frame.frequency ?: return uncounted(FrameDecision.SILENT, frame.level)
        if (frame.probability < rules.gate) return uncounted(FrameDecision.LOW_PROBABILITY, frame.level)
        if (frame.level < rules.minLevel) return uncounted(FrameDecision.FAINT, frame.level)
        gap = 0
        unheard = 0
        levelSum += frame.level
        levelCount++
        recentLevels.addLast(frame.level)
        while (recentLevels.size > rules.recentFrames) recentLevels.removeFirst()
        if (sounding != null) framesSinceOnset++
        if (stop?.age == 0) runFrames.clear() else runFrames.addLast(frequency) // a stop's frame is the old note's
        while (runFrames.size > RUN_FRAMES) runFrames.removeFirst()
        window.add(frequency)
        if (!window.isSteady(rules.lockFrames, rules.lockCents)) return Result(FrameDecision.UNSTEADY, onset = null).also { ageStop() }
        val median = window.median()
        val before = sounding
        val identified = identify(median)
        // The tone is back after a stop: the note started again, whatever pitch locked.
        val restart = stop?.takeIf { it.age in 1..rules.restartFrames && frame.minLevel >= it.floor + rules.restartRise }
        val onset = when {
            identified != before && !drifted -> Onset(identified, fresh = before == null || restart != null)
            identified == before && restart != null -> Onset(identified, fresh = true)
            else -> null
        }
        sounding = identified
        if (onset == null) {
            // Shown as the sounding note going on (unless a stop waits to be a restart): this frame and all before it
            // are that note's, however near the pitch of a note stepped to after them (a sharp b, then a slur to c).
            if (stop == null) runFrames.clear()
            ageStop()
            return Result(FrameDecision.SHOWN, onset = null)
        }
        val lead = leadOf(median)
        // Under the quieter side: a joined note softer than the one before must not turn a breath's dip into a tongue's.
        val stopDrop = restart?.let { minOf(it.recent, frame.minLevel) - it.floor }
        beginNote(frame.level)
        return Result(FrameDecision.SHOWN, onset, lead, lockHz = median, stopDrop = stopDrop)
    }

    /** A note began on a frame at [level]: its loudness is its own, the frames that locked it are its first, and no stop is pending in it. */
    private fun beginNote(level: Float) {
        levelSum = level.toDouble()
        levelCount = 1
        recentLevels.clear()
        recentLevels.addLast(level)
        framesSinceOnset = 0
        stop = null
    }

    /**
     * How many frames before this one the note that locked at [median] began: the frames in a row, back from
     * this one, that sounded within [ListenRules.NEAR_CENTS] of it (the tracker's own measure of the same note
     * approached, as against a clear step), a blink among them included. So an attack that scoops into the note
     * is the note's, and sound that wandered elsewhere before it, a wobble of other notes or a slide, is not.
     */
    private fun leadOf(median: Double): Int {
        var lead = 0
        var counted = 0 // the lead as far as its oldest counted frame: a blink before the note's first frame is not its
        for (i in runFrames.size - 2 downTo 0) {
            val frequency = runFrames[i]
            if (frequency != null && abs(NoteMath.centsApart(frequency, median)) > rules.nearCents) break
            lead++
            if (frequency != null) counted = lead
        }
        return counted
    }

    /** Back to silence: no note sounding, nothing remembered of it (the room's floor is kept). */
    fun reset() {
        sounding = null
        soundingHz = null
        window.clear()
        gap = 0
        unheard = 0
        driftFrames = 0
        drifted = false
        levelSum = 0.0
        levelCount = 0
        recentLevels.clear()
        framesSinceOnset = 0
        runFrames.clear()
        stop = null
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
            val pending = stop
            if (pending == null) {
                stop = Stop(recent = recentMean, floor = frame.minLevel)
            } else {
                pending.floor = minOf(pending.floor, frame.minLevel)
                pending.age = 0
            }
        }
    }

    /** A frame has passed since the stop; past [RESTART_FRAMES] it was the end of the note, not a stop in it. */
    private fun ageStop() {
        stop?.let { if (++it.age > rules.restartFrames) stop = null }
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
        // A blink inside a note that is beginning stays in the run; a second one empties the window below.
        if (note == null && runFrames.isNotEmpty()) runFrames.addLast(null) else runFrames.clear()
        if (note == null) {
            floor = floor?.let { minOf(level, it + rules.floorRise) } ?: level
        }
        val quiet = note == null || levelCount == 0 ||
            level < (levelSum / levelCount) * rules.gapLevelRatio ||
            floor?.let { level <= it + rules.floorMargin } == true
        if (quiet) gap++
        unheard++
        if ((quiet && gap >= rules.onsetGapFrames) || unheard >= rules.holdFrames) reset()
        ageStop()
        return Result(decision, onset = null)
    }

    /**
     * The note heard (the [median]'s nearest note) becomes the sounding note at once when nothing
     * was sounding, when it is the sounding note, or when it is a clear step away: further than
     * [ListenRules.NEAR_CENTS] from the sounding note's written pitch or, with [Rules.stepFromSounding],
     * from where it was sounding ([soundingHz]: a slurred semitone on a flute off pitch); a drift that has
     * only passed the halfway point to a neighbour waits for [ListenRules.SWITCH_FRAMES] shown
     * frames in a row, one frame back on the sounding note starting the count again.
     */
    private fun identify(median: Double): Int {
        val heard = NoteMath.frequencyToNote(median).midi
        val current = sounding
        drifted = false
        if (current != null && heard != current && !isClearStep(median, current)) {
            if (++driftFrames < rules.switchFrames) return current
            drifted = true
        }
        driftFrames = 0
        soundingHz = median
        return heard
    }

    /** Whether [median] is a clear step away from the sounding note [current]: from its written pitch, or from where it was sounding. */
    private fun isClearStep(median: Double, current: Int): Boolean {
        val reach = abs(NoteMath.centsBetween(median, current))
        val fromSounding = soundingHz?.takeIf { rules.stepFromSounding }?.let { abs(NoteMath.centsApart(median, it)) } ?: 0.0
        return reach > rules.nearCents || fromSounding > rules.nearCents
    }

    companion object {
        /**
         * Quiet frames in a row that did not count (about 90 ms) after which the next note is a new
         * one. One frame is too few: a single dropped frame inside a held note would split it in two.
         */
        const val ONSET_GAP_FRAMES = 2

        /** The most frames kept to read a note's lead from: over two seconds, more than any attack takes to settle. */
        private const val RUN_FRAMES = 64

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
         * stop: 0.12, about 7 dB; a tongued stop falls 10 dB or more even with the room ringing on.
         * A child's breath can dip about as far for a moment inside a held note, which this hears as
         * a stop and a restart: the flute's walk does not cut a note on it
         * (`ListenRun.Rules.restartCuts`), and a higher value would lose the 40 ms tongue in a lively
         * room (`ListenPathTest`; `ui/flute/DESIGN.md`, "The first runs with the walk").
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

        /**
         * The level under which a frame with a clear pitch still does not count ([FrameDecision.FAINT]):
         * 0.45, about -33 dBFS. A voice across the room or a television has a clear pitch too, and
         * until 2026-11-10 "Your turn" judged it: on the user's phone a run earned a star and five
         * wrong marks before anything was played, from sounds whose counted frames were never louder
         * than 0.36, while every note of the real flute sessions of 2026-11-05 was judged at 0.67 or
         * over (their median 0.79). 0.45 stands between the two, 5 dB over the loudest of that room
         * and 13 dB under the softest of those notes, and is where [HearingTracker] stops calling a
         * sound "close" for good ([HearingTracker.CLOSE_LEVEL] less its margin), so what is left out
         * is what "Come closer to the phone" is already said for: faint frames from
         * [HearingTracker.SOUND_LEVEL] up are sound to it, and a second of them brings that line.
         * A frame under it does not count, as one under the probability gate does not: a note goes on
         * through such frames and ends by the gap rules. One room and one sitting of real playing
         * stand behind the number; it is a [Rules] value, in every session's `session.rules`, so a
         * session where a child's soft playing was left out says so (the `sessions` skill, a sweep
         * of `minLevel`). The tuner does not apply it: its ring may show a quiet note.
         */
        const val MIN_LEVEL = 0.45f
    }
}
