// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the manifest itself declares. `TurningThePhoneTest` cannot see these flags: on Android 17 an
 * app targeting API 37 has its activity report them whether it declares them or not, and the only
 * emulator image is API 37. Older Android still re-creates the activity when a keyboard is attached,
 * which would stop a running tune, so the manifest has to say so. Unit tests run in the module folder.
 */
class ManifestFlagsTest {

    private val flags = Regex("""android:configChanges="([^"]+)"""")
        .find(File("src/main/AndroidManifest.xml").readText())!!
        .groupValues[1]
        .split("|")

    @Test
    fun `a keyboard attached during a tune does not re-create the activity`() {
        for (flag in listOf("keyboard", "keyboardHidden", "navigation")) {
            assertTrue("$flag is handled by the activity", flag in flags)
        }
    }

    @Test
    fun `the language switch can still re-create it`() {
        assertFalse("the locale is handled by the activity", "locale" in flags)
    }
}
