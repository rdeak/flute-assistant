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

/** The writing rules by themselves, no clock and no coroutines: at the writing pace (130 a minute) a quarter note is about 462 ms. */
class TuneWriterTest {

    private val writer = TuneWriter()

    /**
     * How long a key is held to write [length] at the writing pace: the length's own time, halfway between the points
     * where it grows, so these tests follow [TuneWriter.WRITING_PULSE] and only the pace test pins its numbers.
     */
    private fun hold(length: NoteLength): Long = Tune.durationMs(length, TuneWriter.WRITING_PULSE)

    /** A quarter note's hold at the writing pace. */
    private val quarter = hold(QUARTER)

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
        val half = hold(HALF)
        assertEquals(TuneWriter.Ended(60, heldMs = half, length = HALF, added = true), writer.release(half))
        assertEquals(listOf(TuneNote(60, HALF)), writer.tune)
        assertNull(writer.held)
        assertNull("nothing held: nothing ends", writer.release(2000))
    }

    @Test
    fun `a held key grows at the writing pace whatever the metronome says`() {
        // 2026-11-17, the user: a held key is counted at its own pace, only to choose a length; 130 a minute since
        // 2026-11-23 ("reduce speed of adding notes to 130, just adding notes, not metronome").
        assertEquals(130, TuneWriter.WRITING_PULSE.bpm)
        assertEquals(60_000.0 / 130, TuneWriter.WRITING_PULSE.quarterMs, 1e-9)
        assertEquals(listOf(347L, 693L, 1154L, 1616L), Tune.growAtMs(TuneWriter.WRITING_PULSE))
        assertEquals(462L, TuneWriter.TAP_MS)
        val edges = listOf(
            346L to EIGHTH, 347L to QUARTER, 692L to QUARTER, 693L to HALF,
            1153L to HALF, 1154L to DOTTED_HALF, 1615L to DOTTED_HALF, 1616L to WHOLE,
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
        assertEquals(TuneWriter.Ended(null, heldMs = quarter, length = QUARTER, added = true), writer.release(quarter))
        assertEquals(listOf(TuneRest(QUARTER)), writer.tune)
    }

    @Test
    fun `a press over a held key ends that key first`() {
        writer.press(writer.key(60), 0)
        val press = writer.press(writer.key(62), quarter)!!
        assertEquals(TuneWriter.Ended(60, heldMs = quarter, length = QUARTER, added = true), press.ended)
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
        writer.release(quarter)
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
        tap(null, at = 0, heldMs = quarter) // a quarter rest
        val press = writer.press(null, 5_000)!!
        assertEquals(5_000L - quarter, press.rejoinGapMs)
        assertEquals("the pause is not counted", quarter, press.elapsedMs)
        writer.release(5_000 + quarter) // another quarter's hold: a half rest in all
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
        assertEquals(TuneWriter.Ended(62, heldMs = quarter, length = QUARTER, added = false), full.release(quarter))
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
        tap(60, at = 0, heldMs = quarter)
        assertTrue(writer.join())
        assertTrue(writer.joinWaiting)
        tap(62, at = 1000, heldMs = quarter)
        assertEquals(TuneNote(62, QUARTER, joined = true), writer.tune[1])
        assertFalse(writer.joinWaiting)
        tap(64, at = 2000, heldMs = quarter)
        assertFalse("one tap joins one pair", (writer.tune[2] as TuneNote).joined)
    }

    @Test
    fun `a second tap cancels the join`() {
        tap(60, at = 0, heldMs = quarter)
        assertTrue(writer.join())
        assertFalse(writer.join())
        assertFalse(writer.joinWaiting)
        tap(62, at = 1000, heldMs = quarter)
        assertFalse((writer.tune[1] as TuneNote).joined)
    }

    @Test
    fun `the rest key cancels a waiting join`() {
        tap(60, at = 0, heldMs = quarter)
        writer.join()
        assertEquals("the press says it dropped the join", TuneWriter.Dropped(join = true), writer.press(null, 1000)!!.dropped)
        writer.release(1000 + quarter)
        assertFalse(writer.joinWaiting)
        assertEquals("a note key spends a join, it drops nothing", TuneWriter.Dropped(), writer.press(writer.key(62), 2000)!!.dropped)
        writer.release(2000 + quarter)
        assertFalse((writer.tune[2] as TuneNote).joined)
    }

    @Test
    fun `the breath cancels a waiting join`() {
        tap(60, at = 0, heldMs = quarter)
        writer.join()
        assertEquals("the breath says it dropped the join", TuneWriter.Dropped(join = true), writer.breath().dropped)
        assertFalse(writer.joinWaiting)
    }

    @Test
    fun `erase while a join waits cancels the join and erases nothing, the next erase erases`() {
        tap(60, at = 0, heldMs = quarter)
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
        tap(60, at = 0, heldMs = quarter)
        writer.breath()
        assertFalse(Joins.canJoinAfter(writer.tune))
        assertFalse(writer.join())
        tap(null, at = 1000, heldMs = quarter)
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
        tap(60, at = 0, heldMs = quarter)
        writer.join()
        writer.press(writer.key(60), quarter + 20) // 20 ms after the release: inside HOLD_REJOIN_MS, but the join came between
        assertEquals(2, writer.tune.size)
        assertEquals(TuneNote(60, EIGHTH, joined = true), writer.tune[1])
    }

    @Test
    fun `dropWaiting says whether a join waited, and another book drops it`() {
        tap(60, at = 0, heldMs = quarter)
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
        writer.release(quarter)
        writer.press(writer.key(60), quarter + 20) // the same hold going on
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
        writer.release(quarter)
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
        tap(60, at = 0, heldMs = quarter)
        writer.sign()
        tap(null, at = 1000, heldMs = quarter)
        assertTrue(writer.signWaiting)
        writer.breath()
        assertTrue(writer.signWaiting)
        tap(65, at = 2000, heldMs = quarter)
        assertEquals(TuneNote(66, QUARTER), writer.tune.last())
    }

    @Test
    fun `erase drops a waiting sign and a waiting join together and erases nothing`() {
        tap(60, at = 0, heldMs = quarter)
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
        tap(60, at = 0, heldMs = quarter)
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
        writer.release(quarter)
        val press = writer.press(writer.key(64), quarter + 30) // 30 ms after the release: the same hold going on, the key white again
        assertEquals(30L, press?.rejoinGapMs)
        assertEquals(listOf(TuneNote(63, QUARTER)), writer.tune)
        assertEquals(Held(63, index = 0, key = 2), writer.held)
    }

    @Test
    fun `a tap on the sign while a key is held keeps the hold and the sign waits for the next press`() {
        writer.press(writer.key(60), 0)
        assertTrue(writer.sign())
        assertEquals(Held(60, index = 0), writer.held)
        writer.press(writer.key(65), quarter)
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
        tap(60, at = 0, heldMs = quarter)
        writer.join()
        writer.sign()
        tap(67, at = 1000, heldMs = quarter)
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
        writer.release(quarter)
        assertTrue("the same hold going on is taken", writer.takes(writer.key(62), quarter + 20))
        assertFalse("a fresh press of D is not", writer.takes(writer.key(62), quarter + 200))
        val press = writer.press(writer.key(62), quarter + 20)
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
        writer.release(quarter)
        writer.press(1, 1000)
        writer.release(1000 + quarter)
        assertEquals(listOf(TuneNote(72, QUARTER), TuneNote(74, QUARTER)), writer.tune)
        assertTrue(writer.chooseOctave(1))
        writer.press(6, 2000)
        assertEquals(Held(71, index = 2), writer.held)
    }

    @Test
    fun `the chosen pill again changes nothing`() {
        writer.press(0, 0)
        writer.release(quarter)
        assertFalse(writer.chooseOctave(1))
        assertEquals("the same hold goes on", 60L, writer.press(0, quarter + 60)?.rejoinGapMs)
        assertFalse(writer.chooseOctave(0))
        assertFalse(writer.chooseOctave(5))
        assertEquals(1, writer.octave)
    }

    @Test
    fun `a pill ends the rejoin window`() {
        writer.press(0, 0)
        writer.release(quarter)
        writer.chooseOctave(2)
        assertNull("a new note, not the hold going on", writer.press(0, quarter + 60)?.rejoinGapMs)
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
        assertEquals(60, writer.release(quarter)?.midi)
    }

    @Test
    fun `a bounce on a key held when a pill was tapped goes on with its note`() {
        // The pill's twin of the ♯♭ rule: C held, pill 2 tapped, the screen splits the hold.
        writer.press(0, 0)
        writer.chooseOctave(2)
        writer.release(quarter)
        assertEquals(20L, writer.press(0, quarter + 20)?.rejoinGapMs)
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

    // A step chosen on the staff, for the keys to change (since 2026-11-24, spec 2026-11-24-flute-edit-a-note-design.md).

    /** Writes c¹ d¹ e¹, each a quarter, a second apart. */
    private fun cde() {
        tap(60, at = 0, heldMs = quarter)
        tap(62, at = 1000, heldMs = quarter)
        tap(64, at = 2000, heldMs = quarter)
    }

    @Test
    fun `a tap selects a step, the same tap again unselects it, another moves it`() {
        cde()
        assertEquals(1, writer.select(1))
        assertEquals(1, writer.selected)
        assertEquals(2, writer.select(2))
        assertNull(writer.select(2))
        assertNull(writer.selected)
    }

    @Test
    fun `a key pressed on the selected step changes it in place, its length from the hold, and ends the choice`() {
        cde()
        writer.select(1)
        writer.press(writer.key(67), 3000)
        assertEquals(Held(67, index = 1, replaces = true), writer.held)
        assertEquals("chosen while the key is held", 1, writer.selected)
        val ended = writer.release(3000 + hold(HALF))!!
        assertTrue(ended.replaced)
        assertEquals(listOf(TuneNote(60, QUARTER), TuneNote(67, HALF), TuneNote(64, QUARTER)), writer.tune)
        assertNull("the key that changed it let the choice go", writer.selected)
        writer.press(writer.key(69), 4000)
        writer.release(4000 + hold(EIGHTH))
        assertEquals("the next key writes at the end", TuneNote(69, EIGHTH), writer.tune.last())
        assertEquals(4, writer.tune.size)
    }

    @Test
    fun `a bounce of the key goes on with the chosen step's note`() {
        cde()
        writer.select(1)
        writer.press(writer.key(67), 3000)
        writer.release(3000 + quarter)
        assertNull(writer.selected)
        writer.press(writer.key(67), 3000 + quarter + 30) // the touch screen split one hold in two
        assertEquals(Held(67, index = 1, replaces = true), writer.held)
        assertEquals("chosen again while it goes on", 1, writer.selected)
        writer.release(3000 + hold(HALF))
        assertEquals(listOf(TuneNote(60, QUARTER), TuneNote(67, HALF), TuneNote(64, QUARTER)), writer.tune)
        assertNull(writer.selected)
    }

    @Test
    fun `the rest key and the breath key change the selected step too`() {
        tap(60, at = 0, heldMs = quarter)
        tap(62, at = 1000, heldMs = quarter)
        writer.select(0)
        writer.press(null, 2000)
        writer.release(2000 + quarter)
        assertEquals(listOf(TuneRest(QUARTER), TuneNote(62, QUARTER)), writer.tune)
        assertNull("the rest key let the choice go", writer.selected)
        writer.select(0)
        val breath = writer.breath()
        assertTrue(breath.added)
        assertTrue(breath.replaced)
        assertEquals(listOf(TuneBreath, TuneNote(62, QUARTER)), writer.tune)
        assertNull("so did the breath key", writer.selected)
    }

    @Test
    fun `a selected step is changed at the cap, where nothing can be added`() {
        repeat(Tune.MAX_NOTES) { tap(60, at = it * 1000L, heldMs = quarter) }
        writer.select(5)
        writer.press(writer.key(62), 999_000)
        writer.release(999_000 + quarter)
        assertEquals(TuneNote(62, QUARTER), writer.tune[5])
        assertEquals(Tune.MAX_NOTES, writer.tune.size)
    }

    @Test
    fun `the sign and the octave apply to the replacement, and a join only when the key waits`() {
        tap(60, at = 0, heldMs = quarter)
        writer.join()
        tap(62, at = 1000, heldMs = quarter) // d joined to c
        writer.select(1)
        writer.press(writer.key(64), 2000)
        writer.release(2000 + quarter)
        assertEquals("a replacement made without ⌒ is not joined", TuneNote(64, QUARTER), writer.tune[1])
        writer.select(1)
        writer.sign()
        writer.chooseOctave(2)
        assertTrue(writer.join())
        writer.press(3, 3000) // F, with the sign, in octave 2
        writer.release(3000 + quarter)
        assertEquals(TuneNote(78, QUARTER, joined = true), writer.tune[1]) // F♯5, joined to the c before it
    }

    @Test
    fun `the join key needs a note before the selected step`() {
        tap(null, at = 0, heldMs = quarter)
        tap(62, at = 1000, heldMs = quarter)
        writer.select(1)
        assertFalse(TuneWriter.canJoin(writer.tune, writer.selected))
        assertFalse(writer.join())
        writer.select(0)
        assertFalse(TuneWriter.canJoin(writer.tune, 0))
        writer.unselect()
        assertTrue(TuneWriter.canJoin(writer.tune, null))
    }

    @Test
    fun `erase with a selection removes the selected step, after a waiting join or sign goes first`() {
        cde()
        writer.select(1)
        writer.sign()
        assertEquals(TuneWriter.Erase(TuneWriter.Dropped(sign = true), erased = false), writer.erase())
        assertEquals(TuneWriter.Erase(TuneWriter.Dropped(), erased = true, selected = true), writer.erase())
        assertEquals(listOf(TuneNote(60, QUARTER), TuneNote(64, QUARTER)), writer.tune)
        assertNull(writer.selected)
        assertTrue("no selection: the last step again", writer.erase().erased)
        assertEquals(listOf(TuneNote(60, QUARTER)), writer.tune)
    }

    @Test
    fun `the note after a removed or silenced step loses its join`() {
        tap(60, at = 0, heldMs = quarter)
        writer.join()
        tap(62, at = 1000, heldMs = quarter)
        writer.join()
        tap(64, at = 2000, heldMs = quarter) // c ⌒ d ⌒ e
        writer.select(1)
        writer.press(writer.key(65), 3000)
        writer.release(3000 + quarter)
        assertEquals("a note in its place keeps the next note's join", TuneNote(64, QUARTER, joined = true), writer.tune[2])
        writer.select(1)
        writer.press(null, 4000) // the chosen step becomes a rest
        writer.release(4000 + quarter)
        assertEquals(TuneNote(64, QUARTER), writer.tune[2])
        tap(60, at = 5000, heldMs = quarter)
        writer.join()
        tap(62, at = 6000, heldMs = quarter) // … c ⌒ d at the end
        writer.select(3)
        writer.erase() // removes the c the last d is joined to
        assertEquals(TuneNote(62, QUARTER), writer.tune.last())
    }

    @Test
    fun `a tap while a key is held selects nothing`() {
        tap(60, at = 0, heldMs = quarter)
        writer.press(writer.key(62), 1000)
        assertNull(writer.select(0))
        assertNull(writer.selected)
    }

    @Test
    fun `the selection never points outside the tune`() {
        tap(60, at = 0, heldMs = quarter)
        tap(62, at = 1000, heldMs = quarter)
        assertNull(writer.select(2))
        assertNull(writer.select(-1))
        writer.select(1)
        writer.open(listOf(TuneNote(60, QUARTER)))
        assertNull(writer.selected)
        writer.select(0)
        writer.erase()
        assertNull(writer.selected)
        assertTrue(writer.tune.isEmpty())
    }

    @Test
    fun `choosing a step drops a waiting join, which then runs from the note before the chosen step`() {
        cde()
        assertTrue(writer.join())
        writer.select(0)
        assertFalse("the note it would join from changed", writer.joinWaiting)
        writer.press(writer.key(65), 3000)
        writer.release(3000 + quarter)
        assertEquals(TuneNote(65, QUARTER), writer.tune[0])
        assertNull("no note before the first step", TuneWriter.joinFrom(writer.tune, 0))
        assertEquals(0, TuneWriter.joinFrom(writer.tune, 1))
        assertEquals("nothing chosen: the last note", 2, TuneWriter.joinFrom(writer.tune, null))
        writer.select(1)
        assertTrue(writer.join())
        writer.select(1) // let go: the join would now run from the last note
        assertFalse(writer.joinWaiting)
    }

    @Test
    fun `the rest key again after changing the chosen step does not go on with that rest`() {
        cde()
        writer.select(1)
        writer.press(null, 3000)
        writer.release(3000 + hold(HALF))
        assertEquals(TuneRest(HALF), writer.tune[1])
        writer.press(null, 5000)
        writer.release(5000 + quarter)
        assertEquals("the rest in the middle stays as it was", TuneRest(HALF), writer.tune[1])
        assertEquals("a new rest at the end", TuneRest(QUARTER), writer.tune.last())
    }

    @Test
    fun `letting the chosen step go ends the chance to go on with its rest`() {
        cde()
        writer.select(1)
        writer.press(null, 3000)
        writer.release(3000 + quarter)
        writer.unselect()
        writer.press(null, 5000)
        writer.release(5000 + quarter)
        assertEquals(
            listOf(TuneNote(60, QUARTER), TuneRest(QUARTER), TuneNote(64, QUARTER), TuneRest(QUARTER)),
            writer.tune,
        )
    }

    @Test
    fun `no two breaths side by side, by a breath on the chosen step or by erasing between two`() {
        tap(60, at = 0, heldMs = quarter)
        writer.breath()
        tap(62, at = 1000, heldMs = quarter)
        writer.breath()
        tap(64, at = 2000, heldMs = quarter) // c , d , e
        writer.select(2)
        assertFalse(writer.breath().added)
        assertEquals(TuneNote(62, QUARTER), writer.tune[2])
        writer.erase() // d goes, and the two breaths would meet
        assertEquals(listOf(TuneNote(60, QUARTER), TuneBreath, TuneNote(64, QUARTER)), writer.tune)
    }

    @Test
    fun `letting the chosen step go drops a waiting join`() {
        cde()
        writer.select(1)
        assertTrue(writer.join())
        assertTrue(writer.unselect())
        assertFalse("the note it would join from changed", writer.joinWaiting)
    }

    // The review of 2026-11-24.

    @Test
    fun `the join key does nothing while a key is held on the chosen step`() {
        // The join it set outlived the choice, and the next note, written at the end, came out joined to the last
        // step, even a rest.
        tap(60, at = 0, heldMs = quarter)
        tap(62, at = 1000, heldMs = quarter)
        tap(null, at = 2000, heldMs = quarter) // c d 💤
        writer.select(1)
        writer.press(writer.key(64), 3000)
        assertFalse("faded", TuneWriter.canJoin(writer.tune, writer.selected, writer.held))
        assertFalse("the next note is written at the end, not after the held one", writer.join())
        assertFalse(writer.joinWaiting)
        writer.release(3000 + quarter)
        tap(65, at = 4000, heldMs = quarter)
        assertEquals(
            listOf(TuneNote(60, QUARTER), TuneNote(64, QUARTER), TuneRest(QUARTER), TuneNote(65, QUARTER)),
            writer.tune,
        )
    }

    @Test
    fun `a key held at the end still joins its note to the next`() {
        tap(60, at = 0, heldMs = quarter)
        writer.press(writer.key(62), 1000)
        assertTrue(TuneWriter.canJoin(writer.tune, writer.selected, writer.held))
        assertTrue(writer.join())
        writer.press(writer.key(64), 1000 + quarter)
        assertEquals(TuneNote(64, EIGHTH, joined = true), writer.tune.last())
    }

    @Test
    fun `a key held on the chosen step keeps the choice until it is let go`() {
        cde()
        writer.select(1)
        writer.press(writer.key(65), 3000)
        assertFalse("nothing while a key is held, as a tap on a step", writer.unselect())
        assertEquals(1, writer.selected)
        writer.release(3000 + quarter)
        assertNull(writer.selected)
    }
}
