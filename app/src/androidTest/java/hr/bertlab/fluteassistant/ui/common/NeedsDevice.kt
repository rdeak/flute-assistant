// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.os.Build
import org.junit.Assume.assumeFalse

/**
 * The first line of a test that only a device (the emulator) can run. These tests are compiled
 * twice: as instrumented tests, and as JVM tests under Robolectric, where nearly all of them pass
 * in a fraction of the time. The ones that read the screen's pixels, or need a real gesture's
 * cancel, see something else there, so on the JVM they are skipped, not failed.
 */
fun needsDevice() = assumeFalse("only a device draws this as a phone does", Build.FINGERPRINT == "robolectric")
