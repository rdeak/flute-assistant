// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TurningThePhoneTest {

    /**
     * A turn must not re-create the activity: a re-creation stops it, `FluteScreen` calls
     * `leaveTab()` when that happens, and that stops a tune that is playing back. The locale stays
     * out of the list because the language switch relies on re-creation.
     */
    @Test
    fun the_activity_handles_a_turn_itself() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handled = context.packageManager
            .getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
            .configChanges

        for ((name, flag) in listOf(
            "orientation" to ActivityInfo.CONFIG_ORIENTATION,
            "screenSize" to ActivityInfo.CONFIG_SCREEN_SIZE,
            "smallestScreenSize" to ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            "screenLayout" to ActivityInfo.CONFIG_SCREEN_LAYOUT,
        )) {
            assertTrue("$name is handled by the activity", (handled and flag) == flag)
        }
        assertEquals(
            "the locale is not handled by the activity",
            0,
            handled and ActivityInfo.CONFIG_LOCALE,
        )
    }
}
