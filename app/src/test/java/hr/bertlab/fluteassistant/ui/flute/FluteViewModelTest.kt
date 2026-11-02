package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.DOTTED_HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.fakes.FakeClock
import hr.bertlab.fluteassistant.fakes.FakeFluteTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluteViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsStore()
    private val tonePlayer = FakeFluteTonePlayer()
    private val clock = FakeClock(now = 10_000L)
    private val logger = FakeLogger()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FluteViewModel(settings, tonePlayer, clock, logger)

    private fun freq(midi: Int) = NoteMath.midiToFrequency(midi)

    private fun on(midi: Int) = FakeFluteTonePlayer.NoteOn(freq(midi), TunerViewModel.REFERENCE_TONE_VOLUME)

    private val FluteViewModel.current: FluteUiState get() = state.value

    /** The length of a step the test knows is a note. */
    private val TuneItem.length get() = (this as TuneNote).length

    /** Presses [midi], holds it for [heldMs] of fake time and lets go. */
    private fun FluteViewModel.tap(midi: Int, heldMs: Long) {
        onKeyPressed(midi)
        clock.now += heldMs
        onKeyReleased(midi)
    }

    /** A two-note tune at the default 120 bpm (c1 a quarter, g1 a half note) with playback just started. */
    private fun playingViewModel(): FluteViewModel = viewModel().apply {
        tap(60, 500)
        tap(67, 1000)
        togglePlayback()
    }

    @Test
    fun `starts empty and silent`() {
        val vm = viewModel()
        assertEquals(FluteUiState(), vm.current)
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `a stored tune is restored and sanitized`() {
        settings.tune = listOf(TuneNote(60, HALF), TuneNote(61, QUARTER), TuneNote(84, WHOLE))
        val vm = viewModel()
        assertEquals(listOf(TuneNote(60, HALF), TuneNote(84, WHOLE)), vm.current.tune)
        assertNull(vm.current.shownMidi)
    }

    @Test
    fun `entering the tab opens the player and leaving closes it`() {
        val vm = viewModel()
        vm.enterTab()
        assertEquals(1, tonePlayer.openCount)
        vm.leaveTab()
        assertEquals(1, tonePlayer.closeCount)
    }

    @Test
    fun `a press sounds the note at once, shows it and adds it as an eighth`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.heldMidi)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(67, vm.current.shownMidi)
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune)
        assertEquals(EIGHTH, vm.current.heldLength)
        assertEquals(0, vm.current.holdBeats)
        assertEquals(vm.current.tune, settings.tune)
    }

    @Test
    fun `a release stops the sound and records the length the hold made`() {
        val vm = viewModel()
        vm.tap(67, 450) // 0.9 of a beat at 120 bpm
        assertEquals(1, tonePlayer.offCount)
        assertNull(tonePlayer.sounding)
        assertNull(vm.current.heldMidi)
        assertNull(vm.current.heldIndex)
        assertNull(vm.current.heldLength)
        assertEquals(67, vm.current.shownMidi)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        val event = logger.events("flute.note").single()
        assertEquals(67, event["midi"])
        assertEquals(450L, event["heldMs"])
        assertEquals(QUARTER, event["length"])
        assertEquals(true, event["added"])
    }

    @Test
    fun `a quick tap is an eighth and a long hold stays a whole note`() {
        val vm = viewModel()
        vm.tap(67, 50)
        vm.tap(69, 7000)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(69, WHOLE)), vm.current.tune)
    }

    @Test
    fun `a held note grows beat by beat and keeps its length when let go`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(67)
        advanceTimeBy(374)
        assertEquals(EIGHTH, vm.current.tune[0].length)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(QUARTER, vm.current.tune[0].length)
        assertEquals(1, vm.current.holdBeats)
        advanceTimeBy(375)
        runCurrent()
        assertEquals(HALF, vm.current.heldLength)
        assertEquals(2, vm.current.holdBeats)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(DOTTED_HALF, vm.current.tune[0].length)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(WHOLE, vm.current.tune[0].length)
        assertEquals(4, vm.current.holdBeats)
        clock.now += 1800
        vm.onKeyReleased(67)
        assertEquals(listOf(TuneNote(67, WHOLE)), vm.current.tune)
        assertNull(vm.current.holdBeats)
        advanceTimeBy(5000)
        assertEquals(listOf(TuneNote(67, WHOLE)), vm.current.tune)
    }

    @Test
    fun `beats are counted at the metronome's tempo, kept in the ring's range`() {
        settings.config = MetronomeConfig(bpm = 60)
        val vm = viewModel()
        vm.tap(67, 450) // under 3/4 of a one-second beat
        assertEquals(EIGHTH, vm.current.tune[0].length)
        settings.config = MetronomeConfig(bpm = 30) // an older build's tempo, below the ring: counted at 40
        vm.tap(69, 1200) // 0.8 of a 1.5 s beat
        assertEquals(QUARTER, vm.current.tune[1].length)
    }

    @Test
    fun `releasing a key that is not sounding is ignored`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        vm.onKeyReleased(69)
        assertEquals(0, tonePlayer.offCount)
        assertEquals(67, vm.current.heldMidi)
    }

    @Test
    fun `the newest press wins`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 400
        vm.onKeyPressed(69)
        assertEquals(on(69), tonePlayer.sounding)
        assertEquals(listOf(TuneNote(67, QUARTER), TuneNote(69, EIGHTH)), vm.current.tune)
        assertEquals(1, vm.current.heldIndex)
        clock.now += 500
        vm.onKeyReleased(67)
        assertEquals(0, tonePlayer.offCount)
        assertEquals(69, vm.current.heldMidi)
        vm.onKeyReleased(69)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(QUARTER, vm.current.tune[1].length)
    }

    @Test
    fun `a press outside the keys is ignored`() {
        val vm = viewModel()
        vm.onKeyPressed(61)
        assertTrue(tonePlayer.notesOn.isEmpty())
        assertTrue(vm.current.tune.isEmpty())
    }

    @Test
    fun `at the cap a press still sounds but is not added`() {
        settings.tune = List(200) { TuneNote(60, QUARTER) }
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.shownMidi)
        assertNull(vm.current.heldIndex)
        assertNull(vm.current.litIndex)
        assertEquals(200, vm.current.tune.size)
        clock.now += 400
        vm.onKeyReleased(67)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(200, vm.current.tune.size)
        assertEquals(TuneNote(60, QUARTER), vm.current.tune.last())
        assertEquals(false, logger.events("flute.note").single()["added"])
    }

    @Test
    fun `erase removes the last note and is ignored while a key is held or the tune is empty`() {
        val vm = viewModel()
        vm.eraseLast()
        assertTrue(logger.events("flute.erase").isEmpty())
        vm.tap(67, 300)
        vm.tap(69, 300)
        vm.eraseLast()
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        assertEquals(1, logger.events("flute.erase").size)
        vm.onKeyPressed(72)
        vm.eraseLast()
        assertEquals(2, vm.current.tune.size)
    }

    @Test
    fun `play sounds each note for its length at the tempo with a gap, lighting it as it goes`() = runTest(dispatcher) {
        val vm = playingViewModel()
        assertTrue(vm.current.isPlaying)
        assertEquals(0, vm.current.playingIndex)
        assertEquals(60, vm.current.shownMidi)
        assertEquals(on(60), tonePlayer.sounding)
        assertEquals(60, vm.current.litMidi)
        advanceTimeBy(421) // a beat is 500 ms, the last 80 of them silent
        assertEquals(3, tonePlayer.offCount)
        assertEquals(0, vm.current.playingIndex)
        advanceTimeBy(80)
        assertEquals(1, vm.current.playingIndex)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.shownMidi)
        advanceTimeBy(920) // two beats
        assertEquals(4, tonePlayer.offCount)
        advanceTimeBy(80)
        assertNull(vm.current.playingIndex)
        assertFalse(vm.current.isPlaying)
        assertEquals(67, vm.current.shownMidi)
        val play = logger.events("flute.play").single()
        assertEquals(2, play["notes"])
        assertEquals(120, play["bpm"])
        assertEquals(1, logger.events("flute.play_done").size)
    }

    @Test
    fun `playback follows the metronome's tempo when it changes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(60, 500)
        settings.config = MetronomeConfig(bpm = 60)
        vm.togglePlayback()
        advanceTimeBy(919)
        assertEquals(on(60), tonePlayer.sounding)
        advanceTimeBy(2)
        assertNull(tonePlayer.sounding)
        assertEquals(QUARTER, vm.current.tune[0].length)
    }

    @Test
    fun `stop ends playback at once`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.togglePlayback()
        assertFalse(vm.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals(1, logger.events("flute.stop").size)
        val notesOn = tonePlayer.notesOn.size
        advanceTimeBy(5000)
        assertEquals(notesOn, tonePlayer.notesOn.size)
    }

    @Test
    fun `a press during playback stops it and plays the key`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.onKeyPressed(72)
        assertFalse(vm.current.isPlaying)
        assertEquals(on(72), tonePlayer.sounding)
        assertEquals(3, vm.current.tune.size)
        assertEquals(2, vm.current.heldIndex)
    }

    @Test
    fun `erase during playback stops it first`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.eraseLast()
        assertFalse(vm.current.isPlaying)
        assertEquals(1, vm.current.tune.size)
    }

    @Test
    fun `play with a key held releases it first`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 500
        vm.togglePlayback()
        assertEquals(QUARTER, vm.current.tune[0].length)
        assertNull(vm.current.heldMidi)
        assertEquals(0, vm.current.playingIndex)
    }

    @Test
    fun `play does nothing while the tune is empty`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.togglePlayback()
        assertFalse(vm.current.isPlaying)
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `leaving the tab silences the flute but keeps the tune`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 350
        vm.leaveTab()
        assertEquals(1, tonePlayer.offCount)
        assertNull(vm.current.heldMidi)
        assertEquals(listOf(TuneNote(60, EIGHTH)), vm.current.tune)
        assertEquals(1, tonePlayer.closeCount)
        assertEquals(vm.current.tune, settings.tune)
        vm.enterTab()
        vm.togglePlayback()
        advanceTimeBy(100)
        vm.leaveTab()
        assertFalse(vm.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals(2, tonePlayer.closeCount)
        assertEquals(1, vm.current.tune.size)
    }

    @Test
    fun `stop says why`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.togglePlayback()
        assertEquals("pill", logger.events("flute.stop").single()["reason"])
        vm.togglePlayback()
        advanceTimeBy(100)
        vm.onKeyPressed(72)
        assertEquals("key", logger.events("flute.stop")[1]["reason"])
        vm.onKeyReleased(72)
        vm.togglePlayback()
        advanceTimeBy(100)
        vm.eraseLast()
        assertEquals("erase", logger.events("flute.stop")[2]["reason"])
    }

    @Test
    fun `leaving the tab mid-playback logs why it stopped`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.leaveTab()
        assertEquals("leave", logger.events("flute.stop").single()["reason"])
        assertTrue(logger.events("flute.play_done").isEmpty())
    }

    @Test
    fun `a release after leaving the tab is ignored`() {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 350
        vm.leaveTab()
        vm.onKeyReleased(60)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(listOf(TuneNote(60, EIGHTH)), vm.current.tune)
        assertEquals(1, logger.events("flute.note").size)
    }

    @Test
    fun `a real press on an activated key is not released by the activation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyActivated(67)
        advanceTimeBy(100)
        vm.onKeyPressed(67) // a finger lands on the same key before the activation's beat is up
        advanceTimeBy(501)
        assertEquals(67, vm.current.heldMidi)
        assertEquals(0, tonePlayer.offCount)
        assertEquals(2, vm.current.tune.size)
    }

    @Test
    fun `activating a key sounds it for one beat and adds a quarter note`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyActivated(67)
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(501)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
    }

    /** Holds the rest key for [heldMs] of fake time and lets go. */
    private fun FluteViewModel.rest(heldMs: Long) {
        onRestPressed()
        clock.now += heldMs
        onRestReleased()
    }

    @Test
    fun `the rest key adds a silent rest that grows like a note`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestPressed()
        assertEquals(listOf(TuneRest(EIGHTH)), vm.current.tune)
        assertTrue(vm.current.restHeld)
        assertTrue(vm.current.restLit)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(Shown.Rest, vm.current.shown)
        assertNull(vm.current.shownMidi)
        assertEquals(0, vm.current.holdBeats)
        assertTrue(tonePlayer.notesOn.isEmpty())
        advanceTimeBy(751)
        runCurrent()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        assertEquals(2, vm.current.holdBeats)
        clock.now += 1000
        vm.onRestReleased()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        assertFalse(vm.current.restHeld)
        assertNull(vm.current.heldLength)
        assertEquals(Shown.Rest, vm.current.shown)
        assertEquals(vm.current.tune, settings.tune)
        assertEquals(0, tonePlayer.offCount)
        val event = logger.events("flute.rest").single()
        assertEquals(1000L, event["heldMs"])
        assertEquals(HALF, event["length"])
        assertEquals(true, event["added"])
        assertTrue(logger.events("flute.note").isEmpty())
    }

    @Test
    fun `a rest takes over a held note and silences it`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 500
        vm.onRestPressed()
        assertNull(tonePlayer.sounding)
        assertNull(vm.current.heldMidi)
        assertEquals(listOf(TuneNote(67, QUARTER), TuneRest(EIGHTH)), vm.current.tune)
        vm.onKeyReleased(67) // the finger on the note lifts after the rest took over
        assertTrue(vm.current.restHeld)
        assertEquals(1, tonePlayer.offCount)
    }

    @Test
    fun `a note takes over a held rest`() {
        val vm = viewModel()
        vm.onRestPressed()
        clock.now += 500
        vm.onKeyPressed(60)
        assertEquals(on(60), tonePlayer.sounding)
        assertFalse(vm.current.restHeld)
        assertEquals(listOf(TuneRest(QUARTER), TuneNote(60, EIGHTH)), vm.current.tune)
        vm.onRestReleased()
        assertEquals(60, vm.current.heldMidi)
        assertEquals(1, logger.events("flute.rest").size)
    }

    @Test
    fun `a breath is a mark with no sound, never twice in a row`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.onBreath()
        assertEquals(listOf(TuneNote(60, QUARTER), TuneBreath), vm.current.tune)
        assertEquals(Shown.Breath, vm.current.shown)
        assertEquals(vm.current.tune, settings.tune)
        vm.onBreath()
        assertEquals(2, vm.current.tune.size)
        assertEquals(listOf(true, false), logger.events("flute.breath").map { it["added"] })
        assertEquals(1, tonePlayer.notesOn.size)
    }

    @Test
    fun `a breath ends a held note`() {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 500
        vm.onBreath()
        assertNull(tonePlayer.sounding)
        assertNull(vm.current.heldMidi)
        assertEquals(listOf(TuneNote(60, QUARTER), TuneBreath), vm.current.tune)
    }

    @Test
    fun `play is silent through a rest and goes straight past a breath`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.rest(1000)
        vm.onBreath()
        vm.tap(67, 500)
        vm.togglePlayback()
        assertEquals(0, vm.current.playingIndex)
        advanceTimeBy(501)
        assertEquals(1, vm.current.playingIndex)
        assertEquals(Shown.Rest, vm.current.shown)
        assertNull(tonePlayer.sounding)
        assertTrue(vm.current.restLit)
        assertNull(vm.current.litMidi)
        assertEquals(1, vm.current.litIndex)
        advanceTimeBy(998) // a half rest: two whole beats of silence, no gap to take out
        assertEquals(1, vm.current.playingIndex)
        assertEquals(3, tonePlayer.notesOn.size) // the two taps, then c1 played back: nothing for the rest
        advanceTimeBy(2)
        assertEquals(3, vm.current.playingIndex) // the breath took no time
        assertEquals(on(67), tonePlayer.sounding)
        assertFalse(vm.current.restLit)
        advanceTimeBy(500)
        assertFalse(vm.current.isPlaying)
        val play = logger.events("flute.play").single()
        assertEquals(2, play["notes"])
        assertEquals(1, play["rests"])
    }

    @Test
    fun `erase removes rests and breaths and waits while the rest key is held`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.rest(500)
        vm.onBreath()
        vm.eraseLast()
        assertEquals(listOf(TuneNote(60, QUARTER), TuneRest(QUARTER)), vm.current.tune)
        vm.onRestPressed()
        vm.eraseLast()
        assertEquals(3, vm.current.tune.size)
        clock.now += 100
        vm.onRestReleased()
        vm.eraseLast()
        vm.eraseLast()
        assertEquals(listOf(TuneNote(60, QUARTER)), vm.current.tune)
    }

    @Test
    fun `activating the rest key adds a quarter rest`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestActivated()
        assertTrue(vm.current.restHeld)
        advanceTimeBy(501)
        assertFalse(vm.current.restHeld)
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `at the cap a rest or a breath is shown but not added`() {
        settings.tune = List(200) { TuneNote(60, QUARTER) }
        val vm = viewModel()
        vm.onRestPressed()
        assertEquals(Shown.Rest, vm.current.shown)
        assertNull(vm.current.heldIndex)
        vm.onRestReleased()
        vm.onBreath()
        assertEquals(Shown.Breath, vm.current.shown)
        assertEquals(200, vm.current.tune.size)
        assertEquals(TuneNote(60, QUARTER), vm.current.tune.last())
        assertEquals(false, logger.events("flute.rest").single()["added"])
        assertEquals(false, logger.events("flute.breath").single()["added"])
    }

    @Test
    fun `leaving the tab or pressing play ends a held rest`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestPressed()
        clock.now += 500
        vm.leaveTab()
        assertFalse(vm.current.restHeld)
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        assertEquals(0, tonePlayer.offCount)
        vm.enterTab()
        vm.onRestPressed()
        clock.now += 500
        vm.togglePlayback()
        assertFalse(vm.current.restHeld)
        assertEquals(listOf(TuneRest(QUARTER), TuneRest(QUARTER)), vm.current.tune)
        assertTrue(vm.current.isPlaying)
    }

    @Test
    fun `the line takes the metronome's time signature, read again when the tab opens`() {
        settings.config = MetronomeConfig(timeSignature = TimeSignature.THREE_FOUR)
        val vm = viewModel()
        assertEquals(TimeSignature.THREE_FOUR, vm.current.timeSignature)
        settings.config = MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT)
        vm.enterTab()
        assertEquals(TimeSignature.SIX_EIGHT, vm.current.timeSignature)
    }

    @Test
    fun `in six eight the flute counts a click as an eighth note, like the metronome`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 500) // one click
        vm.tap(62, 1000) // two clicks
        assertEquals(listOf(TuneNote(60, EIGHTH), TuneNote(62, QUARTER)), vm.current.tune)
        vm.togglePlayback()
        advanceTimeBy(501)
        assertEquals(1, vm.current.playingIndex) // the eighth lasted one click
        advanceTimeBy(1000)
        assertFalse(vm.current.isPlaying) // and the quarter two
        val play = logger.events("flute.play").single()
        assertEquals(120, play["bpm"])
        assertEquals("6/8", play["time"])
    }

    @Test
    fun `a screen reader's quarter note lasts two clicks in six eight`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.onKeyActivated(67)
        advanceTimeBy(999)
        assertEquals(67, vm.current.heldMidi)
        advanceTimeBy(2)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
    }

    @Test
    fun `in six eight a held note's dots fill click by click of its length`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(1, vm.current.holdBeats) // an eighth: one click
        advanceTimeBy(751)
        runCurrent()
        assertEquals(2, vm.current.holdBeats) // a quarter: two clicks
        advanceTimeBy(750)
        runCurrent()
        assertEquals(4, vm.current.holdBeats) // a half: four
    }
}
