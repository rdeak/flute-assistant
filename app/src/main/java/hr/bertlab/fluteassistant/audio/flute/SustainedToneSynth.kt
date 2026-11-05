// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import kotlin.math.PI

/**
 * A flute voice ([FluteTimbre]) that does not know its length in advance: [noteOn] fades the
 * note in over [ATTACK_MS] and holds it, [noteOff] fades it out over [RELEASE_MS], and a
 * [noteOn] while a note sounds fades the old note down over [RETRIGGER_MS] before the new
 * attack, so nothing clicks. The oscillator is phase-continuous across every change.
 * [render] fills PCM blocks on demand and writes zeros while [isSilent]. Pure Kotlin, one
 * thread at a time (the player's render thread drives it through a command queue).
 */
class SustainedToneSynth(private val sampleRate: Int) {

    private enum class Stage { IDLE, ATTACK, SUSTAIN, RELEASE, RETRIGGER }

    private var stage = Stage.IDLE

    /** The envelope, 0..1, moved linearly per sample toward the stage's target. */
    private var level = 0.0
    private var phase = 0.0
    private var phaseStep = 0.0
    private var gain = 0.0
    private var pendingFrequencyHz = 0.0
    private var pendingAmplitude = 0f

    private val attackStep = 1.0 / samplesFor(ATTACK_MS)
    private val releaseStep = 1.0 / samplesFor(RELEASE_MS)
    private val retriggerStep = 1.0 / samplesFor(RETRIGGER_MS)

    init {
        require(sampleRate > 0) { "sample rate must be positive" }
    }

    val isSilent: Boolean get() = stage == Stage.IDLE

    /** Starts [frequencyHz] at [amplitude] (0..1 peak); while a note sounds, it takes over after a short fade. */
    fun noteOn(frequencyHz: Double, amplitude: Float) {
        require(frequencyHz > 0.0) { "frequency must be positive" }
        if (stage == Stage.IDLE || (stage == Stage.RELEASE && level < RESTART_LEVEL)) {
            start(frequencyHz, amplitude)
        } else {
            pendingFrequencyHz = frequencyHz
            pendingAmplitude = amplitude
            stage = Stage.RETRIGGER
        }
    }

    /** Fades the sounding note out; a note waiting behind a retrigger fade is dropped. */
    fun noteOff() {
        if (stage != Stage.IDLE) stage = Stage.RELEASE
    }

    fun render(out: FloatArray) {
        for (i in out.indices) {
            if (stage == Stage.IDLE) {
                out.fill(0f, i)
                return
            }
            advanceEnvelope()
            out[i] = (level * gain * FluteTimbre.waveform(phase)).toFloat()
            phase += phaseStep
            if (phase >= TWO_PI) phase -= TWO_PI
        }
    }

    private fun start(frequencyHz: Double, amplitude: Float) {
        phaseStep = TWO_PI * frequencyHz / sampleRate
        gain = amplitude / FluteTimbre.cyclePeak
        stage = Stage.ATTACK
    }

    private fun advanceEnvelope() {
        when (stage) {
            Stage.ATTACK -> {
                level += attackStep
                if (level >= 1.0) {
                    level = 1.0
                    stage = Stage.SUSTAIN
                }
            }
            Stage.RELEASE -> {
                level -= releaseStep
                if (level <= 0.0) {
                    level = 0.0
                    stage = Stage.IDLE
                }
            }
            Stage.RETRIGGER -> {
                level -= retriggerStep
                if (level <= 0.0) {
                    level = 0.0
                    start(pendingFrequencyHz, pendingAmplitude)
                }
            }
            Stage.SUSTAIN, Stage.IDLE -> Unit
        }
    }

    private fun samplesFor(ms: Int): Int = (sampleRate.toLong() * ms / 1000).toInt().coerceAtLeast(1)

    companion object {
        const val ATTACK_MS = 30
        const val RELEASE_MS = 120
        const val RETRIGGER_MS = 10

        /** Below this a releasing note is quiet enough to restart the attack in place of a retrigger fade. */
        private const val RESTART_LEVEL = 0.01
        private const val TWO_PI = 2 * PI
    }
}
