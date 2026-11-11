// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.audio.tuner.AudioTap
import hr.bertlab.fluteassistant.core.log.LogLine
import hr.bertlab.fluteassistant.core.log.LogSink
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.flute.ListenFrame
import hr.bertlab.fluteassistant.domain.flute.ListenRecorder
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The debug build's record of every "Your turn" run, one folder per run under `root/sessions/`
 * named by the run's local start time (`2026-11-05_18-32-10`, `_2` for a second run in the
 * same second), the newest [KEEP] kept: `audio.wav`, the samples the engine analysed ([WavWriter],
 * mono 32-bit float at the microphone's rate, from the first hop after it opened); `frames.csv`,
 * one row per frame the view model processed, [HEADER] naming the columns; and `session.log`,
 * the run's header (the facts, every rule, the pulse, the tune) followed by every log line the
 * app wrote while the run was open, each stamped `frame=<rows so far> tag=<tag> level=<level>`,
 * and an end line with the marks. The design and the reading of it: `docs/superpowers/specs/2026-11-05-flute-listen-
 * session-recorder-design.md`; the tools that replay it: `ui/flute/CLAUDE.md`, "Sessions".
 *
 * It is the engine's [AudioTap] (samples on the IO thread), the view model's [ListenRecorder]
 * (rows on the main thread) and the [hr.bertlab.fluteassistant.core.log.RecordingLogger]'s
 * [LogSink], under one lock. The audio is a child's playing: it stays in this folder on this
 * phone (app-private, gone with the app, excluded from backup) and leaves it only over the cable.
 * A write that fails logs `session.error` once through [logger] (the plain logger, not the
 * recording one) and drops the session; the listening itself is never touched.
 */
class FileSessionRecorder(
    private val root: File,
    private val facts: Facts,
    private val logger: Logger,
    private val now: () -> Long = System::currentTimeMillis,
) : ListenRecorder, AudioTap, LogSink {

    /** What the start line says of the phone and the app. */
    data class Facts(val app: String, val code: Long, val device: String, val sdk: Int)

    private class Session(val dir: File, val log: BufferedWriter, val csv: BufferedWriter) {
        var wav: WavWriter? = null
        var rate = DEFAULT_RATE
        var rows = 0
    }

    private val lock = Any()
    private var session: Session? = null

    override fun start(tune: List<TuneItem>, pulse: Pulse, floor: Float?) = guarded {
        session?.let { close(it, marks = emptyMap()) }
        val dir = newFolder()
        val log = File(dir, LOG).bufferedWriter()
        val csv = File(dir, FRAMES).bufferedWriter()
        val s = Session(dir, log, csv)
        session = s
        val at = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(Date(now()))
        line(
            s, "session.start", "at" to at, "app" to facts.app, "code" to facts.code, "device" to facts.device,
            "sdk" to facts.sdk, "floor" to floor?.let { level(it) },
        )
        val rules = NoteOnsetTracker.Rules().fields() +
            listOf("repeatAdviceFrames" to ListenRun.REPEAT_ADVICE_FRAMES.toString(), "finishFrames" to ListenRun.FINISH_FRAMES.toString())
        line(s, "session.rules", *rules.toTypedArray())
        line(s, "session.pulse", "bpm" to pulse.bpm, "time" to pulse.timeSignature.label)
        tune.forEachIndexed { index, item ->
            when (item) {
                is TuneNote -> line(s, "session.tune", "index" to index, "note" to item.midi, "length" to item.length)
                is TuneRest -> line(s, "session.tune", "index" to index, "rest" to item.length)
                TuneBreath -> line(s, "session.tune", "index" to index, "breath" to true)
            }
        }
        csv.write(HEADER)
        csv.newLine()
        csv.flush()
        logger.i(LogTags.FLUTE, "session.open", "path" to dir.path)
    }

    override fun opened(sampleRate: Int, source: String) = guarded {
        val s = session ?: return@guarded
        if (s.wav != null) {
            // The microphone opened again inside one run (nothing in the app does this today): the
            // audio keeps going at the first opening's rate, so the rows still line up with it.
            line(s, "session.reopened", "frame" to s.rows, "rate" to sampleRate, "source" to source)
            return@guarded
        }
        s.rate = sampleRate
        s.wav = WavWriter(File(s.dir, AUDIO), sampleRate)
        line(
            s, "session.audio", "rate" to sampleRate, "source" to source,
            "window" to AudioRecordTunerEngine.WINDOW_SIZE, "hop" to AudioRecordTunerEngine.HOP_SIZE,
        )
    }

    override fun samples(chunk: FloatArray, count: Int) = guarded {
        session?.wav?.write(chunk, count)
    }

    override fun closed() = Unit // the session ends with the run, not with the microphone

    override fun frame(frame: ListenFrame) = guarded {
        val s = session ?: return@guarded
        s.rows++
        val ms = s.rows * AudioRecordTunerEngine.HOP_SIZE * 1000.0 / s.rate
        val f = frame.frame
        val cells = listOf(
            s.rows.toString(), String.format(Locale.ROOT, "%.2f", ms),
            f.frequency?.let { String.format(Locale.ROOT, "%.2f", it) }.orEmpty(), String.format(Locale.ROOT, "%.3f", f.probability),
            level(f.level), level(f.minLevel), frame.decision.name, frame.sounding?.toString().orEmpty(),
            frame.onset?.let { if (it.fresh) "fresh" else "step" }.orEmpty(), frame.verdict?.let(::verdict).orEmpty(),
            frame.waited?.toString().orEmpty(), frame.expected?.toString().orEmpty(), frame.heardNow?.toString().orEmpty(),
            frame.advice.takeUnless { it == TunerAdvice.NONE }?.name.orEmpty(), frame.repeatHeld.toString(), frame.finishing.toString(),
            frame.hearing.name, frame.floor?.let(::level).orEmpty(),
        )
        s.csv.write(cells.joinToString(","))
        s.csv.newLine()
        s.csv.flush()
    }

    override fun line(tag: String, level: Char, line: String) = guarded {
        val s = session ?: return@guarded
        val event = line.substringBefore(' ')
        val rest = line.substringAfter(' ', missingDelimiterValue = "")
        val stamp = "$event frame=${s.rows} tag=$tag level=$level"
        s.log.write(if (rest.isEmpty()) stamp else "$stamp $rest")
        s.log.newLine()
        s.log.flush()
    }

    override fun end(marks: Map<Int, StepMark>) = guarded {
        val s = session ?: return@guarded
        close(s, marks)
    }

    private fun close(s: Session, marks: Map<Int, StepMark>) {
        session = null
        try {
            val spelled = marks.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.name}" }
            line(s, "session.end", "frames" to s.rows, "marks" to spelled)
        } finally {
            closeFiles(s)
        }
    }

    /** Closes every file of [s], each of them even when another fails, and throws the first failure. */
    private fun closeFiles(s: Session) {
        var failure: Exception? = null
        val closes = listOf({ s.log.close() }, { s.csv.close() }, { (s.wav ?: WavWriter(File(s.dir, AUDIO), s.rate)).close() })
        for (close in closes) {
            try {
                close()
            } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
    }

    private fun line(s: Session, event: String, vararg fields: Pair<String, Any?>) {
        s.log.write(LogLine.format(event, *fields))
        s.log.newLine()
        s.log.flush()
    }

    private fun newFolder(): File {
        val sessions = File(root, FOLDER)
        if (!sessions.isDirectory && !sessions.mkdirs()) throw IOException("cannot make $sessions")
        val old = sessions.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        for (dir in old.dropLast(KEEP - 1)) dir.deleteRecursively()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(now()))
        var dir = File(sessions, stamp)
        var n = 2
        while (dir.exists()) dir = File(sessions, "${stamp}_${n++}")
        if (!dir.mkdir()) throw IOException("cannot make $dir")
        return dir
    }

    /** Runs [block] under the lock; a failure of any kind is logged once and ends the session, never the run. */
    private inline fun guarded(block: () -> Unit) {
        synchronized(lock) {
            try {
                block()
            } catch (e: Exception) {
                logger.w(LogTags.FLUTE, "session.error", "message" to e.message, error = e)
                session?.let { s ->
                    session = null
                    runCatching { closeFiles(s) }
                }
            }
        }
    }

    private fun level(value: Float) = String.format(Locale.ROOT, "%.4f", value)

    private fun verdict(verdict: Verdict) = verdict.name.lowercase(Locale.ROOT)

    companion object {
        /** Sessions kept on the phone; starting one more deletes the oldest. */
        const val KEEP = 20
        const val FOLDER = "sessions"
        const val AUDIO = "audio.wav"
        const val FRAMES = "frames.csv"
        const val LOG = "session.log"
        const val HEADER = "frame,ms,f,p,level,minLevel,decision,sounding,onset,verdict,waited,expected,heardNow,advice,repeatHeld,finishing,hearing,floor"
        /** The rate of an empty `audio.wav` when the microphone never opened. */
        const val DEFAULT_RATE = 48_000
    }
}
