// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig

/**
 * The key under a finger: a note key's [midi], or null for the rest key, and where its note or
 * rest sits in the tune ([index]; null at the cap, where a press sounds but is not added).
 */
data class Held(val midi: Int?, val index: Int?) {
    /** Whether the key is the rest key. */
    val isRest: Boolean get() = midi == null
}

/**
 * Writes the child's [tune] as the keys go down and up. A key pressed adds an eighth (a note, or
 * a rest for the rest key), which [grow]s while the key stays down and takes the length the whole
 * hold made when it is let go ([release], [Tune.lengthOf]). A press while another key is held
 * ends that one first: the newest press wins. At [Tune.MAX_NOTES] a key is held but nothing is
 * added.
 *
 * A key let go of and pressed again goes on with the same note or rest instead of adding one,
 * as long as nothing else happened in between ([forget], a breath, an erase, another book): a
 * note key within [HOLD_REJOIN_MS], counted from the press that began it, gap included; the rest
 * key however long the pause, and past the window from the length its rest has, the pause not
 * counted (more of the rest key is only more of the same silence).
 *
 * Pure Kotlin and clock-free: the view model passes the time of every press and release and the
 * metronome's [Pulse], and does the sounding, the growing over time, the saving and the logging.
 */
class TuneWriter(tune: List<TuneItem> = emptyList()) {

    var tune: List<TuneItem> = tune
        private set

    /** The key held now; null when none. */
    var held: Held? = null
        private set

    /** The pulse the held key's note or rest is counted in, read at the press that began it. */
    var pulse: Pulse = Pulse(MetronomeConfig.DEFAULT_BPM)
        private set

    /** A hold that ended: the key ([midi], null for the rest), how long it was held, the length that made, and whether it is in the tune. */
    data class Ended(val midi: Int?, val heldMs: Long, val length: NoteLength, val added: Boolean)

    /**
     * What a press did: [ended] is the key that was held until it; [rejoinGapMs] says the press
     * went on with the key let go of that long ago (null: a new note or rest); [elapsedMs] is how
     * far into its hold the key now is, which is where its growing goes on from.
     */
    data class Press(val ended: Ended?, val rejoinGapMs: Long?, val elapsedMs: Long)

    /** The key the finger let go of last, while a press of it may still go on with its hold. */
    private data class LetGo(val key: Held, val pressedAt: Long, val pulse: Pulse, val at: Long, val length: NoteLength)

    private var pressedAt = 0L
    private var letGo: LetGo? = null

    /** The key [midi] (null: the rest key) went down at [now], the metronome being at [pulse]. */
    fun press(midi: Int?, now: Long, pulse: Pulse): Press {
        val again = rejoinable(midi, now) // asked before a held key is ended: a press over a held key is a new note
        val ended = release(now)
        letGo = null
        if (again == null) {
            pressedAt = now
            this.pulse = pulse
            val atCap = tune.size >= Tune.MAX_NOTES
            held = Held(midi, index = tune.size.takeUnless { atCap })
            if (!atCap) tune = tune + if (midi == null) TuneRest(NoteLength.EIGHTH) else TuneNote(midi, NoteLength.EIGHTH)
            return Press(ended, rejoinGapMs = null, elapsedMs = 0)
        }
        // A touch screen can report one hold as several (its palm rejection cancelling a flat finger
        // and starting it over, or a bounce): within the window the gap counts as held. Longer ago
        // (only the rest key gets here) the pause was not silence the child asked for.
        val gap = now - again.at
        val elapsed = if (gap <= HOLD_REJOIN_MS) now - again.pressedAt else Tune.durationMs(again.length, again.pulse)
        pressedAt = now - elapsed
        this.pulse = again.pulse
        held = again.key
        grow(Tune.lengthOf(elapsed, this.pulse))
        return Press(ended, rejoinGapMs = gap, elapsedMs = elapsed)
    }

    /** The held key's note or rest reached [length]; nothing changes at the cap, where it was not added. */
    fun grow(length: NoteLength) {
        val index = held?.index ?: return
        val resized = when (val item = tune[index]) {
            is TuneNote -> item.copy(length = length)
            is TuneRest -> item.copy(length = length)
            TuneBreath -> return
        }
        tune = tune.toMutableList().also { it[index] = resized }
    }

    /**
     * The held key was let go at [now]: its note or rest takes the length [heldMs] makes (by
     * default the time since its press), and a press of the same key may go on with it. Null when
     * no key is held.
     */
    fun release(now: Long, heldMs: Long = (now - pressedAt).coerceAtLeast(0)): Ended? {
        val key = held ?: return null
        val length = Tune.lengthOf(heldMs, pulse)
        grow(length)
        held = null
        letGo = LetGo(key, pressedAt, pulse, at = now, length = length)
        return Ended(key.midi, heldMs, length, added = key.index != null)
    }

    /** Something other than the finger acted on the tune: no press goes on with the key let go of before. */
    fun forget() {
        letGo = null
    }

    /** Marks a breath, unless the tune is at the cap or its last step is one already; says whether it was added. */
    fun breath(): Boolean {
        letGo = null
        if (tune.size >= Tune.MAX_NOTES || tune.lastOrNull() == TuneBreath) return false
        tune = tune + TuneBreath
        return true
    }

    /** Drops the last step; refused (false) while a key is held or the tune is empty. */
    fun erase(): Boolean {
        if (held != null || tune.isEmpty()) return false
        tune = tune.dropLast(1)
        letGo = null // the step let go of last may be the one erased
        return true
    }

    /** Another book's [tune] takes this one's place. */
    fun open(tune: List<TuneItem>) {
        this.tune = tune
        letGo = null
    }

    /** The hold a press of [midi] at [now] goes on with: see the class comment; null for a new note or rest. */
    private fun rejoinable(midi: Int?, now: Long): LetGo? {
        val last = letGo ?: return null
        if (last.key.midi != midi || held != null) return null
        val inTime = midi == null || now - last.at <= HOLD_REJOIN_MS
        return if (inTime) last else null
    }

    companion object {
        /**
         * A note key let go of and pressed again within this long is one hold, not two notes. A
         * touch screen can report one hold as several: its palm rejection cancels a flat finger and
         * starts it over, or the contact bounces, a few frames apart. A child's quickest deliberate
         * repeat of a note takes well over 100 ms, and Android itself takes an up and a down under
         * 40 ms (`ViewConfiguration.getDoubleTapMinTime`) for jitter rather than a second tap. The
         * rest key is not bound by it: within it a hold's gap counts as held, past it the rest goes
         * on from the length it has.
         */
        const val HOLD_REJOIN_MS = 60L
    }
}
