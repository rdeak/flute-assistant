// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.core.log.AndroidLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * The engine's [AudioTap] on a real microphone (the emulator's: silence under `-no-audio`, which
 * is enough, the hops still come). Every hop the detector sees reaches the tap, and none after
 * the collector cancelled: the debug build's session recorder keeps the hops, and a "Your turn"
 * run started within one hop (43 ms) of the one before must not begin with that run's last hop.
 */
@RunWith(AndroidJUnit4::class)
class AudioRecordTunerEngineTest {

    private class CountingTap : AudioTap {
        @Volatile var cancelled = false
        @Volatile var opened = false
        @Volatile var closed = false
        val hops = AtomicInteger()
        val late = AtomicInteger()

        override fun opened(sampleRate: Int, source: String) {
            opened = true
        }

        override fun samples(chunk: FloatArray, count: Int) {
            hops.incrementAndGet()
            if (cancelled) late.incrementAndGet()
        }

        override fun closed() {
            closed = true
        }
    }

    @Test
    fun theTapGetsEveryHopAndNoneAfterTheCollectorCancels() = runBlocking {
        assumeTrue("granting a runtime permission needs API 28", Build.VERSION.SDK_INT >= 28)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val tap = CountingTap()
        val engine = AudioRecordTunerEngine(context, AndroidLogger(debuggable = true), tap)
        val frames = AtomicInteger()

        val job = launch { engine.pitchFrames().collect { frames.incrementAndGet() } }
        withTimeout(10_000) { while (frames.get() < 10) delay(20) }
        // cancel() marks the engine's producer cancelled before it returns; the hop its blocking
        // read is in the middle of returns up to 43 ms later and must not reach the tap.
        job.cancel()
        tap.cancelled = true
        job.join()

        assertTrue("the tap was told the microphone opened", tap.opened)
        assertTrue("the tap was told the microphone closed", tap.closed)
        assertEquals("hops handed to the tap after the collector cancelled", 0, tap.late.get())
        assertTrue("the tap saw every frame the collector got (${tap.hops.get()} of ${frames.get()})", tap.hops.get() >= frames.get())
    }
}
