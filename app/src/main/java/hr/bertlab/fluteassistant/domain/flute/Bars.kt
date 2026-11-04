package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.model.TimeSignature

/** What the tune line draws, left to right ([Bars.write]). */
sealed interface LineMark {
    /**
     * Step [index] of the tune, a note or a rest, written as [parts]: the lengths it is drawn in
     * (tied, for a note) with a [BarLine] between two of them where it runs into the next bar.
     */
    data class Step(val index: Int, val parts: List<StepPart>) : LineMark

    /** Step [index] of the tune, a breath: no time, so it never moves a bar line. */
    data class Breath(val index: Int) : LineMark

    /** The line after a full bar, between steps or inside one that crosses it. */
    data object BarLine : LineMark, StepPart
}

/** One piece of a written [LineMark.Step]: a length, or the [LineMark.BarLine] it crosses. */
sealed interface StepPart {
    data class Length(val length: NoteLength) : StepPart
}

/**
 * Writes the child's tune in bars of the metronome's time signature, as the tune line shows it:
 * a bar line after every full bar (the last one too, so the child sees a bar fill up), and a note
 * or a rest that runs past a bar line split there into pieces, the way written music does it. A
 * piece no single symbol can show (two and a half beats, say) is written as the longest lengths
 * that fit, one after another. The tune itself never changes: this is only how it is drawn.
 */
object Bars {
    /** How many half beats of the flute's beat (a quarter note) fill a bar of [signature]: 8 for 4/4, 6 for 6/8. */
    fun halfBeatsPerBar(signature: TimeSignature): Int = signature.beatsPerBar * 8 / signature.noteValue

    fun write(tune: List<TuneItem>, signature: TimeSignature): List<LineMark> {
        val bar = halfBeatsPerBar(signature)
        val marks = mutableListOf<LineMark>()
        var position = 0 // half beats into the current bar
        for ((index, item) in tune.withIndex()) {
            val length = when (item) {
                is TuneNote -> item.length
                is TuneRest -> item.length
                TuneBreath -> {
                    marks += LineMark.Breath(index)
                    continue
                }
            }
            val parts = mutableListOf<StepPart>()
            var left = length.halfBeats
            var barFull = false
            while (left > 0) {
                if (barFull) parts += LineMark.BarLine
                val taken = minOf(left, bar - position)
                lengthsOf(taken).mapTo(parts) { StepPart.Length(it) }
                left -= taken
                position += taken
                barFull = position == bar
                if (barFull) position = 0
            }
            marks += LineMark.Step(index, parts)
            if (barFull) marks += LineMark.BarLine
        }
        return marks
    }

    /**
     * Which of [marks] the staff scrolls to its left edge while step [litIndex] of [tune] sounds,
     * is waited on or is held: the last note before it, so the note the child just played stays
     * on the staff with what it earned (its star, or the note heard for it) and the lit step is the
     * second thing in view (the user, 2026-11-04); the lit step itself when no note comes before it;
     * the line's end when nothing is lit. Rests, breaths and bar lines between the two stay in view
     * with them, as a rest between them would otherwise hide the star of the note before it.
     */
    fun anchor(marks: List<LineMark>, tune: List<TuneItem>, litIndex: Int?): Int {
        val lit = marks.indexOfFirst { it is LineMark.Step && it.index == litIndex }
        if (lit < 0) return marks.lastIndex
        for (i in lit - 1 downTo 0) {
            val mark = marks[i]
            if (mark is LineMark.Step && tune[mark.index] is TuneNote) return i
        }
        return lit
    }

    /** [halfBeats] as the longest lengths that fit, longest first: 5 is a half and an eighth. */
    private fun lengthsOf(halfBeats: Int): List<NoteLength> {
        val lengths = mutableListOf<NoteLength>()
        var left = halfBeats
        while (left > 0) {
            val next = NoteLength.entries.last { it.halfBeats <= left }
            lengths += next
            left -= next.halfBeats
        }
        return lengths
    }
}
