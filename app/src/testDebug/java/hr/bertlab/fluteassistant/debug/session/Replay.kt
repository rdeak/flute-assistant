// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import java.util.Locale

/**
 * Runs the listening path again over a recorded [Session]'s audio ([ListenPath], the real
 * detector, tracker and check) and compares it with what the phone did: [report] writes the
 * timeline and the [diff]; [sweep] replays with one rule at other values. No difference means the
 * phone did what the rules say, and the rules can be worked on here; a difference means timing
 * on the phone (a delayed or lost frame), and the first differing frame says where.
 */
object Replay {

    /** Frames whose decision, sounding note, onset or verdict differ, over the phone's rows; [trailing] hops the audio has past them. */
    data class Diff(val first: Int?, val count: Int, val trailing: Int)

    fun diff(phone: List<ListenPath.Step>, replay: List<ListenPath.Step>): Diff {
        var first: Int? = null
        var count = 0
        for ((i, a) in phone.withIndex()) {
            val b = replay.getOrNull(i)
            val same = b != null && a.decision == b.decision && a.sounding == b.sounding && a.onset == b.onset && a.verdict == b.verdict
            if (!same) {
                count++
                if (first == null) first = a.frame
            }
        }
        return Diff(first, count, maxOf(0, replay.size - phone.size))
    }

    fun report(session: Session, rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules()): String {
        val rate = session.audio.sampleRate
        val run = ListenPath.run(session.audio.samples, rate, session.tune, rules, session.floor)
        val out = StringBuilder()
        out.appendLine(
            "session ${session.dir.name}: ${session.rows.size} rows on the phone, ${run.steps.size} hops in the audio at $rate Hz, " +
                "floor at start ${session.floor?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "none"}",
        )
        out.appendLine("facts: " + session.facts.entries.joinToString(" ") { "${it.key}=${it.value}" })
        out.appendLine("tune: " + session.tune.withIndex().joinToString(" ") { (i, item) -> "$i:${spell(item)}" })
        out.appendLine("rules: " + rules.fields().joinToString(" ") { "${it.first}=${it.second}" })
        out.appendLine("replay:")
        for (step in run.steps) if (step.onset != null) out.appendLine("  " + onset(step, rate))
        out.appendLine("  marks=" + Session.spell(run.marks))
        val diff = diff(session.rows, run.steps)
        val trailing = if (diff.trailing > 0) " (${diff.trailing} hops after the last row)" else ""
        out.appendLine(if (diff.count == 0) "diff: none$trailing" else "diff: first=${diff.first} count=${diff.count}$trailing")
        if (diff.count > 0) {
            var shown = 0
            for ((i, a) in session.rows.withIndex()) {
                val b = run.steps.getOrNull(i)
                if (b != null && a.decision == b.decision && a.sounding == b.sounding && a.onset == b.onset && a.verdict == b.verdict) continue
                out.appendLine("  frame ${a.frame}: phone ${cells(a)} / replay ${b?.let(::cells) ?: "no hop"}")
                if (++shown == 10) {
                    out.appendLine("  ...")
                    break
                }
            }
        }
        out.appendLine("phone:")
        for (step in session.rows) if (step.onset != null) out.appendLine("  " + onset(step, rate))
        out.appendLine("events:")
        for (event in session.events) out.appendLine("  $event")
        return out.toString()
    }

    /** One line per value of the rule named in [spec] (`stopDrop:0.08,0.12,0.5`): what was heard and the marks earned. */
    fun sweep(session: Session, spec: String): String {
        require(':' in spec) { "a sweep is <rule>:<value>,<value>,...: $spec" }
        val name = spec.substringBefore(':').trim()
        val values = spec.substringAfter(':').split(',').map { it.trim().toDouble() }
        val out = StringBuilder()
        out.appendLine("sweep of $name over ${session.dir.name}:")
        for (value in values) {
            val rules = NoteOnsetTracker.Rules().with(name, value)
            val run = ListenPath.run(session.audio.samples, session.audio.sampleRate, session.tune, rules, session.floor)
            out.appendLine("$name=${number(value)} heard=${run.heard.joinToString(",") { it.onset.midi.toString() }} marks=${Session.spell(run.marks)}")
        }
        return out.toString()
    }

    private fun onset(step: ListenPath.Step, rate: Int): String {
        val t = step.frame * AudioRecordTunerEngine.HOP_SIZE / rate.toDouble()
        val onset = step.onset!!
        return String.format(
            Locale.ROOT, "t=%.2fs frame=%d index=%s expected=%s heard=%d %s verdict=%s",
            t, step.frame, step.waited ?: "none", step.expected ?: "none", onset.midi, if (onset.fresh) "fresh" else "step",
            step.verdict?.name?.lowercase(Locale.ROOT) ?: "none",
        )
    }

    private fun cells(step: ListenPath.Step) =
        "${step.decision} ${step.sounding ?: "-"} ${step.onset?.let { if (it.fresh) "fresh" else "step" } ?: "-"} ${step.verdict?.name?.lowercase(Locale.ROOT) ?: "-"}"

    private fun spell(item: hr.bertlab.fluteassistant.domain.flute.TuneItem) = when (item) {
        is TuneNote -> "${item.midi}/${item.length}"
        is TuneRest -> "rest/${item.length}"
        TuneBreath -> "breath"
    }

    private fun number(value: Double) = value.toString().removeSuffix(".0")
}
