// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.fakes.FakeLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingersViewModelTest {
    private val logger = FakeLogger()
    private fun viewModel() = FingersViewModel(logger)

    @Test
    fun `it opens on c and logs nothing`() {
        assertEquals(FingerPick(), viewModel().state.value)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `a new note is logged once`() {
        val vm = viewModel()
        vm.tapLetter(5)
        assertEquals(FingerPick(9), vm.state.value)
        val event = logger.events("fingers.note").single()
        assertEquals('d', event.level)
        assertEquals(LogTags.FINGERS, event.tag)
        assertEquals(9, event["pitchClass"])
        assertEquals(3, event["notes"])
    }

    @Test
    fun `a tap that changes no note logs nothing`() {
        val vm = viewModel()
        vm.tapLetter(0) // already on C
        vm.tapSign()
        vm.tapLetter(1) // D has no black note
        assertTrue(logger.events.isEmpty())
        assertEquals(FingerPick(0, true), vm.state.value)
    }

    @Test
    fun `leaving drops a waiting sign`() {
        val vm = viewModel()
        vm.tapLetter(4)
        vm.tapSign()
        vm.leave()
        assertEquals(FingerPick(7), vm.state.value)
    }
}
