// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSchedulerTest {

    private val sampleRate = 48_000
    private val base = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.FOUR_FOUR)

    private fun events(config: MetronomeConfig, count: Int, scheduler: ClickScheduler = ClickScheduler(sampleRate)) =
        List(count) { scheduler.next(config) }

    @Test
    fun `a click on every beat in four four`() {
        val e = events(base, 5)
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L, 96_000L), e.map { it.frame })
        assertEquals(
            listOf(BeatStrength.STRONG, BeatStrength.WEAK, BeatStrength.WEAK, BeatStrength.WEAK, BeatStrength.STRONG),
            e.map { it.strength },
        )
        assertEquals(listOf(0, 1, 2, 3, 0), e.map { it.beatIndex })
        assertEquals(listOf(0L, 0L, 0L, 0L, 1L), e.map { it.barIndex })
    }

    @Test
    fun `six eight has a secondary accent on beat four`() {
        val e = events(base.copy(timeSignature = TimeSignature.SIX_EIGHT), 7)
        assertEquals(
            listOf(
                BeatStrength.STRONG, BeatStrength.WEAK, BeatStrength.WEAK,
                BeatStrength.MEDIUM, BeatStrength.WEAK, BeatStrength.WEAK, BeatStrength.STRONG,
            ),
            e.map { it.strength },
        )
    }

    @Test
    fun `tempo changes apply from the next interval`() {
        val scheduler = ClickScheduler(sampleRate)
        val first = scheduler.next(base)                 // 120 bpm -> next in 24000
        val second = scheduler.next(base.copy(bpm = 60)) // 60 bpm  -> next in 48000
        val third = scheduler.next(base.copy(bpm = 60))
        assertEquals(0L, first.frame)
        assertEquals(24_000L, second.frame)
        assertEquals(72_000L, third.frame)
    }

    @Test
    fun `switching to a shorter bar wraps the beat index`() {
        val scheduler = ClickScheduler(sampleRate)
        repeat(4) { scheduler.next(base) }             // now at beat index 0 of bar 1? no: 4 events -> beat 0..3, next is beat 0
        scheduler.next(base)                           // beat 0 of bar 1
        scheduler.next(base)                           // beat 1
        scheduler.next(base)                           // beat 2  -> internal next index = 3
        val e = scheduler.next(base.copy(timeSignature = TimeSignature.TWO_FOUR))
        assertEquals(0, e.beatIndex)
        assertEquals(BeatStrength.STRONG, e.strength)
    }

    @Test
    fun `retime moves the pending click to the new tempo`() {
        val scheduler = ClickScheduler(sampleRate)
        scheduler.next(base.copy(bpm = 60))                    // frame 0, next would be 48000
        assertEquals(24_000L, scheduler.retime(base.copy(bpm = 120), notBefore = 0))
        assertEquals(24_000L, scheduler.next(base.copy(bpm = 120)).frame)
    }

    @Test
    fun `retime never schedules into already rendered audio`() {
        val scheduler = ClickScheduler(sampleRate)
        scheduler.next(base.copy(bpm = 60))                    // next at 48000
        assertEquals(30_000L, scheduler.retime(base.copy(bpm = 300), notBefore = 30_000))
    }

    @Test
    fun `an out of range bpm is clamped instead of breaking the timeline`() {
        val e = events(base.copy(bpm = 0), 2)
        assertEquals(sampleRate * 60L / MetronomeConfig.MIN_BPM, e[1].frame)
    }

    @Test
    fun `no drift over many beats at an awkward tempo`() {
        val config = base.copy(bpm = 137)
        val e = events(config, 138)
        val expected = 137.0 * sampleRate * 60.0 / 137.0 // = 60 seconds of audio
        assertTrue("frame ${e.last().frame} vs $expected", abs(e.last().frame - expected) <= 1.0)
    }

    @Test
    fun `reset restarts from a given frame`() {
        val scheduler = ClickScheduler(sampleRate)
        repeat(3) { scheduler.next(base) }
        scheduler.reset(startFrame = 1_000)
        val e = scheduler.next(base)
        assertEquals(1_000L, e.frame)
        assertEquals(0, e.beatIndex)
        assertEquals(0L, e.barIndex)
    }
}
