package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.LineMark.BarLine
import hr.bertlab.fluteassistant.domain.flute.LineMark.Breath
import hr.bertlab.fluteassistant.domain.flute.LineMark.Step
import hr.bertlab.fluteassistant.domain.flute.NoteLength.DOTTED_HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsTest {

    private fun lengths(vararg pieces: Any): List<StepPart> =
        pieces.map { if (it is NoteLength) StepPart.Length(it) else it as StepPart }

    @Test
    fun `a bar holds the signature's beats counted in quarter notes`() {
        assertEquals(4, Bars.halfBeatsPerBar(TimeSignature.TWO_FOUR))
        assertEquals(6, Bars.halfBeatsPerBar(TimeSignature.THREE_FOUR))
        assertEquals(8, Bars.halfBeatsPerBar(TimeSignature.FOUR_FOUR))
        assertEquals(10, Bars.halfBeatsPerBar(TimeSignature.FIVE_FOUR))
        assertEquals(6, Bars.halfBeatsPerBar(TimeSignature.SIX_EIGHT))
        assertEquals(3, Bars.halfBeatsPerBar(TimeSignature.THREE_EIGHT))
        assertEquals(8, Bars.halfBeatsPerBar(TimeSignature.TWO_TWO))
    }

    @Test
    fun `a bar line follows every full bar, the last one too`() {
        val tune = listOf(TuneNote(60, QUARTER), TuneNote(62, QUARTER), TuneNote(64, HALF), TuneNote(65, QUARTER), TuneNote(67, DOTTED_HALF))
        assertEquals(
            listOf(
                Step(0, lengths(QUARTER)), Step(1, lengths(QUARTER)), Step(2, lengths(HALF)), BarLine,
                Step(3, lengths(QUARTER)), Step(4, lengths(DOTTED_HALF)), BarLine,
            ),
            Bars.write(tune, TimeSignature.FOUR_FOUR),
        )
    }

    @Test
    fun `a note held into the next bar is split there and tied`() {
        val tune = listOf(TuneNote(60, DOTTED_HALF), TuneNote(69, HALF), TuneNote(67, QUARTER))
        assertEquals(
            listOf(Step(0, lengths(DOTTED_HALF)), Step(1, lengths(QUARTER, BarLine, QUARTER)), Step(2, lengths(QUARTER))),
            Bars.write(tune, TimeSignature.FOUR_FOUR),
        )
    }

    @Test
    fun `a rest is split at the bar line the same way`() {
        val tune = listOf(TuneNote(60, QUARTER), TuneRest(WHOLE))
        assertEquals(
            listOf(Step(0, lengths(QUARTER)), Step(1, lengths(HALF, BarLine, HALF))), // two beats into the second bar
            Bars.write(tune, TimeSignature.THREE_FOUR),
        )
    }

    @Test
    fun `a breath takes no time and stays where it was put`() {
        val tune = listOf(TuneNote(60, HALF), TuneNote(62, HALF), TuneBreath, TuneNote(64, HALF), TuneBreath)
        assertEquals(
            listOf(Step(0, lengths(HALF)), Step(1, lengths(HALF)), BarLine, Breath(2), Step(3, lengths(HALF)), Breath(4)),
            Bars.write(tune, TimeSignature.FOUR_FOUR),
        )
    }

    @Test
    fun `a piece with no single symbol is written as tied lengths`() {
        // 3/8: a bar is a quarter and an eighth, so a half note after a quarter needs four pieces.
        val tune = listOf(TuneNote(60, QUARTER), TuneNote(62, HALF))
        assertEquals(
            listOf(Step(0, lengths(QUARTER)), Step(1, lengths(EIGHTH, BarLine, QUARTER, EIGHTH)), BarLine),
            Bars.write(tune, TimeSignature.THREE_EIGHT),
        )
        // 2/4: a whole note fills two bars.
        assertEquals(
            listOf(Step(0, lengths(HALF, BarLine, HALF)), BarLine),
            Bars.write(listOf(TuneNote(60, WHOLE)), TimeSignature.TWO_FOUR),
        )
    }

    @Test
    fun `six eight bars hold three quarter notes`() {
        val tune = listOf(TuneNote(60, QUARTER), TuneNote(62, HALF), TuneNote(64, QUARTER))
        assertEquals(
            listOf(Step(0, lengths(QUARTER)), Step(1, lengths(HALF)), BarLine, Step(2, lengths(QUARTER))),
            Bars.write(tune, TimeSignature.SIX_EIGHT),
        )
    }

    @Test
    fun `every piece of a step adds up to its length`() {
        val lengthsAll = NoteLength.entries
        for (signature in TimeSignature.entries) {
            val tune = List(40) { i -> if (i % 3 == 0) TuneRest(lengthsAll[i % 5]) else TuneNote(60, lengthsAll[(i * 2) % 5]) }
            val marks = Bars.write(tune, signature)
            val steps = marks.filterIsInstance<Step>()
            assertEquals(signature.name, tune.indices.toList(), steps.map { it.index })
            for (step in steps) {
                val total = step.parts.filterIsInstance<StepPart.Length>().sumOf { it.length.halfBeats }
                val expected = when (val item = tune[step.index]) {
                    is TuneNote -> item.length.halfBeats
                    is TuneRest -> item.length.halfBeats
                    TuneBreath -> 0
                }
                assertEquals("${signature.name} step ${step.index}", expected, total)
            }
            // Bar lines fall exactly on bar boundaries.
            var position = 0
            for (mark in marks) {
                val parts = if (mark is Step) mark.parts else listOf(mark)
                for (part in parts) {
                    when (part) {
                        is StepPart.Length -> position += part.length.halfBeats
                        BarLine -> assertTrue(signature.name, position > 0 && position % Bars.halfBeatsPerBar(signature) == 0)
                        else -> Unit
                    }
                }
            }
        }
    }

    @Test
    fun `nothing to write for an empty tune`() {
        assertTrue(Bars.write(emptyList(), TimeSignature.FOUR_FOUR).isEmpty())
    }
}
