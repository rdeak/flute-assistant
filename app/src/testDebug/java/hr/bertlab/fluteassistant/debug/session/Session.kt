// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.core.log.LogLine
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import hr.bertlab.fluteassistant.ui.tuner.FrameDecision
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker
import java.io.File
import java.util.Locale

/**
 * A recorded "Your turn" session read back from its folder ([FileSessionRecorder] wrote it): the
 * [tune] it checked, the [audio] the engine analysed, the room [floor] it started with, the
 * [rows] of `frames.csv` as [ListenPath.Step]s (what the phone did), the stamped [events] of its
 * log, the [rules] in force, the [facts] of the start line, and the [pin], the marks a pinned
 * session must earn (`pin marks=0:FIRST_TRY,...`, a line added by hand), or null.
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
) {
    companion object {
        private val HEADER_EVENTS = setOf("session.start", "session.rules", "session.pulse", "session.tune", "pin")

        fun load(dir: File): Session {
            val lines = File(dir, FileSessionRecorder.LOG).readLines().filter { it.isNotBlank() }
            val tune = sortedMapOf<Int, TuneItem>()
            var floor: Float? = null
            var rules = emptyMap<String, String>()
            var facts = emptyMap<String, String>()
            var pin: Map<Int, StepMark>? = null
            val events = mutableListOf<String>()
            for (line in lines) {
                val (event, f) = LogLine.parse(line)
                when (event) {
                    "session.start" -> {
                        facts = f
                        floor = f["floor"]?.takeUnless { it == "none" }?.toFloat()
                    }
                    "session.rules" -> rules = f
                    "session.tune" -> tune[f.getValue("index").toInt()] = when {
                        f["note"] != null -> TuneNote(f.getValue("note").toInt(), NoteLength.valueOf(f.getValue("length")))
                        f["rest"] != null -> TuneRest(NoteLength.valueOf(f.getValue("rest")))
                        else -> TuneBreath
                    }
                    "pin" -> pin = marks(f["marks"])
                    else -> if (event !in HEADER_EVENTS) events += line
                }
            }
            return Session(
                dir, tune.values.toList(), WavFile.read(File(dir, FileSessionRecorder.AUDIO)), floor,
                rows(File(dir, FileSessionRecorder.FRAMES)), events, rules, facts, pin,
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

        private fun rows(csv: File): List<ListenPath.Step> {
            if (!csv.isFile) return emptyList() // a pinned folder may keep only the audio and the log
            val lines = csv.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()
            val columns = lines.first().split(',').withIndex().associate { it.value to it.index }
            return lines.drop(1).map { line ->
                val cells = line.split(',')
                fun cell(name: String) = cells.getOrNull(columns.getValue(name)).orEmpty()
                val sounding = cell("sounding").toIntOrNull()
                ListenPath.Step(
                    frame = cell("frame").toInt(),
                    pitch = PitchFrame(cell("f").toDoubleOrNull(), cell("p").toDouble(), cell("level").toFloat(), cell("minLevel").toFloat()),
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
                )
            }
        }
    }
}
