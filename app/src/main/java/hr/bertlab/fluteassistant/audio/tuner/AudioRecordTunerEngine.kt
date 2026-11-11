// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Captures microphone audio with [AudioRecord] and runs the flute-tuned [YinPitchDetector]
 * ([YinPitchDetector.forFlute]) on a sliding window.
 *
 * - Prefers the UNPROCESSED source (no AGC / noise suppression) when the device supports it,
 *   because automatic gain and suppression distort the signal a tuner wants to analyse.
 * - Analysis window: 4096 samples, advanced by 2048 per frame (~43 ms at 48 kHz).
 * - Runs on [Dispatchers.IO] (the reads block), which buffers the frames (64 deep) rather than
 *   conflating them, so a busy main thread delays a frame but never loses one: the flute's "Your
 *   turn" counts frames (a gap is two in a row). The microphone is released when the collector
 *   cancels.
 * - Logs `mic.open` (source, rate, buffer) and `mic.close` under [LogTags.TUNER]; failures
 *   propagate to the collector, which logs them.
 */
@Singleton
class AudioRecordTunerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val tap: AudioTap,
) : TunerEngine {

    override fun pitchFrames(): Flow<PitchFrame> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission not granted")
        }

        val sampleRate = pickSampleRate()
        val record = openRecorder(sampleRate)
        val detector = YinPitchDetector.forFlute(sampleRate, bufferSize = WINDOW_SIZE)
        val window = FloatArray(WINDOW_SIZE)
        val chunk = FloatArray(HOP_SIZE)
        var frames = 0
        logger.i(
            LogTags.TUNER, "mic.open",
            "source" to sourceName(record.audioSource), "unprocessedSupported" to unprocessedSupported(),
            "rate" to record.sampleRate, "bufferFrames" to record.bufferSizeInFrames,
            "window" to WINDOW_SIZE, "hop" to HOP_SIZE,
        )
        tap.opened(record.sampleRate, sourceName(record.audioSource))

        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone is busy" }
            while (currentCoroutineContext().isActive) {
                val read = record.read(chunk, 0, HOP_SIZE, AudioRecord.READ_BLOCKING)
                // The collector may have cancelled during the read: that hop is nobody's, least of
                // all the tap's, where it would open the next run's recording (if one started
                // within 43 ms) with a hop of this one.
                if (!currentCoroutineContext().isActive) break
                if (read <= 0) throw IllegalStateException("Microphone read failed (code $read)")
                tap.samples(chunk, read)

                // Slide the analysis window left and append the new samples.
                System.arraycopy(window, read, window, 0, WINDOW_SIZE - read)
                System.arraycopy(chunk, 0, window, WINDOW_SIZE - read, read)

                val estimate = detector.detect(window)
                frames++
                emit(
                    PitchFrame(
                        frequency = estimate?.frequency,
                        probability = estimate?.probability ?: 0.0,
                        level = FrameLevels.level(window),
                        minLevel = FrameLevels.minLevel(window, HOP_SIZE),
                    ),
                )
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            tap.closed()
            logger.i(LogTags.TUNER, "mic.close", "frames" to frames)
        }
    }.flowOn(Dispatchers.IO) // blocking reads belong on IO; each collector decides whether to conflate or buffer

    private fun pickSampleRate(): Int =
        PREFERRED_SAMPLE_RATES.firstOrNull { rate ->
            AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) > 0
        } ?: 44_100

    // Permission is verified at the top of pitchFrames() before this is reached.
    @SuppressLint("MissingPermission")
    private fun openRecorder(sampleRate: Int): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val record = AudioRecord.Builder()
            .setAudioSource(preferredSource())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBytes, WINDOW_SIZE * 4 * 2))
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Microphone could not be opened")
        }
        return record
    }

    private fun preferredSource(): Int =
        if (unprocessedSupported()) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC

    private fun unprocessedSupported(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> source.toString()
    }

    companion object {
        /** Samples the detector sees at once; public so the replay of a recorded session frames audio the same way. */
        const val WINDOW_SIZE = 4096
        /** Samples read, and the window advanced, per frame: about 43 ms at 48 kHz. */
        const val HOP_SIZE = 2048
        private val PREFERRED_SAMPLE_RATES = intArrayOf(48_000, 44_100)
    }
}
