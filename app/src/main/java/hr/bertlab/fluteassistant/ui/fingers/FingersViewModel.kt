// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Fingers tab's state (since 2026-11-17): which note it shows and whether the ♯♭ key waits, as [FingerPick] rules.
 * Activity-scoped like the other tabs, so the note survives a tab switch and a turn; nothing is stored, so a fresh
 * start opens on C. Logs `fingers.note` (pitchClass, notes) at `d` when the note on the screen changes.
 */
@HiltViewModel
class FingersViewModel @Inject constructor(private val logger: Logger) : ViewModel() {
    private val _state = MutableStateFlow(FingerPick())
    val state: StateFlow<FingerPick> = _state.asStateFlow()

    fun tapLetter(letter: Int) = move(_state.value.tapLetter(letter))

    fun tapSign() = move(_state.value.tapSign())

    /** The tab was left: a waiting sign is dropped. */
    fun leave() = move(_state.value.leave())

    private fun move(next: FingerPick) {
        val before = _state.value
        _state.value = next
        if (next.pitchClass != before.pitchClass) {
            logger.d(LogTags.FINGERS, "fingers.note", "pitchClass" to next.pitchClass, "notes" to next.notes.size)
        }
    }
}
