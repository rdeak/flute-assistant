// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixHoldTest {

    private val hold = FixHold(frames = 5)

    private fun silence(n: Int) = repeat(n) { hold.onFrame(TunerAdvice.NONE, silent = true) }

    @Test
    fun `nothing is held before a fix was said`() {
        silence(3)
        assertEquals(TunerAdvice.NONE, hold.held)
    }

    @Test
    fun `a fix stays through the silence after it and then goes`() {
        hold.onFrame(TunerAdvice.HEAD_JOINT_IN, silent = false)
        silence(5)
        assertEquals(TunerAdvice.HEAD_JOINT_IN, hold.held)
        silence(1)
        assertEquals(TunerAdvice.NONE, hold.held)
    }

    @Test
    fun `the last fix said is the one held, and saying it again starts the count again`() {
        hold.onFrame(TunerAdvice.ROLL_IN, silent = false)
        silence(4)
        hold.onFrame(TunerAdvice.ROLL_OUT, silent = false)
        silence(5)
        assertEquals(TunerAdvice.ROLL_OUT, hold.held)
    }

    @Test
    fun `other words, a new sound or the note playing take the line at once`() {
        hold.onFrame(TunerAdvice.HEAD_JOINT_OUT, silent = false)
        silence(1)
        assertEquals(TunerAdvice.NONE, hold.onFrame(TunerAdvice.COME_CLOSER, silent = false)) // other words
        hold.onFrame(TunerAdvice.HEAD_JOINT_OUT, silent = false)
        assertEquals(TunerAdvice.NONE, hold.onFrame(TunerAdvice.NONE, silent = false)) // in tune, or the note playing
        silence(1)
        assertEquals(TunerAdvice.NONE, hold.held) // and it does not come back
    }

    @Test
    fun `clearing drops what is held`() {
        hold.onFrame(TunerAdvice.HEAD_JOINT_IN, silent = false)
        hold.clear()
        assertEquals(TunerAdvice.NONE, hold.held)
    }

    @Test
    fun `the head joint and rolling are the fixes on the flute`() {
        val fixes = setOf(TunerAdvice.HEAD_JOINT_IN, TunerAdvice.HEAD_JOINT_OUT, TunerAdvice.ROLL_IN, TunerAdvice.ROLL_OUT)
        for (advice in TunerAdvice.entries) {
            if (advice in fixes) assertTrue("$advice", advice.isFluteFix) else assertFalse("$advice", advice.isFluteFix)
        }
    }

    @Test
    fun `the app holds a fix about five seconds`() {
        assertEquals(5.0, FixHold.HOLD_FRAMES * 2048 / 48_000.0, 0.1)
    }
}
