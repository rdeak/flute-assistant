// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.core.log

/**
 * Structured app logging. Every line is a dotted event name plus `key=value` fields (see
 * [LogLine]) under a per-subsystem tag from [LogTags], so `adb logcat -s FA.Tuner` shows one
 * subsystem and a script can split lines on spaces. Levels follow Android's:
 * - `v`: per-frame detail, off unless enabled for the tag at runtime with
 *   `adb shell setprop log.tag.FA.Tuner VERBOSE`; guard anything costly with [isVerbose],
 * - `d`: periodic summaries and state changes, on in debug builds (or per tag with `setprop`),
 * - `i`: lifecycle (a microphone opened, a session started), always on,
 * - `w` / `e`: failures, always on, with the exception when there is one.
 */
interface Logger {
    fun isVerbose(tag: String): Boolean
    fun v(tag: String, event: String, vararg fields: Pair<String, Any?>)
    fun d(tag: String, event: String, vararg fields: Pair<String, Any?>)
    fun i(tag: String, event: String, vararg fields: Pair<String, Any?>)
    fun w(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable? = null)
    fun e(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable? = null)
}

/** One tag per subsystem, short enough for every Android version. */
object LogTags {
    const val TUNER = "FA.Tuner"
    const val METRONOME = "FA.Metronome"
    const val PLAYBACK = "FA.Playback"
    const val FLUTE = "FA.Flute"
}
