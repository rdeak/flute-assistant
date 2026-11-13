// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.core.log.LogLine
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import java.io.File
import java.util.Locale

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
    /** Whether `frames.csv` has the walk's columns (since 2026-11-11): only then do the [rows] say where the animal was. */
    val recordsWalk: Boolean = false,
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
                        f["note"] != null -> TuneNote(f.getValue("note").toInt(), NoteLength.valueOf(f.getValue("length")))
                        f["rest"] != null -> TuneRest(NoteLength.valueOf(f.getValue("rest")))
                        else -> TuneBreath
                    }
                    "pin" -> pin = marks(f["marks"])
                    else -> if (event !in HEADER_EVENTS) events += line
                }
            }
            val audio = WavFile.read(File(dir, FileSessionRecorder.AUDIO))
            val (rows, recordsWalk) = rows(File(dir, FileSessionRecorder.FRAMES), audio.sampleRate)
            return Session(
                dir, tune.values.toList(), audio, floor,
                rows, events, rules, facts, pin, pulse, recordsWalk,
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

        /** The rows of [csv], and whether it has the walk's columns (a table from before 2026-11-11 ends at `floor`). */
        private fun rows(csv: File, rate: Int): Pair<List<ListenPath.Step>, Boolean> {
            if (!csv.isFile) return emptyList<ListenPath.Step>() to false // a pinned folder may keep only the audio and the log
            val lines = csv.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList<ListenPath.Step>() to false
            val columns = lines.first().split(',').withIndex().associate { it.value to it.index }
            val recordsWalk = "phase" in columns
            return lines.drop(1).map { line ->
                val cells = line.split(',')
                fun cell(name: String) = cells.getOrNull(columns.getValue(name)).orEmpty()
                val sounding = cell("sounding").toIntOrNull()
                val walk = if (recordsWalk && cell("phase").isNotEmpty()) {
                    Walk(cell("walk").toInt(), cell("halfBeat").toInt(), Walk.Phase.valueOf(cell("phase")))
                } else {
                    null
                }
                ListenPath.Step(
                    frame = cell("frame").toInt(),
                    pitch = PitchFrame(
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
                    walk = walk,
                )
            } to recordsWalk
        }
    }
}
