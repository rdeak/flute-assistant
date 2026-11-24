// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [rememberBounce]: a line's good news bounces in, and a line that is not good news stands at its size. */
@RunWith(AndroidJUnit4::class)
class BounceTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * "Perfect!" that gives way in the middle of its bounce (the note drifts out a moment later) must not leave the
     * next line drawn large: the spring ends with the good news, and "A little lower" stands at its size at once
     * (the review of 2026-11-24: it stayed up to 1.3 times its size until the next "Perfect!").
     */
    @Test
    fun good_news_that_ends_in_the_middle_of_its_bounce_leaves_the_line_at_its_size() {
        var on by mutableStateOf(false)
        var scale: () -> Float = { 1f }
        compose.mainClock.autoAdvance = false
        compose.setContent { scale = rememberBounce(on) }
        on = true
        after(100)
        assertTrue("the bounce should be under way: ${scale()}", scale() > 1.05f)
        on = false
        after(100)
        assertEquals(1f, scale(), 0f)
    }

    /** With the clock held, a change reaches the composition once it is applied (`waitForIdle`) and frames pass. */
    private fun after(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
    }

    @Test
    fun good_news_left_alone_springs_back_to_its_size() {
        var on by mutableStateOf(false)
        var scale: () -> Float = { 1f }
        compose.setContent { scale = rememberBounce(on) }
        on = true
        compose.waitForIdle()
        assertEquals(1f, scale(), 0.01f)
    }
}
