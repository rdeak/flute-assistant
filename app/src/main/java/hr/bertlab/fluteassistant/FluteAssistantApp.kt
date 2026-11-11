// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FluteAssistantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Earlier builds kept a Room database of metronome presets that no screen could fill; it
        // went on 2026-11-09. This removes the empty file an update leaves behind, and does
        // nothing once it is gone.
        deleteDatabase("flute_assistant.db")
    }
}
