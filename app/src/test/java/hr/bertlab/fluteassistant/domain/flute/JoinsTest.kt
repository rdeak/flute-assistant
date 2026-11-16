// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs of joined notes (since 2026-11-16, the ⌒ key): what the staff draws and what Listen to me hears as one note. */
class JoinsTest {

    private fun q(midi: Int) = TuneNote(midi, QUARTER)
    private fun j(midi: Int) = TuneNote(midi, QUARTER, joined = true)

    @Test
    fun `a run of joined notes is one slur`() =
        assertEquals(listOf(JoinArc(0, 2, tie = false)), Joins.arcs(listOf(q(60), j(62), j(64))))

    @Test
    fun `the same note joined is a tie`() =
        assertEquals(listOf(JoinArc(0, 1, tie = true)), Joins.arcs(listOf(q(60), j(60))))

    @Test
    fun `a slur over a tie draws both`() =
        assertEquals(listOf(JoinArc(0, 2, tie = false), JoinArc(1, 2, tie = true)), Joins.arcs(listOf(q(60), j(62), j(62))))

    @Test
    fun `notes not joined draw nothing, and two runs draw two slurs`() {
        assertEquals(emptyList<JoinArc>(), Joins.arcs(listOf(q(60), q(62))))
        assertEquals(
            listOf(JoinArc(0, 1, tie = false), JoinArc(2, 3, tie = false)),
            Joins.arcs(listOf(q(60), j(62), q(64), j(65))),
        )
    }

    @Test
    fun `an octave is a slur on the staff and heard as one`() {
        val tune = listOf(q(60), j(72))
        assertEquals(listOf(JoinArc(0, 1, tie = false)), Joins.arcs(tune))
        assertTrue(Joins.heardWithBefore(tune, 1))
        assertFalse(Joins.joinsOtherLetter(tune, 1))
    }

    @Test
    fun `notes heard as one end where the letter changes`() {
        val tune = listOf(q(60), j(60), j(72), j(62), q(62))
        assertEquals(2, Joins.lastHeardWith(tune, 0))
        assertTrue(Joins.joinsOtherLetter(tune, 3))
        assertEquals(3, Joins.lastHeardWith(tune, 3))
        assertEquals(4, Joins.lastHeardWith(tune, 4))
        assertFalse(Joins.heardWithBefore(tune, 0))
        assertFalse("not joined: a written repeat", Joins.heardWithBefore(tune, 4))
    }

    @Test
    fun `nothing to join from after a rest, a breath, nothing, or at the cap`() {
        assertFalse(Joins.canJoinAfter(emptyList()))
        assertFalse(Joins.canJoinAfter(listOf(q(60), TuneRest(QUARTER))))
        assertFalse(Joins.canJoinAfter(listOf(q(60), TuneBreath)))
        assertFalse(Joins.canJoinAfter(List(Tune.MAX_NOTES) { q(60) }))
        assertTrue(Joins.canJoinAfter(listOf(q(60))))
    }

    @Test
    fun `black notes join by pitch class as the white ones do`() {
        assertEquals(listOf(JoinArc(0, 1, tie = false)), Joins.arcs(listOf(q(65), j(66))))
        assertTrue(Joins.joinsOtherLetter(listOf(q(65), j(66)), 1))
        assertEquals(listOf(JoinArc(0, 1, tie = true)), Joins.arcs(listOf(q(66), j(66))))
        assertTrue(Joins.heardWithBefore(listOf(q(66), j(78)), 1))
    }
}
