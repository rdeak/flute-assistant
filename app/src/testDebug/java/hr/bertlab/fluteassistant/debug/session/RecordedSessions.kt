// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.ui.flute.ListenPath
import java.io.File

/**
 * The pinned sessions under a folder (`src/testDebug/resources/sessions`, see its README): each
 * is replayed and must earn the marks its `pin marks=` line says. [check] returns one message
 * per folder that does not, named after the folder, a folder that is no session (no
 * `session.log`, a file missing or unreadable) among them; nothing to check gives none.
 */
object RecordedSessions {

    fun check(dir: File): List<String> =
        dir.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.mapNotNull { folder ->
            if (!File(folder, FileSessionRecorder.LOG).isFile) return@mapNotNull "${folder.name}: no session.log"
            try {
                val session = Session.load(folder)
                val pin = session.pin ?: return@mapNotNull "${folder.name}: no pin line in session.log (pin marks=0:FIRST_TRY,...)"
                val run = ListenPath.run(session.audio.samples, session.audio.sampleRate, session.tune, floor = session.floor)
                if (run.marks == pin) null
                else "${folder.name}: pin ${Session.spell(pin)} but replay ${Session.spell(run.marks)}"
            } catch (e: Exception) {
                "${folder.name}: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
}
