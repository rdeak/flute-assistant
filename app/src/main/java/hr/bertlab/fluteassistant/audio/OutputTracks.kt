// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.max

/**
 * What every sound the app plays is to the system: media, so the OS routes it to the speaker or to
 * wired or Bluetooth headphones and the phone's media volume sets its loudness.
 */
internal fun mediaPlaybackAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

/** Mono float PCM, the one format the app plays. */
internal fun monoFloatFormat(sampleRate: Int): AudioFormat = AudioFormat.Builder()
    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
    .setSampleRate(sampleRate)
    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
    .build()

/** The device's own output rate, so the mixer does not resample; 48 kHz when the system does not say. */
internal fun nativeOutputSampleRate(): Int =
    AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE

/**
 * A streaming [AudioTrack] for a render thread that writes small blocks with blocking writes: the
 * native rate, float PCM and PERFORMANCE_MODE_LOW_LATENCY (API 26+) for the fast mixer path.
 * [blockFrames] is what the thread renders at a time, small so a tempo change or a key press is
 * heard at once; the track's buffer ([bufferFrames]) is twice the minimum, against underruns.
 */
internal class LowLatencyStream private constructor(
    val track: AudioTrack,
    val sampleRate: Int,
    val blockFrames: Int,
    val bufferFrames: Int,
) {
    /** False when the system gave a track that cannot play: [close] it and give up. */
    val isInitialised: Boolean get() = track.state == AudioTrack.STATE_INITIALIZED

    /** Drops what is still queued and gives the track back. */
    fun close() {
        runCatching {
            track.pause()
            track.flush()
            track.stop()
        }
        track.release()
    }

    companion object {
        /** Opens the track; throws a [RuntimeException] when the device refuses the format. */
        fun open(): LowLatencyStream {
            val sampleRate = nativeOutputSampleRate()
            val minBufferBytes = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            ).takeIf { it > 0 } ?: (sampleRate / 10 * BYTES_PER_FRAME)
            val blockFrames = (minBufferBytes / BYTES_PER_FRAME / 2).coerceIn(128, 1024)
            val bufferBytes = max(minBufferBytes * 2, blockFrames * 4 * BYTES_PER_FRAME)

            val builder = AudioTrack.Builder()
                .setAudioAttributes(mediaPlaybackAttributes())
                .setAudioFormat(monoFloatFormat(sampleRate))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            return LowLatencyStream(builder.build(), sampleRate, blockFrames, bufferBytes / BYTES_PER_FRAME)
        }
    }
}

private const val DEFAULT_SAMPLE_RATE = 48_000
private const val BYTES_PER_FRAME = Float.SIZE_BYTES // mono float
