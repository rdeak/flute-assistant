// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.core.log.LogLine
import hr.bertlab.fluteassistant.domain.flute.ListenFrame
import hr.bertlab.fluteassistant.domain.flute.MarkChange
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pitch
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import java.io.File
import java.util.Locale

/**
 * Which of the younger columns a session's `frames.csv` has, and so what its rows can be compared by: the [walk]'s
 * (since 2026-11-11: where the animal was), the arrow's ([pitch], since 2026-11-14) and the [changed] mark's (since
 * 2026-11-16). A table from before all three has none.
 */
data class Columns(val walk: Boolean = false, val pitch: Boolean = false, val changed: Boolean = false)

/**
 * A recorded "Your turn" session read back from its folder ([FileSessionRecorder] wrote it): the
 * [tune] it checked, the [audio] the engine analysed, the room [floor] it started with, the
 * [rows] of `frames.csv` as [ListenPath.Step]s (what the phone did), the stamped [events] of its
 * log, the [rules] in force, the [facts] of the start line, the [pin], the marks a pinned
 * session must earn (`pin marks=0:FIRST_TRY,...`, a line added by hand), or null, and the [pulse]
 * the tune was checked at.
 */
class Session(
    val dir: File,
    val tune: List<TuneItem>,
    val audio: WavFile.Audio,
    val floor: Float?,
    val rows: List<ListenPath.Step>,
    val events: List<String>,
    val rules: Map<String, String>,
    val facts: Map<String, String>,
    val pin: Map<Int, StepMark>?,
    /** The metronome's pulse the tune was checked at (`session.pulse`); 60 in 4/4 when the log has none. */
    val pulse: Pulse = Pulse(60),
    /** Which of the younger columns `frames.csv` has: only those do the [rows] say anything about. */
    val columns: Columns = Columns(),
) {
    /** The [audio] as the engine frames it, detected once however many rules it is then replayed under. */
    val frames: ListenPath.Frames by lazy { ListenPath.frames(audio.samples, audio.sampleRate) }

    companion object {
        private val HEADER_EVENTS = setOf("session.start", "session.rules", "session.pulse", "session.tune", "pin")

        fun load(dir: File): Session {
            val lines = File(dir, FileSessionRecorder.LOG).readLines().filter { it.isNotBlank() }
            val tune = sortedMapOf<Int, TuneItem>()
            var floor: Float? = null
            var rules = emptyMap<String, String>()
            var facts = emptyMap<String, String>()
            var pin: Map<Int, StepMark>? = null
            var pulse = Pulse(60)
            val events = mutableListOf<String>()
            for (line in lines) {
                val (event, f) = LogLine.parse(line)
                when (event) {
                    "session.start" -> {
                        facts = f
                        floor = f["floor"]?.takeUnless { it == "none" }?.toFloat()
                    }
                    "session.rules" -> rules = f
                    "session.pulse" -> pulse = Pulse(
                        f.getValue("bpm").toInt(),
                        TimeSignature.entries.first { it.label == f.getValue("time") },
                    )
                    "session.tune" -> tune[f.getValue("index").toInt()] = when {
                        f["note"] != null -> TuneNote(
                            f.getValue("note").toInt(),
                            NoteLength.valueOf(f.getValue("length")),
                            joined = f["joined"] == "true",
                        )
                        f["rest"] != null -> TuneRest(NoteLength.valueOf(f.getValue("rest")))
                        else -> TuneBreath
                    }
                    "pin" -> pin = marks(f["marks"])
                    else -> if (event !in HEADER_EVENTS) events += line
                }
            }
            val audio = WavFile.read(File(dir, FileSessionRecorder.AUDIO))
            val table = rows(File(dir, FileSessionRecorder.FRAMES), audio.sampleRate)
            return Session(
                dir, tune.values.toList(), audio, floor,
                table.rows, events, rules, facts, pin, pulse, table.columns,
            )
        }

        /** `0:FIRST_TRY,3:RETRY` as marks; empty text as none. */
        fun marks(text: String?): Map<Int, StepMark> =
            text.orEmpty().split(',').filter { it.isNotEmpty() }.associate { entry ->
                entry.substringBefore(':').toInt() to StepMark.valueOf(entry.substringAfter(':'))
            }

        /** Marks as the end line spells them, in index order. */
        fun spell(marks: Map<Int, StepMark>): String =
            marks.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.name}" }

        /** A frame table read back: its rows, and which of the younger columns it has. */
        private class Table(val rows: List<ListenPath.Step>, val columns: Columns)

        /**
         * The rows of [csv], and whether it has the walk's columns (a table from before 2026-11-11 ends at `floor`) and the
         * arrow's (before 2026-11-14 it ends at `phase`).
         */
        private fun rows(csv: File, rate: Int): Table {
            if (!csv.isFile) return Table(emptyList(), Columns()) // a pinned folder may keep only the audio and the log
            val lines = csv.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return Table(emptyList(), Columns())
            val columns = lines.first().split(',').withIndex().associate { it.value to it.index }
            val has = Columns(walk = "phase" in columns, pitch = "pitch" in columns, changed = "changed" in columns)
            val rows = lines.drop(1).map { line ->
                val cells = line.split(',')
                fun cell(name: String) = cells.getOrNull(columns.getValue(name)).orEmpty()
                val sounding = cell("sounding").toIntOrNull()
                val walk = if (has.walk && cell("phase").isNotEmpty()) {
                    Walk(cell("walk").toInt(), cell("halfBeat").toInt(), Walk.Phase.valueOf(cell("phase")))
                } else {
                    null
                }
                val row = ListenFrame(
                    frame = PitchFrame(
                        cell("f").toDoubleOrNull(), cell("p").toDouble(), cell("level").toFloat(), cell("minLevel").toFloat(),
                        PitchFrame.hopMsAt(rate),
                    ),
                    decision = FrameDecision.valueOf(cell("decision")),
                    sounding = sounding,
                    onset = when (cell("onset")) {
                        "fresh" -> NoteOnsetTracker.Onset(sounding!!, fresh = true)
                        "step" -> NoteOnsetTracker.Onset(sounding!!, fresh = false)
                        else -> null
                    },
                    floor = cell("floor").toFloatOrNull(),
                    verdict = cell("verdict").takeIf { it.isNotEmpty() }?.let { Verdict.valueOf(it.uppercase(Locale.ROOT)) },
                    waited = cell("waited").toIntOrNull(),
                    expected = cell("expected").toIntOrNull(),
                    // What the screen showed: kept for the row, compared by no diff, so a name since renamed reads as none.
                    heardNow = cell("heardNow").toIntOrNull(),
                    advice = TunerAdvice.entries.firstOrNull { it.name == cell("advice") } ?: TunerAdvice.NONE,
                    repeatHeld = cell("repeatHeld").toBoolean(),
                    finishing = cell("finishing").toBoolean(),
                    hearing = Hearing.entries.firstOrNull { it.name == cell("hearing") } ?: Hearing.CLEAR,
                    walk = walk,
                    pitch = if (has.pitch) cell("pitch").takeIf { it.isNotEmpty() }?.let(Pitch::valueOf) else null,
                    changed = if (has.changed) cell("changed").takeIf { it.isNotEmpty() }?.let(::markChange) else null,
                )
                ListenPath.Step(cell("frame").toInt(), row)
            }
            return Table(rows, has)
        }

        /** A `changed` cell, `<index>:<MARK>`. */
        private fun markChange(cell: String) = MarkChange(cell.substringBefore(':').toInt(), StepMark.valueOf(cell.substringAfter(':')))
    }
}
