// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import android.media.AudioTrack
import android.os.Process
import hr.bertlab.fluteassistant.audio.LowLatencyStream
import hr.bertlab.fluteassistant.audio.toneAmplitude
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FluteTonePlayer] on a streaming [AudioTrack] ([LowLatencyStream]), after the metronome's player: a dedicated
 * URGENT_AUDIO thread renders small blocks with [SustainedToneSynth] and writes them with
 * blocking writes from [open] until [close], writing silence while no note is held, so a press
 * never waits for a track to open. Note on and off reach the thread through a lock-free
 * command queue drained before every block, so the UI thread never blocks. Media attributes,
 * no audio focus request: a metronome left running keeps ticking under the flute.
 *
 * On [close] the thread lets a sounding note release and plays the track buffer out before it
 * gives the track back ([ToneRenderLoop]), so leaving the tab fades rather than clicks; the
 * caller never waits for that, and a new [open] meanwhile (the child leaves the tab and comes
 * straight back; a turn of the phone does not close the player) starts its own track at
 * once rather than waiting for the fade, so the two overlap for a fraction of a second. Every
 * block is written in full ([writeFully]), even through a route rebuild.
 *
 * Logs under [LogTags.FLUTE]: `tone.track_open` (rate, block and buffer frames),
 * `tone.track_closed` (underruns) and every failure as `tone.error`.
 */
@Singleton
class AudioTrackFluteTonePlayer @Inject constructor(private val logger: Logger) : FluteTonePlayer {

    private val commands = ConcurrentLinkedQueue<ToneCommand>()

    // Guarded by synchronized(this). A thread is held here only while it is meant to run: close()
    // and the thread's own exit both clear it.
    private var thread: RenderThread? = null

    @Synchronized
    override fun open() {
        if (thread != null) return
        commands.clear()
        thread = RenderThread().also { it.start() }
    }

    override fun noteOn(frequencyHz: Double, volume: Float) {
        open()
        commands.add(ToneCommand.On(frequencyHz, toneAmplitude(volume)))
    }

    override fun glide(frequencyHz: Double, volume: Float) {
        open()
        commands.add(ToneCommand.Glide(frequencyHz, toneAmplitude(volume)))
    }

    override fun noteOff() {
        commands.add(ToneCommand.Off)
    }

    @Synchronized
    override fun close() {
        val current = thread ?: return
        current.running = false // the thread releases any note, then exits; no join on the caller
        thread = null
    }

    /** Called by a render thread when it finishes for any reason, so a failed open is retried by the next note. */
    @Synchronized
    private fun onThreadExited(exited: RenderThread) {
        if (thread === exited) thread = null
    }

    private inner class RenderThread : Thread("FluteAudio") {

        @Volatile
        var running = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var stream: LowLatencyStream? = null
            try {
                stream = LowLatencyStream.open() // may throw on unsupported formats
                val track = stream.track
                val sampleRate = stream.sampleRate
                if (!stream.isInitialised) {
                    logger.w(LogTags.FLUTE, "tone.error", "reason" to "track_uninitialised", "rate" to sampleRate)
                    return
                }
                logger.d(
                    LogTags.FLUTE, "tone.track_open",
                    "rate" to sampleRate, "blockFrames" to stream.blockFrames, "bufferFrames" to stream.bufferFrames,
                )

                val loop = ToneRenderLoop(
                    SustainedToneSynth(sampleRate), commands, sampleRate, stream.blockFrames, stream.bufferFrames,
                )

                track.play()
                while (loop.next(running)) {
                    val written = writeFully(loop.block, running = { running }) { block, offset, count ->
                        track.write(block, offset, count, AudioTrack.WRITE_BLOCKING)
                    }
                    if (written < 0) {
                        logger.w(LogTags.FLUTE, "tone.error", "reason" to "write_failed", "code" to written)
                        break
                    }
                    if (written == 0) break // told to close while the route was being rebuilt
                }
            } catch (e: RuntimeException) {
                // UnsupportedOperationException / IllegalArgumentException from the builder,
                // IllegalStateException from play(): report and stop instead of killing the process.
                logger.w(LogTags.FLUTE, "tone.error", "reason" to "exception", "message" to e.message, error = e)
            } finally {
                stream?.let {
                    logger.d(LogTags.FLUTE, "tone.track_closed", "underruns" to it.track.underrunCount)
                    it.close()
                }
                onThreadExited(this)
            }
        }
    }
}
