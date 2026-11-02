package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluteUiStateTest {

    @Test
    fun `lit note and index follow playback first, then the held key`() {
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))
        val playing = FluteUiState(tune = tune, heldMidi = 72, heldIndex = 1, playingIndex = 0)
        assertEquals(60, playing.litMidi)
        assertEquals(0, playing.litIndex)
        val held = FluteUiState(tune = tune, heldMidi = 72, heldIndex = 1)
        assertEquals(72, held.litMidi)
        assertEquals(1, held.litIndex)
        assertNull(FluteUiState().litMidi)
        assertNull(FluteUiState().litIndex)
    }

    @Test
    fun `empty, playing and cap flags`() {
        assertTrue(FluteUiState().isEmpty)
        assertFalse(FluteUiState().isPlaying)
        assertFalse(FluteUiState().atCap)
        assertTrue(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER)), playingIndex = 0).isPlaying)
        assertTrue(FluteUiState(tune = List(200) { TuneNote(60, NoteLength.QUARTER) }).atCap)
    }

    @Test
    fun `the rest key is lit while held or while its rest plays`() {
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneRest(NoteLength.HALF), TuneBreath)
        assertFalse(FluteUiState(tune = tune).restLit)
        assertTrue(FluteUiState(tune = tune, restHeld = true, heldIndex = 1).restLit)
        assertTrue(FluteUiState(tune = tune, restHeld = true).isHolding)
        assertTrue(FluteUiState(tune = tune, playingIndex = 1).restLit)
        assertNull(FluteUiState(tune = tune, playingIndex = 1).litMidi)
        assertEquals(1, FluteUiState(tune = tune, playingIndex = 1).litIndex)
        assertFalse(FluteUiState(tune = tune, playingIndex = 0).restLit)
        assertEquals(60, FluteUiState(tune = tune, playingIndex = 0).litMidi)
        assertFalse(FluteUiState(tune = tune).isHolding)
    }

    @Test
    fun `the shown note is only a note`() {
        assertEquals(67, FluteUiState(shown = Shown.Note(67)).shownMidi)
        assertNull(FluteUiState(shown = Shown.Rest).shownMidi)
        assertNull(FluteUiState(shown = Shown.Breath).shownMidi)
        assertNull(FluteUiState().shownMidi)
    }
}
