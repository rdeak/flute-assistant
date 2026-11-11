// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.core.log

/** Where the debug build's [RecordingLogger] tees each line: the tag, the level letter and the line as formatted. */
fun interface LogSink {
    fun line(tag: String, level: Char, line: String)
}

/**
 * The debug build's [Logger]: every `d`, `i`, `w` and `e` line goes to [inner] (the
 * [AndroidLogger], so logcat is unchanged) and then to [sink], the session recorder, which keeps
 * the lines written while a "Your turn" run is open next to its audio (`ui/flute/CLAUDE.md`,
 * "Sessions"). Verbose lines are per-frame noise and go to [inner] only.
 */
class RecordingLogger(private val inner: Logger, private val sink: LogSink) : Logger {

    override fun isVerbose(tag: String): Boolean = inner.isVerbose(tag)

    override fun v(tag: String, event: String, vararg fields: Pair<String, Any?>) = inner.v(tag, event, *fields)

    override fun d(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        inner.d(tag, event, *fields)
        sink.line(tag, 'd', LogLine.format(event, *fields))
    }

    override fun i(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        inner.i(tag, event, *fields)
        sink.line(tag, 'i', LogLine.format(event, *fields))
    }

    override fun w(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable?) {
        inner.w(tag, event, *fields, error = error)
        sink.line(tag, 'w', LogLine.format(event, *fields))
    }

    override fun e(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable?) {
        inner.e(tag, event, *fields, error = error)
        sink.line(tag, 'e', LogLine.format(event, *fields))
    }
}
