// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.DOTTED_HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The writing rules by themselves, no clock and no coroutines: at the writing pace (200 a minute) a quarter note is 300 ms. */
class TuneWriterTest {

    private val writer = TuneWriter()

    /**
     * The letter key of the white note [midi] (0 = C to 6 = B), after choosing its octave: since 2026-11-18 the keys are
     * seven letters and four octave pills, and these tests name a key by the note it writes.
     */
    private fun TuneWriter.key(midi: Int): Int {
        chooseOctave(Math.floorDiv(midi - 60, 12) + 1)
        return NoteSpelling.letterOf(midi)
    }

    /** Presses the key of [midi] (null: the rest key) at [at], holds it for [heldMs] and lets go. */
    private fun tap(midi: Int?, at: Long, heldMs: Long) {
        writer.press(midi?.let { writer.key(it) }, at)
        writer.release(at + heldMs)
    }

    @Test
    fun `a press adds an eighth, which takes the length of the hold when let go`() {
        val press = writer.press(writer.key(60), 0)!!
        assertEquals(TuneWriter.Press(ended = null, rejoinGapMs = null, elapsedMs = 0), press)
        assertEquals(listOf(TuneNote(60, EIGHTH)), writer.tune)
        assertEquals(Held(60, index = 0), writer.held)
        writer.grow(QUARTER)
        assertEquals(listOf(TuneNote(60, QUARTER)), writer.tune)
        assertEquals(TuneWriter.Ended(60, heldMs = 600, length = HALF, added = true), writer.release(600))
        assertEquals(listOf(TuneNote(60, HALF)), writer.tune)
        assertNull(writer.held)
        assertNull("nothing held: nothing ends", writer.release(2000))
    }

    @Test
    fun `a held key grows at the writing pace whatever the metronome says`() {
        // 2026-11-17, the user: a held key is counted at the ring's fastest, 200 a minute, only to choose a length.
        assertEquals(300.0, TuneWriter.WRITING_PULSE.quarterMs, 0.0)
        assertEquals(listOf(225L, 450L, 750L, 1050L), Tune.growAtMs(TuneWriter.WRITING_PULSE))
        val edges = listOf(
            224L to EIGHTH, 225L to QUARTER, 449L to QUARTER, 450L to HALF,
            749L to HALF, 750L to DOTTED_HALF, 1049L to DOTTED_HALF, 1050L to WHOLE,
        )
        for ((heldMs, length) in edges) {
            val writer = TuneWriter()
            writer.press(writer.key(60), 0)
            assertEquals("held $heldMs ms", length, writer.release(heldMs)?.length)
        }
    }

    @Test
    fun `the rest key writes a rest the same way`() {
        writer.press(null, 0)
        assertEquals(Held(midi = null, index = 0), writer.held)
        assertEquals(TuneWriter.Ended(null, heldMs = 300, length = QUARTER, added = true), writer.release(300))
        assertEquals(listOf(TuneRest(QUARTER)), writer.tune)
    }

    @Test
    fun `a press over a held key ends that key first`() {
        writer.press(writer.key(60), 0)
        val press = writer.press(writer.key(62), 300)!!
        assertEquals(TuneWriter.Ended(60, heldMs = 300, length = QUARTER, added = true), press.ended)
        assertNull(press.rejoinGapMs)
        assertEquals(listOf(TuneNote(60, QUARTER), TuneNote(62, EIGHTH)), writer.tune)
        assertEquals(Held(62, index = 1), writer.held)
    }

    @Test
    fun `a note key pressed again inside the window goes on with its note, gap included`() {
        tap(60, at = 0, heldMs = 100)
        val press = writer.press(writer.key(60), 100 + TuneWriter.HOLD_REJOIN_MS)!!
        assertEquals(TuneWriter.HOLD_REJOIN_MS, press.rejoinGapMs)
        assertEquals("counted from the first press", 100 + TuneWriter.HOLD_REJOIN_MS, press.elapsedMs)
        assertEquals(Held(60, index = 0), writer.held)
        writer.release(300)
        assertEquals(listOf(TuneNote(60, QUARTER)), writer.tune)
    }

    @Test
    fun `a note key pressed again past the window is a new note`() {
        tap(60, at = 0, heldMs = 100)
        val press = writer.press(writer.key(60), 100 + TuneWriter.HOLD_REJOIN_MS + 1)!!
        assertNull(press.rejoinGapMs)
        assertEquals(2, writer.tune.size)
    }

    @Test
    fun `the rest key goes on with its rest however long the pause, from the length it has`() {
        tap(null, at = 0, heldMs = 300) // a quarter rest
        val press = writer.press(null, 5_000)!!
        assertEquals(4_700L, press.rejoinGapMs)
        assertEquals("the pause is not counted", 300L, press.elapsedMs)
        writer.release(5_300) // another 300 ms: a half rest in all
        assertEquals(listOf(TuneRest(HALF)), writer.tune)
    }

    @Test
    fun `forget, another key, a breath, an erase and another book each end the chance to go on`() {
        val acts: List<(TuneWriter) -> Unit> = listOf(
            { it.forget() },
            { it.press(1, 101); it.release(102) }, // the D key
            { it.breath() },
            { it.erase() },
            { it.open(listOf(TuneNote(60, EIGHTH))) },
        )
        for (act in acts) {
            val writer = TuneWriter()
            writer.press(writer.key(60), 0)
            writer.release(100)
            act(writer)
            assertNull(writer.press(writer.key(60), 110)!!.rejoinGapMs)
        }
    }

    @Test
    fun `at the cap a key is held but nothing is added`() {
        val full = TuneWriter(List(Tune.MAX_NOTES) { TuneNote(60, EIGHTH) })
        full.press(1, 0)
        assertEquals(Held(62, index = null), full.held)
        full.grow(WHOLE)
        assertEquals(TuneWriter.Ended(62, heldMs = 300, length = QUARTER, added = false), full.release(300))
        assertEquals(Tune.MAX_NOTES, full.tune.size)
        assertFalse(full.breath().added)
    }

    @Test
    fun `a breath is never added twice in a row`() {
        tap(60, at = 0, heldMs = 100)
        assertTrue(writer.breath().added)
        assertFalse(writer.breath().added)
        assertEquals(listOf(TuneNote(60, EIGHTH), TuneBreath), writer.tune)
    }

    @Test
    fun `erase drops the last step, but not while a key is held or from an empty tune`() {
        assertFalse(writer.erase().erased)
        tap(60, at = 0, heldMs = 100)
        writer.press(writer.key(62), 1_000)
        assertFalse(writer.erase().erased)
        writer.release(1_100)
        assertTrue(writer.erase().erased)
        assertEquals(listOf(TuneNote(60, EIGHTH)), writer.tune)
    }

    // ---- The join key (2026-11-16): ⌒ joins the next note to the last one, one tap per pair ----

    @Test
    fun `the join key joins the next note to the last one`() {
        tap(60, at = 0, heldMs = 300)
        assertTrue(writer.join())
        assertTrue(writer.joinWaiting)
        tap(62, at = 1000, heldMs = 300)
        assertEquals(TuneNote(62, QUARTER, joined = true), writer.tune[1])
        assertFalse(writer.joinWaiting)
        tap(64, at = 2000, heldMs = 300)
        assertFalse("one tap joins one pair", (writer.tune[2] as TuneNote).joined)
    }

    @Test
    fun `a second tap cancels the join`() {
        tap(60, at = 0, heldMs = 300)
        assertTrue(writer.join())
        assertFalse(writer.join())
        assertFalse(writer.joinWaiting)
        tap(62, at = 1000, heldMs = 300)
        assertFalse((writer.tune[1] as TuneNote).joined)
    }

    @Test
    fun `the rest key cancels a waiting join`() {
        tap(60, at = 0, heldMs = 300)
        writer.join()
        assertEquals("the press says it dropped the join", TuneWriter.Dropped(join = true), writer.press(null, 1000)!!.dropped)
        writer.release(1300)
        assertFalse(writer.joinWaiting)
        assertEquals("a note key spends a join, it drops nothing", TuneWriter.Dropped(), writer.press(writer.key(62), 2000)!!.dropped)
        writer.release(2300)
        assertFalse((writer.tune[2] as TuneNote).joined)
    }

    @Test
    fun `the breath cancels a waiting join`() {
        tap(60, at = 0, heldMs = 300)
        writer.join()
        assertEquals("the breath says it dropped the join", TuneWriter.Dropped(join = true), writer.breath().dropped)
        assertFalse(writer.joinWaiting)
    }

    @Test
    fun `erase while a join waits cancels the join and erases nothing, the next erase erases`() {
        tap(60, at = 0, heldMs = 300)
        writer.join()
        assertEquals(TuneWriter.Erase(TuneWriter.Dropped(join = true), erased = false), writer.erase())
        assertEquals(1, writer.tune.size)
        assertFalse(writer.joinWaiting)
        assertEquals(TuneWriter.Erase(TuneWriter.Dropped(), erased = true), writer.erase())
        assertTrue(writer.tune.isEmpty())
    }

    @Test
    fun `nothing to join from refuses the key`() {
        assertFalse(Joins.canJoinAfter(writer.tune))
        assertFalse(writer.join())
        assertFalse(writer.joinWaiting)
        tap(60, at = 0, heldMs = 300)
        writer.breath()
        assertFalse(Joins.canJoinAfter(writer.tune))
        assertFalse(writer.join())
        tap(null, at = 1000, heldMs = 300)
        assertFalse(writer.join())
    }

    @Test
    fun `a join while a key is held joins the held note to the next`() {
        writer.press(writer.key(60), 0)
        assertTrue(writer.join())
        writer.press(writer.key(62), 200) // ends 60
        assertEquals(TuneNote(60, EIGHTH), writer.tune[0])
        assertEquals(TuneNote(62, EIGHTH, joined = true), writer.tune[1])
    }

    @Test
    fun `a join ends the rejoin window, so the same key again is a tie of two notes`() {
        tap(60, at = 0, heldMs = 400)
        writer.join()
        writer.press(writer.key(60), 420) // 20 ms after the release: inside HOLD_REJOIN_MS, but the join came between
        assertEquals(2, writer.tune.size)
        assertEquals(TuneNote(60, EIGHTH, joined = true), writer.tune[1])
    }

    @Test
    fun `dropWaiting says whether a join waited, and another book drops it`() {
        tap(60, at = 0, heldMs = 300)
        writer.join()
        assertEquals(TuneWriter.Dropped(join = true), writer.dropWaiting())
        assertEquals(TuneWriter.Dropped(), writer.dropWaiting())
        writer.join()
        writer.open(emptyList())
        assertFalse(writer.joinWaiting)
    }

    @Test
    fun `a key that bounces while a join waits keeps the join for the next note`() {
        // The final review of 2026-11-16: a touch screen reporting one hold as two (HOLD_REJOIN_MS) spent the join.
        writer.press(writer.key(60), 0)
        writer.join()
        writer.release(300)
        writer.press(writer.key(60), 320) // the same hold going on
        assertTrue(writer.joinWaiting)
        writer.press(writer.key(62), 800)
        assertTrue((writer.tune[1] as TuneNote).joined)
    }

    // ---- The ♯♭ key (2026-11-17): the next note its letter's sharp or flat, one tap per note ----

    @Test
    fun `the sign key makes the next note its letter's sharp or flat, once`() {
        assertTrue(writer.sign())
        assertTrue(writer.signWaiting)
        assertEquals(63, writer.writes(writer.key(64)))
        writer.press(writer.key(64), 0)
        assertEquals(listOf(TuneNote(63, EIGHTH)), writer.tune)
        assertEquals(Held(63, index = 0, key = 2), writer.held)
        assertFalse("spent by the note", writer.signWaiting)
        assertEquals(64, writer.writes(writer.key(64)))
        writer.release(300)
        assertEquals(listOf(TuneNote(63, QUARTER)), writer.tune)
        writer.press(writer.key(64), 1000)
        assertEquals(TuneNote(64, EIGHTH), writer.tune[1])
    }

    @Test
    fun `a second tap drops the sign`() {
        assertTrue(writer.sign())
        assertFalse(writer.sign())
        assertFalse(writer.signWaiting)
        assertEquals(64, writer.writes(writer.key(64)))
    }

    @Test
    fun `while the sign waits D and A write nothing and do not end a held key`() {
        writer.press(writer.key(60), 0)
        writer.sign()
        assertNull(writer.writes(writer.key(62)))
        assertNull(writer.writes(writer.key(69)))
        assertEquals("C6 takes its sharp since the range runs to C7", 85, writer.writes(writer.key(84)))
        assertNull(writer.press(writer.key(62), 100))
        assertEquals(Held(60, index = 0), writer.held)
        assertEquals(listOf(TuneNote(60, EIGHTH)), writer.tune)
        assertTrue(writer.signWaiting)
    }

    @Test
    fun `the rest and the breath leave the sign waiting`() {
        tap(60, at = 0, heldMs = 300)
        writer.sign()
        tap(null, at = 1000, heldMs = 300)
        assertTrue(writer.signWaiting)
        writer.breath()
        assertTrue(writer.signWaiting)
        tap(65, at = 2000, heldMs = 300)
        assertEquals(TuneNote(66, QUARTER), writer.tune.last())
    }

    @Test
    fun `erase drops a waiting sign and a waiting join together and erases nothing`() {
        tap(60, at = 0, heldMs = 300)
        writer.join()
        writer.sign()
        assertEquals(TuneWriter.Erase(TuneWriter.Dropped(join = true, sign = true), erased = false), writer.erase())
        assertEquals(1, writer.tune.size)
        assertFalse(writer.joinWaiting)
        assertFalse(writer.signWaiting)
        assertTrue(writer.erase().erased)
        assertTrue(writer.tune.isEmpty())
    }

    @Test
    fun `erase drops a waiting sign alone and erases nothing`() {
        tap(60, at = 0, heldMs = 300)
        writer.sign()
        assertEquals(TuneWriter.Erase(TuneWriter.Dropped(sign = true), erased = false), writer.erase())
        assertEquals(1, writer.tune.size)
        assertFalse(writer.signWaiting)
    }

    @Test
    fun `dropWaiting says whether a sign waited, and another book drops it`() {
        writer.sign()
        assertEquals(TuneWriter.Dropped(sign = true), writer.dropWaiting())
        assertEquals(TuneWriter.Dropped(), writer.dropWaiting())
        writer.sign()
        writer.open(emptyList())
        assertFalse(writer.signWaiting)
    }

    @Test
    fun `a bounce on a signed key goes on with the signed note`() {
        writer.sign()
        writer.press(writer.key(64), 0)
        writer.release(400)
        val press = writer.press(writer.key(64), 430) // 30 ms after the release: the same hold going on, the key white again
        assertEquals(30L, press?.rejoinGapMs)
        assertEquals(listOf(TuneNote(63, QUARTER)), writer.tune)
        assertEquals(Held(63, index = 0, key = 2), writer.held)
    }

    @Test
    fun `a tap on the sign while a key is held keeps the hold and the sign waits for the next press`() {
        writer.press(writer.key(60), 0)
        assertTrue(writer.sign())
        assertEquals(Held(60, index = 0), writer.held)
        writer.press(writer.key(65), 300)
        assertEquals(listOf(TuneNote(60, QUARTER), TuneNote(66, EIGHTH)), writer.tune)
    }

    @Test
    fun `the sign ends the rejoin window, so the same key again is a new signed note`() {
        tap(64, at = 0, heldMs = 200)
        writer.sign()
        writer.press(writer.key(64), 220) // 20 ms after the release: inside HOLD_REJOIN_MS, but the sign came between
        assertEquals(listOf(TuneNote(64, EIGHTH), TuneNote(63, EIGHTH)), writer.tune)
    }

    @Test
    fun `a sign and a join together write a signed joined note`() {
        tap(60, at = 0, heldMs = 300)
        writer.join()
        writer.sign()
        tap(67, at = 1000, heldMs = 300)
        assertEquals(TuneNote(68, QUARTER, joined = true), writer.tune[1])
        assertFalse(writer.joinWaiting)
        assertFalse(writer.signWaiting)
    }

    @Test
    fun `a signed press at the cap sounds, adds nothing and spends the sign`() {
        val full = TuneWriter(List(Tune.MAX_NOTES) { TuneNote(60, EIGHTH) })
        full.sign()
        full.press(6, 0) // the B key
        assertEquals(Held(70, index = null, key = 6), full.held)
        assertFalse(full.signWaiting)
        assertEquals(Tune.MAX_NOTES, full.tune.size)
    }

    @Test
    fun `a bounce on a key held when the sign was tapped goes on with its note and keeps the sign`() {
        // The final review of 2026-11-17: D held, ♯♭ tapped (D writes nothing now), the screen splits the hold.
        writer.press(writer.key(62), 0)
        writer.sign()
        writer.release(300)
        assertTrue("the same hold going on is taken", writer.takes(writer.key(62), 320))
        assertFalse("a fresh press of D is not", writer.takes(writer.key(62), 500))
        val press = writer.press(writer.key(62), 320)
        assertEquals(20L, press?.rejoinGapMs)
        assertEquals(Held(62, index = 0), writer.held)
        assertTrue("the sign still waits for the next note", writer.signWaiting)
        assertEquals(listOf(TuneNote(62, QUARTER)), writer.tune)
    }

    // ---- The octave pills (2026-11-18): the letter keys write in the chosen octave until another is chosen ----

    @Test
    fun `the keys write in the chosen octave until another is chosen`() {
        assertEquals(1, writer.octave)
        assertTrue(writer.chooseOctave(2))
        writer.press(0, 0)
        writer.release(300)
        writer.press(1, 1000)
        writer.release(1300)
        assertEquals(listOf(TuneNote(72, QUARTER), TuneNote(74, QUARTER)), writer.tune)
        assertTrue(writer.chooseOctave(1))
        writer.press(6, 2000)
        assertEquals(Held(71, index = 2), writer.held)
    }

    @Test
    fun `the chosen pill again changes nothing`() {
        writer.press(0, 0)
        writer.release(300)
        assertFalse(writer.chooseOctave(1))
        assertEquals("the same hold goes on", 60L, writer.press(0, 360)?.rejoinGapMs)
        assertFalse(writer.chooseOctave(0))
        assertFalse(writer.chooseOctave(5))
        assertEquals(1, writer.octave)
    }

    @Test
    fun `a pill ends the rejoin window`() {
        writer.press(0, 0)
        writer.release(300)
        writer.chooseOctave(2)
        assertNull("a new note, not the hold going on", writer.press(0, 360)?.rejoinGapMs)
        assertEquals(listOf(60, 72), writer.tune.map { (it as TuneNote).midi })
    }

    @Test
    fun `a pill leaves a held note and a waiting sign and join`() {
        writer.press(0, 0)
        writer.join()
        writer.sign()
        writer.chooseOctave(3)
        assertEquals(Held(60, index = 0), writer.held)
        assertTrue(writer.joinWaiting)
        assertTrue(writer.signWaiting)
        assertEquals(60, writer.release(300)?.midi)
    }

    @Test
    fun `a bounce on a key held when a pill was tapped goes on with its note`() {
        // The pill's twin of the ♯♭ rule: C held, pill 2 tapped, the screen splits the hold.
        writer.press(0, 0)
        writer.chooseOctave(2)
        writer.release(300)
        assertEquals(20L, writer.press(0, 320)?.rejoinGapMs)
        assertEquals(Held(60, index = 0), writer.held)
        assertEquals(listOf(TuneNote(60, QUARTER)), writer.tune)
    }

    @Test
    fun `in octave four only c writes`() {
        writer.chooseOctave(4)
        assertEquals(96, writer.writes(0))
        for (letter in 1..6) assertNull("$letter", writer.writes(letter))
        writer.sign()
        assertNull("cis4 is past the range", writer.writes(0))
    }

    @Test
    fun `another book keeps the octave`() {
        writer.chooseOctave(3)
        writer.open(listOf(TuneNote(67, QUARTER)))
        assertEquals(3, writer.octave)
    }
}
