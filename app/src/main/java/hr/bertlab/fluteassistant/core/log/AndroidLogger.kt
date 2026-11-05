// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.core.log

import android.util.Log

/**
 * [Logger] on `android.util.Log`. Verbose lines need `Log.isLoggable` (the `setprop` switch);
 * debug lines are on in a [debuggable] build or with the same switch; the rest always write.
 */
class AndroidLogger(private val debuggable: Boolean) : Logger {

    override fun isVerbose(tag: String): Boolean = Log.isLoggable(tag, Log.VERBOSE)

    override fun v(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        if (isVerbose(tag)) Log.v(tag, LogLine.format(event, *fields))
    }

    override fun d(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        if (debuggable || Log.isLoggable(tag, Log.DEBUG)) Log.d(tag, LogLine.format(event, *fields))
    }

    override fun i(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        Log.i(tag, LogLine.format(event, *fields))
    }

    override fun w(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable?) {
        Log.w(tag, LogLine.format(event, *fields), error)
    }

    override fun e(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable?) {
        Log.e(tag, LogLine.format(event, *fields), error)
    }
}
