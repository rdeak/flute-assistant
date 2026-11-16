// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import java.util.Locale

/**
 * Runs the listening path again over a recorded [Session]'s audio ([ListenPath], the real
 * detector, tracker and check) and compares it with what the phone did: [report] writes the
 * timeline and the [diff], under the rules the session was recorded with ([recordedRules], [recordedRunRules]), and adds what
 * today's rules hear where they are others; [sweep] replays with one of today's rules at other values. No difference means the
 * phone did what the rules say, and the rules can be worked on here; a difference means timing
 * on the phone (a delayed or lost frame), and the first differing frame says where.
 */
object Replay {

    /**
     * Frames whose decision, sounding note, onset or verdict differ, and with [walk] also where the animal was
     * (the frame a note was earned or cut short on, the hops the child saw), over the phone's rows; [trailing]
     * hops the audio has past them.
     */
    data class Diff(val first: Int?, val count: Int, val trailing: Int)

    /**
     * [walk]: compare the walk too, which only a session that recorded it can ([Session.recordsWalk]); [pitch]: the arrow
     * of a note walked off its pitch too ([Session.recordsPitch]).
     */
    fun diff(phone: List<ListenPath.Step>, replay: List<ListenPath.Step>, walk: Boolean = false, pitch: Boolean = false): Diff {
        var first: Int? = null
        var count = 0
        for ((i, a) in phone.withIndex()) {
            if (!same(a, replay.getOrNull(i), walk, pitch)) {
                count++
                if (first == null) first = a.frame
            }
        }
        return Diff(first, count, maxOf(0, replay.size - phone.size))
    }

    private fun same(a: ListenPath.Step, b: ListenPath.Step?, walk: Boolean, pitch: Boolean = false): Boolean =
        b != null && a.decision == b.decision && a.sounding == b.sounding && a.onset == b.onset && sameVerdict(a, b) &&
            (!walk || a.walk == b.walk) && (!pitch || a.row.pitch == b.row.pitch)

    /**
     * The same verdict, or the "wrong" a phone logged until 2026-11-13 for a note heard while a note cut short waited,
     * which the screen never showed as wrong and today's rules call ignored: only the word changed.
     */
    private fun sameVerdict(phone: ListenPath.Step, replay: ListenPath.Step): Boolean =
        phone.verdict == replay.verdict ||
            phone.verdict == Verdict.WRONG && replay.verdict == Verdict.IGNORED && phone.walk?.phase == Walk.Phase.SHORT

    /**
     * Rules younger than the first recorded sessions, by name, each with what the builds before it did: a session
     * whose `session.rules` line does not name one ran with that value. One table for the tracker's rules and the run's.
     */
    private val BEFORE = mapOf(
        "minLevel" to 0.0, // 2026-11-10: no loudness floor
        "lengthSlack" to Double.POSITIVE_INFINITY, // 2026-11-11: pitch only, no length rule
        "maxNeedMs" to Double.POSITIVE_INFINITY,
        "restartCuts" to 1.0, // 2026-11-13: a gapless restart cut the note walked
        "graceFrames" to 2.0, // 2026-11-13: the tracker's onsetGapFrames, 2 in every recording
        "walkRests" to 0.0, // 2026-11-13: rests passed over, the animal jumped to the note after
        "highCents" to 0.0, // 2026-11-14: the nearest semitone, no band above a written note
        "tuneCents" to Double.POSITIVE_INFINITY, // 2026-11-14: no pitch remark, no arrow
        "shortMovesOn" to 0.0, // 2026-11-14: a note let go too early was waited on
    )

    /** [today]'s rules (the ones in [names]) set as [session] recorded them, a rule it does not name as [BEFORE] has it. */
    private fun <R> recorded(session: Session, today: R, names: Set<String>, with: R.(String, Double) -> R): R =
        names.fold(today) { rules, name -> (session.rules[name]?.toDouble() ?: BEFORE[name])?.let { rules.with(name, it) } ?: rules }

    /** The tracker's rules the phone ran [session] under. */
    fun recordedRules(session: Session): NoteOnsetTracker.Rules =
        recorded(session, NoteOnsetTracker.Rules(), NoteOnsetTracker.Rules.NAMES, NoteOnsetTracker.Rules::with)

    /** The run's own rules the phone ran [session] under. */
    fun recordedRunRules(session: Session): ListenRun.Rules =
        recorded(session, ListenRun.Rules(), ListenRun.Rules.NAMES, ListenRun.Rules::with)

    /** Every rule's name and value, the tracker's then the run's, in the order a session's line has them. */
    private fun fields(rules: NoteOnsetTracker.Rules, run: ListenRun.Rules) = rules.fields() + run.fields()

    /**
     * The replay under [rules] and [run], by default the ones the session was recorded with, which is what the
     * diff against the phone's rows must be made with; where today's are others, what they make of the same
     * sound follows. The audio is detected once ([Session.frames]) for both.
     */
    fun report(
        session: Session,
        rules: NoteOnsetTracker.Rules = recordedRules(session),
        run: ListenRun.Rules = recordedRunRules(session),
    ): String {
        val rate = session.audio.sampleRate
        val replay = ListenPath.run(session.frames, session.tune, session.pulse, rules, session.floor, run)
        val out = StringBuilder()
        out.appendLine(
            "session ${session.dir.name}: ${session.rows.size} rows on the phone, ${replay.steps.size} hops in the audio at $rate Hz, " +
                "floor at start ${session.floor?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "none"}",
        )
        out.appendLine("facts: " + session.facts.entries.joinToString(" ") { "${it.key}=${it.value}" })
        out.appendLine("tune: " + session.tune.withIndex().joinToString(" ") { (i, item) -> "$i:${spell(item)}" })
        out.appendLine("rules: " + fields(rules, run).joinToString(" ") { "${it.first}=${it.second}" })
        out.appendLine("pulse: bpm=${session.pulse.bpm} time=${session.pulse.timeSignature.label}")
        out.appendLine("replay:")
        for (step in replay.steps) if (step.onset != null) out.appendLine("  " + onset(step, rate))
        out.appendLine("  marks=" + Session.spell(replay.marks))
        val walk = session.recordsWalk
        val pitch = session.recordsPitch
        val diff = diff(session.rows, replay.steps, walk, pitch)
        val trailing = if (diff.trailing > 0) " (${diff.trailing} hops after the last row)" else ""
        out.appendLine(if (diff.count == 0) "diff: none$trailing" else "diff: first=${diff.first} count=${diff.count}$trailing")
        if (diff.count > 0) {
            var shown = 0
            for ((i, a) in session.rows.withIndex()) {
                val b = replay.steps.getOrNull(i)
                if (same(a, b, walk, pitch)) continue
                out.appendLine("  frame ${a.frame}: phone ${cells(a, walk, pitch)} / replay ${b?.let { cells(it, walk, pitch) } ?: "no hop"}")
                if (++shown == 10) {
                    out.appendLine("  ...")
                    break
                }
            }
        }
        val today = NoteOnsetTracker.Rules()
        val todayRun = ListenRun.Rules()
        if (today != rules || todayRun != run) {
            val then = fields(rules, run).toMap()
            val changed = fields(today, todayRun).filter { (name, value) -> then[name] != value }
            // A rule the session never wrote down was not there to record: "none", not the value that stands in for it.
            out.appendLine("today's rules differ: " + changed.joinToString(" ") { (name, value) -> "$name=$value (recorded ${session.rules[name] ?: "none"})" })
            val now = ListenPath.run(session.frames, session.tune, session.pulse, today, session.floor, todayRun)
            for (step in now.steps) if (step.onset != null) out.appendLine("  " + onset(step, rate))
            out.appendLine("  marks=" + Session.spell(now.marks))
        }
        out.appendLine("phone:")
        for (step in session.rows) if (step.onset != null) out.appendLine("  " + onset(step, rate))
        out.appendLine("events:")
        for (event in session.events) out.appendLine("  $event")
        return out.toString()
    }

    /**
     * One line per value of the rule named in [spec], a tracker's (`stopDrop:0.08,0.12,0.5`) or the run's own
     * (`lengthSlack:0.5,1,2` in half beats, `maxNeedMs:2000,3000,4000`): what was heard and the marks earned, at
     * the session's pulse, the other rules at today's values. The audio is detected once for all of them.
     */
    fun sweep(session: Session, spec: String): String {
        require(':' in spec) { "a sweep is <rule>:<value>,<value>,...: $spec" }
        val name = spec.substringBefore(':').trim()
        val values = spec.substringAfter(':').split(',').map { it.trim().toDouble() }
        val out = StringBuilder()
        out.appendLine("sweep of $name over ${session.dir.name}:")
        val ofRun = name in ListenRun.Rules.NAMES
        for (value in values) {
            val run = ListenPath.run(
                session.frames, session.tune, session.pulse,
                rules = if (ofRun) NoteOnsetTracker.Rules() else NoteOnsetTracker.Rules().with(name, value),
                floor = session.floor,
                run = if (ofRun) ListenRun.Rules().with(name, value) else ListenRun.Rules(),
            )
            out.appendLine("$name=${number(value)} heard=${run.heard.joinToString(",") { it.onset.midi.toString() }} marks=${Session.spell(run.marks)}")
        }
        return out.toString()
    }

    private fun onset(step: ListenPath.Step, rate: Int): String {
        val t = step.frame * PitchFrame.hopMsAt(rate) / 1000
        val onset = step.onset!!
        return String.format(
            Locale.ROOT, "t=%.2fs frame=%d index=%s expected=%s heard=%d %s verdict=%s",
            t, step.frame, step.waited ?: "none", step.expected ?: "none", onset.midi, if (onset.fresh) "fresh" else "step",
            step.verdict?.name?.lowercase(Locale.ROOT) ?: "none",
        )
    }

    private fun cells(step: ListenPath.Step, walk: Boolean, pitch: Boolean = false) =
        "${step.decision} ${step.sounding ?: "-"} ${step.onset?.let { if (it.fresh) "fresh" else "step" } ?: "-"} ${step.verdict?.name?.lowercase(Locale.ROOT) ?: "-"}" +
            (if (walk) " ${step.walk?.let { "${it.index}+${it.halfBeat} ${it.phase}" } ?: "-"}" else "") +
            if (pitch) " ${step.row.pitch ?: "-"}" else ""

    private fun spell(item: hr.bertlab.fluteassistant.domain.flute.TuneItem) = when (item) {
        is TuneNote -> "${item.midi}/${item.length}"
        is TuneRest -> "rest/${item.length}"
        TuneBreath -> "breath"
    }

    private fun number(value: Double) = value.toString().removeSuffix(".0")
}
