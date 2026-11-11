// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlin.math.max

/**
 * Low-latency metronome built on a streaming [AudioTrack].
 *
 * Design:
 * - A dedicated high-priority thread renders small PCM blocks with [MetronomeRenderer] and
 *   writes them with blocking writes, so timing is governed by the audio clock, not by
 *   handlers or timers. Click positions are sample-accurate.
 * - The track runs at the device's native output rate with float PCM and
 *   PERFORMANCE_MODE_LOW_LATENCY (API 26+) to get the fast mixer path where available.
 * - USAGE_MEDIA attributes let the OS route output to wired or Bluetooth headphones and
 *   respect the media volume stream. Audio focus is requested so other media pauses, and
 *   playback stops when focus is lost or when headphones are unplugged
 *   (ACTION_AUDIO_BECOMING_NOISY), so a click never suddenly blasts from the speaker.
 * - Each queued click is published as a [BeatEvent] whose audible time is derived from
 *   [AudioTrack.getTimestamp], so the visual flash lines up with what the ear hears.
 *
 * Threading: settings reach the audio thread through an [AtomicReference]; [stop] never
 * blocks the caller (the thread winds down on its own and a successor joins it before
 * opening a new track, so only one AudioTrack exists at a time). [sessionEnded] reports every
 * end of playback, including stops the engine initiates itself.
 *
 * Logs under [LogTags.METRONOME]: `metronome.start` / `metronome.stop` per session, why a
 * session ended on its own (`metronome.focus_lost`, `metronome.noisy`, `metronome.ended`),
 * the track's geometry and underrun count, and every failure as `metronome.error`.
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

    // The fields below are guarded by synchronized(this).
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
        this.config.set(config)
        thread?.let {
            if (it.running) {
                _playback.update { state -> state?.copy(config = config) }
                return it.sessionId
            }
        }

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
        thread = RenderThread(session, predecessor = thread).also { it.start() }
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
        thread = null
        releaseSystemResources()
        _playback.value = null
        _sessionEnded.tryEmit(current.sessionId)
    }

    /** Called by a render thread when it finishes for any reason. */
    @Synchronized
    private fun onThreadExited(exited: RenderThread) {
        if (thread === exited) {
            logger.i(LogTags.METRONOME, "metronome.ended", "session" to exited.sessionId)
            thread = null
            releaseSystemResources()
            _playback.value = null
            _sessionEnded.tryEmit(exited.sessionId)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes())
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

    private fun playbackAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private inner class RenderThread(
        val sessionId: Long,
        private val predecessor: RenderThread?,
    ) : Thread("MetronomeAudio-$sessionId") {

        @Volatile
        var running = true

        override fun run() {
            // Only one AudioTrack at a time: wait for a previous session to release its track.
            predecessor?.join(PREDECESSOR_JOIN_MS)
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var track: AudioTrack? = null
            try {
                val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
                    .takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE
                val minBufferBytes = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                ).takeIf { it > 0 } ?: (sampleRate / 10 * BYTES_PER_FRAME)
                val minBufferFrames = minBufferBytes / BYTES_PER_FRAME
                // Small blocks keep tempo changes responsive; a 2x buffer guards against underruns.
                val blockFrames = (minBufferFrames / 2).coerceIn(128, 1024)
                val bufferBytes = max(minBufferBytes * 2, blockFrames * 4 * BYTES_PER_FRAME)
                val bufferFrames = bufferBytes / BYTES_PER_FRAME

                track = buildTrack(sampleRate, bufferBytes) // may throw on unsupported formats
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    logger.w(LogTags.METRONOME, "metronome.error", "reason" to "track_uninitialised", "rate" to sampleRate)
                    return
                }
                logger.d(
                    LogTags.METRONOME, "metronome.track_open",
                    "session" to sessionId, "rate" to sampleRate, "blockFrames" to blockFrames, "bufferFrames" to bufferFrames,
                )

                val renderer = MetronomeRenderer(sampleRate) // synthesises its clicks now, not on the first beat
                val block = FloatArray(blockFrames)
                val timestamp = AudioTimestamp()

                track.play()
                while (running) {
                    val events = renderer.render(block, config.get())
                    val written = track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        logger.w(LogTags.METRONOME, "metronome.error", "reason" to "write_failed", "code" to written)
                        break
                    }
                    if (written == 0) {
                        sleep(1) // nothing accepted (route being rebuilt): avoid a hot spin
                        continue
                    }
                    if (events.isNotEmpty()) publish(events, track, timestamp, sampleRate, bufferFrames)
                }
            } catch (e: RuntimeException) {
                // UnsupportedOperationException / IllegalArgumentException from the builder,
                // IllegalStateException from play(): report and stop instead of killing the process.
                logger.w(LogTags.METRONOME, "metronome.error", "reason" to "exception", "message" to e.message, error = e)
            } finally {
                track?.let { t ->
                    logger.d(LogTags.METRONOME, "metronome.track_closed", "session" to sessionId, "underruns" to t.underrunCount)
                    runCatching { t.pause(); t.flush(); t.stop() }
                    t.release()
                }
                onThreadExited(this)
            }
        }

        private fun publish(
            events: List<ClickEvent>,
            track: AudioTrack,
            ts: AudioTimestamp,
            sampleRate: Int,
            bufferFrames: Int,
        ) {
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
                    now + (event.frame - head + bufferFrames / 2) * NANOS_PER_SECOND / sampleRate
                }
                _beatEvents.tryEmit(BeatEvent(event, audibleAt, sessionId))
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
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val BYTES_PER_FRAME = 4 // mono float
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val PREDECESSOR_JOIN_MS = 1_000L
    }
}
