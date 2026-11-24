// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * The key under a finger: the note it wrote and sounds ([midi]), or null for the rest key, where its note or rest sits
 * in the tune ([index]; null at the cap, where a press sounds but is not added), and the letter key itself ([key], 0 = C
 * to 6 = B, since 2026-11-18; by default the note's own letter, E's for an E♭). The key stays the same when the octave
 * pills rename it under the finger. [replaces]: its step took the place of the one chosen on the staff (since 2026-11-24).
 */
data class Held(val midi: Int?, val index: Int?, val key: Int? = midi?.let(NoteSpelling::letterOf), val replaces: Boolean = false) {
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
 * The ⌒ key (since 2026-11-16, [join]) joins the next note pressed to the last one, a slur, or a
 * tie when it is the same note ([TuneNote.joined], [Joins]): one tap per pair, nothing left switched
 * on. A key held counts, its note being the last step. While a join waits, a second tap, the rest
 * key, a breath, another book and [dropWaiting] drop it, and [erase] drops it first and erases
 * nothing. A join ends the window in which a key let go of goes on with its note, as a breath does,
 * so the same key again after it is a tie of two notes.
 *
 * A hold is counted at [WRITING_PULSE], 130 a minute, whatever the speed knob says (since 2026-11-17, the user:
 * "set max tempo 200 just when child type notes and want to set duration, is just for note duration"; 130 since
 * 2026-11-23): a whole note takes 1.6 s of holding at any speed. The knob sets how fast Play plays and how long Listen
 * to me asks a note to last.
 *
 * The ♯♭ key (since 2026-11-17, [sign]) makes the next note key pressed write its letter's black note
 * ([FluteNotes.inOctave], [writes]): one tap per note, as the join. While it waits D and A write nothing (a press of
 * one changes nothing, not even a held key); a note key spends it, the rest key and a breath leave it, a second tap,
 * [erase], another book and [dropWaiting] drop it. A key held is known by its letter ([Held.key]), so a key let go of
 * and pressed again goes on with the note it wrote, signed or not.
 *
 * The keys are seven letters, and the four octaves are pills over them (since 2026-11-18, [chooseOctave]): a letter
 * writes its note in the chosen octave ([octave], [FluteNotes.inOctave]), which stays until another pill is tapped. A
 * key is its letter, so a key held when the octave changes keeps its note; a pill ends the rejoin window and drops no
 * waiting join or sign. In octave 4 only c⁴ writes.
 *
 * A step can be chosen on the staff (since 2026-11-24, [select]; spec `2026-11-24-flute-edit-a-note-design.md`): while
 * one is [selected], a key writes in its place instead of at the end, at the cap too, its length from the hold as
 * always, and the choice ends when that key is let go (the user, the same day: "if user tap on notes it should also
 * deselect currently selected note"; a bounce of the key goes on with it, chosen again); the rest key writes a rest
 * there, the breath key a breath (which ends the choice at once), and a
 * waiting sign, octave and join apply to it (the join to the step before it, [canJoin]). [erase] removes the chosen step
 * and ends the choice. A note joined to a step that is removed, or that becomes a rest or a breath, is no longer joined.
 * The same step tapped again lets it go; a tap while a key is held changes nothing.
 *
 * Pure Kotlin and clock-free: the view model passes the time of every press and release, and does the sounding, the
 * growing over time, the saving and the logging.
 */
class TuneWriter(tune: List<TuneItem> = emptyList()) {

    var tune: List<TuneItem> = tune
        private set

    /** The key held now; null when none. */
    var held: Held? = null
        private set

    /** The next note pressed will be joined to the last one ([join]). */
    var joinWaiting: Boolean = false
        private set

    /** The next note key pressed will write its letter's black note ([sign]). */
    var signWaiting: Boolean = false
        private set

    /** The step chosen on the staff for the keys to change ([select], since 2026-11-24); null when none. Never saved. */
    var selected: Int? = null
        private set

    /** The octave the letter keys write in, 1 to 4 ([chooseOctave], since 2026-11-18). Kept until another is chosen, through another book too. */
    var octave: Int = 1
        private set

    /**
     * A hold that ended: the key ([midi], null for the rest), how long it was held, the length that made, whether it is
     * in the tune, whether its note is joined to the one before ([joined], since 2026-11-16), and whether it took the
     * place of the step chosen on the staff ([replaced], since 2026-11-24).
     */
    data class Ended(
        val midi: Int?,
        val heldMs: Long,
        val length: NoteLength,
        val added: Boolean,
        val joined: Boolean = false,
        val replaced: Boolean = false,
    )

    /**
     * What a press did: [ended] is the key that was held until it; [rejoinGapMs] says the press
     * went on with the key let go of that long ago (null: a new note or rest); [elapsedMs] is how
     * far into its hold the key now is, which is where its growing goes on from; [dropped] is the
     * waiting join the rest key dropped (a note key spends a join and a sign, it drops neither).
     */
    data class Press(val ended: Ended?, val rejoinGapMs: Long?, val elapsedMs: Long, val dropped: Dropped = Dropped())

    /** What waited for the next note key and was dropped unused: a [join], a [sign]. */
    data class Dropped(val join: Boolean = false, val sign: Boolean = false) {
        val any: Boolean get() = join || sign
    }

    /**
     * What a tap of the breath key did: whether a breath was [added], the waiting join it [dropped] (a sign stays), and
     * whether the breath took the chosen step's place ([replaced]).
     */
    data class Breath(val added: Boolean, val dropped: Dropped, val replaced: Boolean = false)

    /**
     * What a tap of the erase key did: it [dropped] what waited and erased nothing, or it [erased] a step (false:
     * refused), the chosen one when [selected], else the last.
     */
    data class Erase(val dropped: Dropped, val erased: Boolean, val selected: Boolean = false)

    /** The key the finger let go of last, while a press of it may still go on with its hold. */
    private data class LetGo(val key: Held, val pressedAt: Long, val at: Long, val length: NoteLength)

    private var pressedAt = 0L
    private var letGo: LetGo? = null

    /** The note a press of the letter key [key] writes now: its own in the chosen octave, or its black note while the sign waits (null: none). */
    fun writes(key: Int): Int? = FluteNotes.inOctave(key, signWaiting, octave)

    /**
     * Whether a press of the letter key [key] at [now] would be taken: a key that writes a note now ([writes]), or the
     * key let go of a moment ago, whose hold a press goes on with even when it writes nothing now (D held, ♯♭ tapped,
     * the touch screen splitting the hold; the final review of 2026-11-17).
     */
    fun takes(key: Int, now: Long): Boolean = rejoinable(key, now) != null || writes(key) != null

    /**
     * The letter key [key] (null: the rest key) went down at [now]. Null, and nothing changes, for a note key that is not
     * taken ([takes]).
     */
    fun press(key: Int?, now: Long): Press? {
        // Asked before a held key is ended: a press over a held key is a new note.
        rejoinable(key, now)?.let { return goOn(it, now) }
        val midi = if (key == null) null else writes(key) ?: return null
        val ended = release(now)
        letGo = null
        // The rest key cancels a waiting join (a slur does not run over a rest); a note key spends it, and a waiting
        // sign, on the note it adds.
        val joined = joinWaiting && midi != null
        val dropped = Dropped(join = joinWaiting && midi == null)
        joinWaiting = false
        if (midi != null) signWaiting = false
        pressedAt = now
        val step = if (midi == null) TuneRest(NoteLength.EIGHTH) else TuneNote(midi, NoteLength.EIGHTH, joined)
        val at = selected
        if (at != null) {
            held = Held(midi, index = at, key = key, replaces = true)
            replaceAt(at, step)
        } else {
            val atCap = tune.size >= Tune.MAX_NOTES
            held = Held(midi, index = tune.size.takeUnless { atCap }, key = key)
            if (!atCap) tune = tune + step
        }
        return Press(ended, rejoinGapMs = null, elapsedMs = 0, dropped)
    }

    /**
     * The key let go of ([again]) is down again at [now], and its hold goes on, signed or not. A touch screen can report
     * one hold as several (its palm rejection cancelling a flat finger and starting it over, or a bounce): within the
     * window the gap counts as held. Longer ago (only the rest key gets here) the pause was not silence the child asked
     * for. No key is held here ([rejoinable]), so none ends. A waiting join and sign stay for the next note, but the
     * rest key cancels the join, as it does on a new press.
     */
    private fun goOn(again: LetGo, now: Long): Press {
        letGo = null
        val rest = again.key.midi == null
        val dropped = Dropped(join = joinWaiting && rest)
        if (rest) joinWaiting = false
        val gap = now - again.at
        val elapsed = if (gap <= HOLD_REJOIN_MS) now - again.pressedAt else Tune.durationMs(again.length, WRITING_PULSE)
        pressedAt = now - elapsed
        held = again.key
        if (again.key.replaces) selected = again.key.index // the hold that changed the chosen step goes on
        grow(Tune.lengthOf(elapsed, WRITING_PULSE))
        return Press(ended = null, rejoinGapMs = gap, elapsedMs = elapsed, dropped)
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
        val length = Tune.lengthOf(heldMs, WRITING_PULSE)
        grow(length)
        held = null
        if (key.replaces) selected = null // the key that changed the chosen step lets the choice go
        letGo = LetGo(key, pressedAt, at = now, length = length)
        val joined = key.index?.let { (tune[it] as? TuneNote)?.joined } == true
        return Ended(key.midi, heldMs, length, added = key.index != null, joined = joined, replaced = key.replaces)
    }

    /**
     * A tap on the step at [index] of the staff: it is chosen for the keys to change, or let go when it was the chosen
     * one; another step moves the choice there. A step not in the tune, or a tap while a key is held, changes nothing.
     * Says the chosen step after it. A waiting join and sign stay; the rejoin window ends.
     */
    fun select(index: Int): Int? {
        if (held != null || index !in tune.indices) return selected
        selected = if (index == selected) null else index
        joinWaiting = false // the note it would join from ([joinFrom]) is another now
        forget()
        return selected
    }

    /**
     * Lets the chosen step go (a tap on the stage beside the notes, Play, Listen to me, the book, the speed panel, leaving
     * the tab); says whether one was chosen. The rejoin window ends with it, so the rest key does not go on with a rest
     * written in the middle, and a waiting join is dropped, as by [select]: the note it would join from changed.
     */
    fun unselect(): Boolean = (selected != null).also {
        if (it) {
            selected = null
            joinWaiting = false
            forget()
        }
    }

    /** Puts [item] in the place of the step at [index]; a note joined to that step stays joined only if [item] is a note. */
    private fun replaceAt(index: Int, item: TuneItem) {
        tune = tune.toMutableList().also {
            it[index] = item
            if (item !is TuneNote) unjoin(it, index + 1)
        }
    }

    /** Something other than the finger acted on the tune: no press goes on with the key let go of before. */
    fun forget() {
        letGo = null
    }

    /**
     * A tap of the ⌒ key: while a join waits it is cancelled (false); with nothing to join from ([canJoin]: the last
     * step, or the one before the chosen step) nothing changes (false); else the next note pressed will be joined to that
     * note (true), and the rejoin window ends. Choosing another step drops it ([select]).
     */
    fun join(): Boolean {
        if (joinWaiting || !canJoin(tune, selected)) {
            joinWaiting = false
            return false
        }
        joinWaiting = true
        forget()
        return true
    }

    /** Drops a waiting join; says whether one waited. */
    private fun cancelJoin(): Boolean = joinWaiting.also { joinWaiting = false }

    /**
     * A tap of the ♯♭ key: the next note key pressed writes its letter's black note (true), or a second tap drops it
     * (false). It never ends a held key, and it ends the rejoin window, as the join does.
     */
    fun sign(): Boolean {
        signWaiting = !signWaiting
        forget()
        return signWaiting
    }

    /** Drops a waiting sign; says whether one waited. */
    private fun cancelSign(): Boolean = signWaiting.also { signWaiting = false }

    /**
     * A tap of an octave pill: the letter keys write in [octave] from now on (true); the chosen one again, or one the
     * pills do not have, changes nothing at all (false). It never ends a held key, which is known by its letter
     * ([Held.key]), drops no waiting join or sign, and ends the rejoin window, as the sign does: the same key again
     * writes its new note.
     */
    fun chooseOctave(octave: Int): Boolean {
        if (octave == this.octave || octave !in FluteNotes.OCTAVES) return false
        this.octave = octave
        forget()
        return true
    }

    /**
     * Marks a breath, unless the tune is at the cap or its last step is one already, and drops a waiting join (a breath
     * in the middle of a slur would contradict it); a waiting sign stays for the next note key. With a step chosen the
     * breath takes its place instead, unless it is a breath already or a breath stands beside it: two breaths never
     * stand side by side ([Tune.sanitize] would drop the second on the next start).
     */
    fun breath(): Breath {
        val dropped = Dropped(join = cancelJoin())
        letGo = null
        selected?.let { at ->
            val beside = tune.getOrNull(at - 1) == TuneBreath || tune.getOrNull(at + 1) == TuneBreath
            val replaced = tune[at] != TuneBreath && !beside
            if (replaced) {
                replaceAt(at, TuneBreath)
                selected = null
            }
            return Breath(added = replaced, dropped, replaced = replaced)
        }
        val added = tune.size < Tune.MAX_NOTES && tune.lastOrNull() != TuneBreath
        if (added) tune = tune + TuneBreath
        return Breath(added, dropped)
    }

    /**
     * Drops the chosen step, and the choice with it, or else the last step; refused while a key is held or the tune is
     * empty. A waiting join or sign goes first: both dropped, nothing erased.
     */
    fun erase(): Erase {
        val dropped = dropWaiting()
        if (dropped.any || held != null || tune.isEmpty()) return Erase(dropped, erased = false)
        letGo = null // the step let go of last may be the one erased
        val at = selected ?: run {
            tune = tune.dropLast(1)
            return Erase(dropped, erased = true)
        }
        tune = tune.toMutableList().also {
            it.removeAt(at)
            // Two breaths that would now stand side by side are one, as [Tune.sanitize] keeps them.
            if (it.getOrNull(at - 1) == TuneBreath && it.getOrNull(at) == TuneBreath) it.removeAt(at)
            unjoin(it, at) // it was joined to the step removed
        }
        selected = null
        return Erase(dropped, erased = true, selected = true)
    }

    /** The step at [index] of [steps], if it is a joined note, joined no more. */
    private fun unjoin(steps: MutableList<TuneItem>, index: Int) {
        val note = steps.getOrNull(index) as? TuneNote ?: return
        if (note.joined) steps[index] = note.copy(joined = false)
    }

    /**
     * Drops a waiting join and a waiting sign, for what takes the tab from the keys (Play, Listen to me, the book, the
     * speed panel, leaving the tab); says what waited.
     */
    fun dropWaiting(): Dropped = Dropped(join = cancelJoin(), sign = cancelSign())

    /** Another book's [tune] takes this one's place. */
    fun open(tune: List<TuneItem>) {
        this.tune = tune
        selected = null
        forget()
        dropWaiting()
    }

    /** The hold a press of the key [key] at [now] goes on with: see the class comment; null for a new note or rest. */
    private fun rejoinable(key: Int?, now: Long): LetGo? {
        val last = letGo ?: return null
        if (last.key.key != key || held != null) return null
        // The rest key goes on with its rest however long the pause, but not with one written in place of a chosen
        // step: pressed again there, it writes the rest anew, as a note key does.
        val inTime = (key == null && !last.key.replaces) || now - last.at <= HOLD_REJOIN_MS
        return if (inTime) last else null
    }

    companion object {
        /**
         * The pace a held key is counted at, in quarter notes a minute whatever the time signature: 130 since
         * 2026-11-23 (the user: "reduce speed of adding notes to 130, just adding notes, not metronome"; 200, the ring's
         * fastest, from 2026-11-17), so a quarter is about 460 ms of holding and a whole note 1.6 s. Writing only: it is
         * shown nowhere and never reaches the metronome. The line between an eighth and a quarter is at 347 ms;
         * `flute.note`'s `heldMs` shows where a child's taps fall, and the number changes only at the user's word or
         * from that.
         */
        const val WRITING_BPM: Int = 130

        /** [WRITING_BPM] as the pulse [Tune.lengthOf] and [Tune.growAtMs] count a hold in. */
        val WRITING_PULSE: Pulse = Pulse(WRITING_BPM, TimeSignature.FOUR_FOUR)

        /** How long a key a screen reader activates is held for: one beat of the writing pace, a quarter note or rest. */
        val TAP_MS: Long = Tune.durationMs(NoteLength.QUARTER, WRITING_PULSE)

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

        /**
         * Whether the ⌒ key has a note to join the next one to: with no step chosen, the last step is a note and the
         * tune is not full ([Joins.canJoinAfter]); with one ([selected]), the step before it is a note.
         */
        fun canJoin(tune: List<TuneItem>, selected: Int?): Boolean =
            if (selected == null) Joins.canJoinAfter(tune) else joinFrom(tune, selected) != null

        /**
         * The note a waiting ⌒ joins the next note to: the last step with no step chosen, the step before the chosen one
         * with one ([selected]); null when that step is not a note. The staff draws the waiting arc from it.
         */
        fun joinFrom(tune: List<TuneItem>, selected: Int?): Int? {
            val from = if (selected == null) tune.lastIndex else selected - 1
            return from.takeIf { tune.getOrNull(it) is TuneNote }
        }
    }
}
