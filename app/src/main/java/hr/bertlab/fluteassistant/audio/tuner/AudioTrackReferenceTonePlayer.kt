// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import android.media.AudioTrack
import hr.bertlab.fluteassistant.audio.mediaPlaybackAttributes
import hr.bertlab.fluteassistant.audio.monoFloatFormat
import hr.bertlab.fluteassistant.audio.nativeOutputSampleRate
import hr.bertlab.fluteassistant.audio.toneAmplitude
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
        val sampleRate = nativeOutputSampleRate()
        val samples = ReferenceToneSynth.render(frequencyHz, sampleRate, durationMs, amplitude = toneAmplitude(volume))
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(mediaPlaybackAttributes())
                .setAudioFormat(monoFloatFormat(sampleRate))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
                .build()
        } catch (e: RuntimeException) {
            // UnsupportedOperationException / IllegalArgumentException from the builder.
            logger.w(LogTags.TUNER, "reference.error", "reason" to "build_failed", "message" to e.message, error = e)
            return
        }
        // A static track reads STATE_NO_STATIC_DATA until it is written, so only "uninitialised" is a failure here.
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
}
