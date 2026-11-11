// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReferenceTonePlayer] on a static-mode [AudioTrack]: the whole tone is rendered up front by
 * [ReferenceToneSynth], handed to the track and played once. One track lives at a time; the
 * next [play] or a [stop] releases it. Media attributes route it like the metronome (speaker,
 * wired or Bluetooth headphones).
 */
@Singleton
class AudioTrackReferenceTonePlayer @Inject constructor(private val logger: Logger) : ReferenceTonePlayer {

    private var track: AudioTrack? = null

    @Synchronized
    override fun play(frequencyHz: Double, durationMs: Int, volume: Float) {
        stop()
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val samples = ReferenceToneSynth.render(frequencyHz, sampleRate, durationMs, amplitude = PEAK * volume.coerceIn(0f, 1f))
        val newTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                .build()
        }.getOrElse { e ->
            logger.w(LogTags.TUNER, "reference.error", "reason" to "build_failed", "message" to e.message, error = e)
            return
        }
        if (newTrack.state == AudioTrack.STATE_UNINITIALIZED) {
            logger.w(LogTags.TUNER, "reference.error", "reason" to "track_uninitialised", "rate" to sampleRate)
            newTrack.release()
            return
        }
        val written = newTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written < samples.size) {
            logger.w(LogTags.TUNER, "reference.error", "reason" to "write_failed", "code" to written)
            newTrack.release()
            return
        }
        newTrack.play()
        track = newTrack
    }

    @Synchronized
    override fun stop() {
        track?.let { t ->
            runCatching { t.stop() }
            t.release()
        }
        track = null
    }

    private companion object {
        /** Comfortable level at full volume; the metronome clicks sit around here too. */
        const val PEAK = 0.6f
    }
}
