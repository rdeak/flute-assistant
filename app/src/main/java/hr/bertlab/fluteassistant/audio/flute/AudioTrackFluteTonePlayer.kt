// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * [FluteTonePlayer] on a streaming [AudioTrack], after the metronome's player: a dedicated
 * URGENT_AUDIO thread renders small blocks with [SustainedToneSynth] and writes them with
 * blocking writes from [open] until [close], writing silence while no note is held, so a press
 * never waits for a track to open. Note on and off reach the thread through a lock-free
 * command queue drained before every block, so the UI thread never blocks. Media attributes,
 * no audio focus request: a metronome left running keeps ticking under the flute.
 *
 * On [close] the thread lets a sounding note release and plays the track buffer out before it
 * gives the track back ([ToneRenderLoop]), so leaving the tab fades rather than clicks; the
 * caller never waits for that, and a new [open] meanwhile (a rotation) starts its own track at
 * once rather than waiting for the fade, so the two overlap for a fraction of a second. Every
 * block is written in full ([writeFully]), even through a route rebuild.
 *
 * Logs under [LogTags.FLUTE]: `tone.track_open` (rate, block and buffer frames),
 * `tone.track_closed` (underruns) and every failure as `tone.error`.
 */
@Singleton
class AudioTrackFluteTonePlayer @Inject constructor(private val logger: Logger) : FluteTonePlayer {

    private val commands = ConcurrentLinkedQueue<ToneCommand>()

    // Guarded by synchronized(this).
    private var thread: RenderThread? = null

    @Synchronized
    override fun open() {
        thread?.let { if (it.running) return }
        commands.clear()
        thread = RenderThread().also { it.start() }
    }

    override fun noteOn(frequencyHz: Double, volume: Float) {
        if (!isOpen()) open()
        commands.add(ToneCommand.On(frequencyHz, PEAK * volume.coerceIn(0f, 1f)))
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

    @Synchronized
    private fun isOpen(): Boolean = thread?.running == true

    /** Called by a render thread when it finishes for any reason, so a failed open is retried by the next note. */
    @Synchronized
    private fun onThreadExited(exited: RenderThread) {
        if (thread === exited) thread = null
    }

    private fun playbackAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private inner class RenderThread : Thread("FluteAudio") {

        @Volatile
        var running = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var track: AudioTrack? = null
            try {
                val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
                    .takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE
                val minBufferBytes = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                ).takeIf { it > 0 } ?: (sampleRate / 10 * BYTES_PER_FRAME)
                val minBufferFrames = minBufferBytes / BYTES_PER_FRAME
                // Small blocks keep a press immediate; a 2x buffer guards against underruns.
                val blockFrames = (minBufferFrames / 2).coerceIn(128, 1024)
                val bufferBytes = max(minBufferBytes * 2, blockFrames * 4 * BYTES_PER_FRAME)
                val bufferFrames = bufferBytes / BYTES_PER_FRAME

                val built = buildTrack(sampleRate, bufferBytes) // may throw on unsupported formats
                track = built
                if (built.state != AudioTrack.STATE_INITIALIZED) {
                    logger.w(LogTags.FLUTE, "tone.error", "reason" to "track_uninitialised", "rate" to sampleRate)
                    return
                }
                logger.d(
                    LogTags.FLUTE, "tone.track_open",
                    "rate" to sampleRate, "blockFrames" to blockFrames, "bufferFrames" to bufferFrames,
                )

                val loop = ToneRenderLoop(SustainedToneSynth(sampleRate), commands, sampleRate, blockFrames, bufferFrames)

                built.play()
                while (loop.next(running)) {
                    val written = writeFully(loop.block, running = { running }) { block, offset, count ->
                        built.write(block, offset, count, AudioTrack.WRITE_BLOCKING)
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
                track?.let { t ->
                    logger.d(LogTags.FLUTE, "tone.track_closed", "underruns" to t.underrunCount)
                    runCatching { t.pause(); t.flush(); t.stop() }
                    t.release()
                }
                onThreadExited(this)
            }
        }

        private fun buildTrack(sampleRate: Int, bufferBytes: Int): AudioTrack {
            val builder = AudioTrack.Builder()
                .setAudioAttributes(playbackAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            return builder.build()
        }
    }

    private companion object {
        /** Comfortable level at full volume, the reference tone's peak. */
        const val PEAK = 0.6f
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val BYTES_PER_FRAME = 4 // mono float
    }
}
