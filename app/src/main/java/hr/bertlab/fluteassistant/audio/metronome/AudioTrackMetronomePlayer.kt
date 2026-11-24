// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import hr.bertlab.fluteassistant.audio.LowLatencyStream
import hr.bertlab.fluteassistant.audio.mediaPlaybackAttributes
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-latency metronome built on a streaming [AudioTrack].
 *
 * Design:
 * - A dedicated high-priority thread renders small PCM blocks with [MetronomeRenderer] and
 *   writes them with blocking writes, every block in full ([renderWhile]), so timing is
 *   governed by the audio clock, not by handlers or timers. Click positions are sample-accurate.
 * - The track is a [LowLatencyStream]: the device's native output rate, float PCM and the fast
 *   mixer path where available, with media attributes. Audio focus is requested so other media pauses, and
 *   playback stops when focus is lost or when headphones are unplugged
 *   (ACTION_AUDIO_BECOMING_NOISY), so a click never suddenly blasts from the speaker.
 * - Each queued click is published as a [BeatEvent] whose audible time is derived from
 *   [AudioTrack.getTimestamp], so the visual flash lines up with what the ear hears.
 *
 * Threading: settings reach the audio thread through an [AtomicReference]; [stop] never
 * blocks the caller: the thread winds down on its own after its current block, so a start
 * straight after a stop overlaps the old track for that block. [sessionEnded] reports every
 * end of playback, including stops the engine initiates itself.
 *
 * Logs under [LogTags.METRONOME]: `metronome.start` / `metronome.stop` per session, why a
 * session ended on its own (`metronome.focus_lost`, `metronome.noisy`, `metronome.ended`),
 * the track's geometry, its underrun count and how many writes it took less than all of, and
 * every failure as `metronome.error`.
 */
@Singleton
class AudioTrackMetronomePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
) : MetronomePlayer {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _beatEvents = MutableSharedFlow<BeatEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val beatEvents: Flow<BeatEvent> = _beatEvents

    private val _sessionEnded = MutableSharedFlow<Long>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val sessionEnded: Flow<Long> = _sessionEnded

    private val _playback = MutableStateFlow<PlaybackState?>(null)
    override val playback: StateFlow<PlaybackState?> = _playback

    private val config = AtomicReference(MetronomeConfig())

    // The fields below are guarded by synchronized(this). A thread is held here only while it is
    // meant to run: stop() and the thread's own exit both clear it.
    private var thread: RenderThread? = null
    private var sessionCounter = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                logger.i(LogTags.METRONOME, "metronome.focus_lost", "change" to change)
                stop()
            }
            else -> Unit // ducking requests are ignored: a short click is not worth ducking
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                logger.i(LogTags.METRONOME, "metronome.noisy")
                stop()
            }
        }
    }

    @Synchronized
    override fun start(config: MetronomeConfig): Long {
        update(config)
        thread?.let { return it.sessionId }

        if (!requestAudioFocus()) {
            logger.w(LogTags.METRONOME, "metronome.error", "reason" to "focus_refused")
            return MetronomePlayer.NO_SESSION
        }
        registerNoisyReceiver()

        val session = ++sessionCounter
        logger.i(
            LogTags.METRONOME, "metronome.start",
            "session" to session, "bpm" to config.bpm, "signature" to config.timeSignature,
        )
        thread = RenderThread(session).also { it.start() }
        _playback.value = PlaybackState(session, config)
        return session
    }

    override fun update(config: MetronomeConfig) {
        this.config.set(config)
        _playback.update { it?.copy(config = config) }
    }

    @Synchronized
    override fun stop() {
        val current = thread ?: return
        logger.i(LogTags.METRONOME, "metronome.stop", "session" to current.sessionId)
        current.running = false // the thread exits after its current block; no join on the caller
        endSession(current.sessionId)
    }

    /** Called by a render thread when it finishes for any reason. */
    @Synchronized
    private fun onThreadExited(exited: RenderThread) {
        if (thread === exited) {
            logger.i(LogTags.METRONOME, "metronome.ended", "session" to exited.sessionId)
            endSession(exited.sessionId)
        }
    }

    private fun endSession(sessionId: Long) {
        thread = null
        releaseSystemResources()
        _playback.value = null
        _sessionEnded.tryEmit(sessionId)
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mediaPlaybackAttributes())
                .setOnAudioFocusChangeListener(focusListener, mainHandler)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseSystemResources() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        if (noisyReceiverRegistered) {
            runCatching { context.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED, // system broadcasts are still delivered
        )
        noisyReceiverRegistered = true
    }

    private inner class RenderThread(val sessionId: Long) : Thread("MetronomeAudio-$sessionId") {

        @Volatile
        var running = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var stream: LowLatencyStream? = null
            var shortWrites = 0 // writes the track took less than all of; each is retried
            try {
                stream = LowLatencyStream.open() // may throw on unsupported formats
                val track = stream.track
                if (!stream.isInitialised) {
                    logger.w(LogTags.METRONOME, "metronome.error", "reason" to "track_uninitialised", "rate" to stream.sampleRate)
                    return
                }
                logger.d(
                    LogTags.METRONOME, "metronome.track_open",
                    "session" to sessionId, "rate" to stream.sampleRate,
                    "blockFrames" to stream.blockFrames, "bufferFrames" to stream.bufferFrames,
                )

                val renderer = MetronomeRenderer(stream.sampleRate) // synthesises its clicks now, not on the first beat
                val timestamp = AudioTimestamp()

                track.play()
                val ended = renderer.renderWhile(
                    running = { running },
                    block = FloatArray(stream.blockFrames),
                    config = { config.get() },
                    write = { block, offset, count ->
                        track.write(block, offset, count, AudioTrack.WRITE_BLOCKING).also { if (it in 0 until count) shortWrites++ }
                    },
                    onClicks = { publish(it, stream, timestamp) },
                )
                if (ended < 0) logger.w(LogTags.METRONOME, "metronome.error", "reason" to "write_failed", "code" to ended)
            } catch (e: RuntimeException) {
                // UnsupportedOperationException / IllegalArgumentException from the builder,
                // IllegalStateException from play(): report and stop instead of killing the process.
                logger.w(LogTags.METRONOME, "metronome.error", "reason" to "exception", "message" to e.message, error = e)
            } finally {
                stream?.let {
                    logger.d(
                        LogTags.METRONOME, "metronome.track_closed",
                        "session" to sessionId, "underruns" to it.track.underrunCount, "shortWrites" to shortWrites,
                    )
                    it.close()
                }
                onThreadExited(this)
            }
        }

        private fun publish(events: List<ClickEvent>, stream: LowLatencyStream, ts: AudioTimestamp) {
            val track = stream.track
            val sampleRate = stream.sampleRate
            val now = System.nanoTime()
            val hasTimestamp = track.getTimestamp(ts)
            for (event in events) {
                val audibleAt = if (hasTimestamp) {
                    // Map the event's frame onto the wall clock using the last known DAC position.
                    ts.nanoTime + (event.frame - ts.framePosition) * NANOS_PER_SECOND / sampleRate
                } else {
                    // Before the first timestamp is available: head position plus a rough
                    // output-latency allowance (half the track buffer).
                    val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    now + (event.frame - head + stream.bufferFrames / 2) * NANOS_PER_SECOND / sampleRate
                }
                _beatEvents.tryEmit(BeatEvent(event, audibleAt, sessionId))
            }
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
