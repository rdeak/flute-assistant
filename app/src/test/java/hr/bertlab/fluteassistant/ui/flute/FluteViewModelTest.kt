package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.NoteLength.DOTTED_HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.flute.Pulse
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
import hr.bertlab.fluteassistant.fakes.FakeTunerEngine
import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import hr.bertlab.fluteassistant.ui.tuner.HearingTracker
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
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
    private val engine = FakeTunerEngine()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FluteViewModel(settings, tonePlayer, engine, clock, logger)

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
    fun `a tune written is kept in the open book with the metronome's speed and beat`() {
        settings.openBook = 2
        settings.config = MetronomeConfig(bpm = 100, timeSignature = TimeSignature.THREE_FOUR)
        val vm = viewModel()
        vm.tap(60, 600) // one beat at 100 bpm: a quarter
        assertEquals(Book(listOf(TuneNote(60, QUARTER)), 100, TimeSignature.THREE_FOUR), settings.books[2])
        assertTrue(settings.books[0].isEmpty)
    }

    @Test
    fun `creation opens the stored open book`() {
        settings.books[4] = Book(listOf(TuneNote(67, HALF)))
        settings.openBook = 4
        assertEquals(listOf(TuneNote(67, HALF)), viewModel().current.tune)
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
        assertEquals(QUARTER, vm.current.heldLength)
        advanceTimeBy(375)
        runCurrent()
        assertEquals(HALF, vm.current.heldLength)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(DOTTED_HALF, vm.current.tune[0].length)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(WHOLE, vm.current.tune[0].length)
        assertEquals(WHOLE, vm.current.heldLength)
        clock.now += 1800
        vm.onKeyReleased(67)
        assertEquals(listOf(TuneNote(67, WHOLE)), vm.current.tune)
        assertNull(vm.current.heldLength)
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
        assertEquals(EIGHTH, vm.current.heldLength)
        assertTrue(tonePlayer.notesOn.isEmpty())
        advanceTimeBy(751)
        runCurrent()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        assertEquals(HALF, vm.current.heldLength)
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
    fun `in six eight a held note grows at the pace of the eighth-note clicks`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(EIGHTH, vm.current.heldLength) // one click
        advanceTimeBy(751)
        runCurrent()
        assertEquals(QUARTER, vm.current.heldLength) // two clicks of 500 ms make a beat
        advanceTimeBy(750)
        runCurrent()
        assertEquals(HALF, vm.current.heldLength) // four
    }

    @Test
    fun `the knob hops on every beat of the playing tune and stops with it`() = runTest(dispatcher) {
        val vm = playingViewModel()
        assertEquals(1L, vm.current.beatPulse) // the first beat, with the first note
        advanceTimeBy(499)
        assertEquals(1L, vm.current.beatPulse)
        advanceTimeBy(1)
        runCurrent() // advanceTimeBy stops short of the tasks due at its end
        assertEquals(2L, vm.current.beatPulse) // 500 ms: a beat at 120
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3L, vm.current.beatPulse)
        advanceTimeBy(5000) // the tune is 1500 ms long
        assertFalse(vm.current.isPlaying)
        val afterEnd = vm.current.beatPulse
        advanceTimeBy(5000)
        assertEquals(afterEnd, vm.current.beatPulse)
    }

    @Test
    fun `stop ends the hops too`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.togglePlayback()
        val stopped = vm.current.beatPulse
        advanceTimeBy(5000)
        assertEquals(stopped, vm.current.beatPulse)
    }

    @Test
    fun `in six eight the knob hops on every click`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 2000) // a half note: four clicks
        vm.togglePlayback()
        assertEquals(1L, vm.current.beatPulse)
        advanceTimeBy(500) // one click is an eighth, 500 ms
        runCurrent()
        assertEquals(2L, vm.current.beatPulse)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3L, vm.current.beatPulse)
    }

    @Test
    fun `on a long note the animal goes on a beat with each click, from the note's head`() = runTest(dispatcher) {
        val vm = playingViewModel() // c1 a quarter, g1 a half note, 500 ms a click
        assertEquals(0, vm.current.playingIndex)
        assertEquals(0, vm.current.playingHalfBeat)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, vm.current.playingIndex) // the half note begins on a click, over its head
        assertEquals(0, vm.current.playingHalfBeat)
        assertEquals(2L, vm.current.beatPulse)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, vm.current.playingIndex) // its second beat: a hop to the next place, not the same one
        assertEquals(2, vm.current.playingHalfBeat)
        assertEquals(3L, vm.current.beatPulse)
        // The note still sounds past its last click and ends a short gap (80 ms) before the step does.
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(419)
        runCurrent()
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(1)
        runCurrent()
        assertNull(tonePlayer.sounding)
    }

    @Test
    fun `in six eight a quarter note is two clicks, two places`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 2000) // a half note: four clicks of an eighth
        vm.togglePlayback()
        val places = mutableListOf(vm.current.playingHalfBeat)
        repeat(3) {
            advanceTimeBy(500)
            runCurrent()
            places += vm.current.playingHalfBeat
        }
        assertEquals(listOf(0, 1, 2, 3), places)
    }

    @Test
    fun `an eighth off the click moves the animal on without a hop`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(60, 250) // two eighths and a quarter at 120 bpm
        vm.tap(62, 250)
        vm.tap(64, 500)
        vm.togglePlayback()
        assertEquals(1L, vm.current.beatPulse)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, vm.current.playingIndex)
        assertEquals(0, vm.current.playingHalfBeat)
        assertEquals(1L, vm.current.beatPulse) // half way through the beat: no click
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, vm.current.playingIndex)
        assertEquals(2L, vm.current.beatPulse)
    }

    @Test
    fun `opening the speed panel stops playback and says why`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.openSpeed()
        assertFalse(vm.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals("speed", logger.events("flute.stop").single()["reason"])
        val open = logger.events("flute.speed_open").single()
        assertEquals(120, open["bpm"])
        assertEquals(TimeSignature.FOUR_FOUR.label, open["time"])
    }

    @Test
    fun `opening the speed panel ends a held key`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 1000
        vm.openSpeed()
        assertNull(vm.current.heldMidi)
        assertNull(tonePlayer.sounding)
        assertEquals(HALF, vm.current.tune.single().length)
    }

    @Test
    fun `closing the speed panel reads the metronome's time signature again`() {
        val vm = viewModel()
        settings.config = MetronomeConfig(timeSignature = TimeSignature.THREE_FOUR)
        vm.closeSpeed()
        assertEquals(TimeSignature.THREE_FOUR, vm.current.timeSignature)
        assertEquals(1, logger.events("flute.speed_done").size)
    }

    @Test
    fun `a key let go of and pressed again at once goes on with the same note`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 300
        advanceTimeBy(300)
        vm.onKeyReleased(67) // the touch screen reported the hold as over: a cancel or a bounce
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune)
        clock.now += 20
        advanceTimeBy(20)
        vm.onKeyPressed(67) // and the same finger as down again
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.heldMidi)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune) // still the one note
        assertEquals(EIGHTH, vm.current.heldLength)
        advanceTimeBy(55) // ¾ of a beat after the first press: the hold grows from where it was
        runCurrent()
        assertEquals(QUARTER, vm.current.tune[0].length)
        clock.now += 680 // a second held all told
        advanceTimeBy(680)
        vm.onKeyReleased(67)
        assertEquals(listOf(TuneNote(67, HALF)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        assertEquals(2, tonePlayer.offCount)
        val rejoin = logger.events("flute.rejoin").single()
        assertEquals(67, rejoin["midi"])
        assertEquals(20L, rejoin["gapMs"])
        assertEquals(listOf(300L, 1000L), logger.events("flute.note").map { it["heldMs"] })
    }

    @Test
    fun `a note gone on with shows the length its whole hold has made`() {
        val vm = viewModel()
        vm.tap(67, 370)
        clock.now += 10 // 380 ms into the hold: it passed ¾ of a beat during the gap
        vm.onKeyPressed(67)
        assertEquals(QUARTER, vm.current.heldLength)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
    }

    @Test
    fun `pressed again after a pause, the same key is a new note`() {
        val vm = viewModel()
        vm.tap(67, 300)
        clock.now += FluteViewModel.HOLD_REJOIN_MS + 1
        vm.tap(67, 300)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(67, EIGHTH)), vm.current.tune)
        assertTrue(logger.events("flute.rejoin").isEmpty())
    }

    @Test
    fun `a key let go of is not gone on with after another key, an erase or the rest`() {
        val vm = viewModel()
        vm.tap(67, 300)
        clock.now += 10
        vm.tap(69, 10)
        clock.now += 10
        vm.tap(67, 300) // a new g1 (an eighth), not the first one gone on with (a quarter by now)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(69, EIGHTH), TuneNote(67, EIGHTH)), vm.current.tune)
        vm.eraseLast()
        clock.now += 10
        vm.tap(67, 300)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(69, EIGHTH), TuneNote(67, EIGHTH)), vm.current.tune)
        clock.now += 10
        vm.onRestPressed() // the rest is not the note let go of
        assertEquals(4, vm.current.tune.size)
        assertTrue(logger.events("flute.rejoin").isEmpty())
    }

    @Test
    fun `the rest key let go of and pressed again at once goes on with the same rest`() {
        val vm = viewModel()
        vm.rest(300)
        clock.now += 10
        vm.onRestPressed()
        assertTrue(vm.current.restHeld)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneRest(EIGHTH)), vm.current.tune)
        clock.now += 690
        vm.onRestReleased()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        assertNull(logger.events("flute.rejoin").single()["midi"])
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `the rest key pressed again after a pause goes on with its rest from the length it has`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.rest(300) // an eighth rest: 250 ms of the 500 ms beat
        clock.now += 500 // far past HOLD_REJOIN_MS: a pause the child did not ask silence for
        advanceTimeBy(500)
        vm.onRestPressed()
        assertTrue(vm.current.restHeld)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneRest(EIGHTH)), vm.current.tune) // still the one rest
        assertEquals(EIGHTH, vm.current.heldLength)
        advanceTimeBy(126) // 250 ms into the rest, it grows at ¾ of a beat: 125 ms on
        runCurrent()
        assertEquals(QUARTER, vm.current.heldLength)
        clock.now += 300 // 550 ms of rest all told: a quarter
        advanceTimeBy(174)
        vm.onRestReleased()
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        val rejoin = logger.events("flute.rejoin").single()
        assertNull(rejoin["midi"])
        assertEquals(500L, rejoin["gapMs"])
        assertEquals(listOf(300L, 550L), logger.events("flute.rest").map { it["heldMs"] })
    }

    @Test
    fun `a whole rest pressed again stays one whole rest`() {
        val vm = viewModel()
        vm.rest(2000) // four beats: as long as a rest gets
        clock.now += 500
        vm.rest(500)
        assertEquals(listOf(TuneRest(WHOLE)), vm.current.tune) // a second rest takes a note or a breath before it
        assertEquals(1, logger.events("flute.rejoin").size)
        vm.onBreath()
        clock.now += 100
        vm.rest(300)
        assertEquals(listOf(TuneRest(WHOLE), TuneBreath, TuneRest(EIGHTH)), vm.current.tune)
    }

    @Test
    fun `a rest let go of is not gone on with after a note, a breath or an erase`() {
        val vm = viewModel()
        vm.rest(300)
        clock.now += 100
        vm.tap(60, 300)
        clock.now += 100
        vm.rest(300)
        assertEquals(listOf(TuneRest(EIGHTH), TuneNote(60, EIGHTH), TuneRest(EIGHTH)), vm.current.tune)
        clock.now += 100
        vm.onBreath()
        clock.now += 100
        vm.rest(300)
        val withBreath = listOf(TuneRest(EIGHTH), TuneNote(60, EIGHTH), TuneRest(EIGHTH), TuneBreath, TuneRest(EIGHTH))
        assertEquals(withBreath, vm.current.tune)
        vm.eraseLast()
        clock.now += 100
        vm.rest(300)
        assertEquals(withBreath, vm.current.tune)
        assertTrue(logger.events("flute.rejoin").isEmpty())
    }

    @Test
    fun `at the cap a note gone on with is still not added`() {
        settings.tune = List(200) { TuneNote(60, QUARTER) }
        val vm = viewModel()
        vm.tap(67, 300)
        clock.now += 10
        vm.onKeyPressed(67)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.heldMidi)
        assertNull(vm.current.heldIndex)
        clock.now += 690
        vm.onKeyReleased(67)
        assertEquals(200, vm.current.tune.size)
        assertEquals(listOf(false, false), logger.events("flute.note").map { it["added"] })
        assertEquals(1, logger.events("flute.rejoin").size)
    }

    // ---- Your turn: the flute listens ----

    private fun q(midi: Int) = TuneNote(midi, QUARTER)

    private fun pitched(midi: Int) = PitchFrame(freq(midi), 0.97, level = 0.4f)
    private val silentFrame = PitchFrame(null, 0.0, level = 0f)

    private fun TestScope.emit(frame: PitchFrame) {
        engine.frames.tryEmit(frame)
        runCurrent()
    }

    /** One note the child plays: enough agreeing frames to lock, then the gap that ends it. */
    private fun TestScope.play(midi: Int) {
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(midi)) }
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
    }

    /** A view model with [items] as its tune (c1 d1 e1 by default), listening. */
    private fun TestScope.listeningViewModel(vararg items: TuneItem = arrayOf(q(60), q(62), q(64))): FluteViewModel {
        settings.tune = items.toList()
        return viewModel().apply {
            startListening()
            runCurrent()
        }
    }

    @Test
    fun `your turn waits on the first note, skipping a leading rest, and opens the microphone`() = runTest(dispatcher) {
        val vm = listeningViewModel(TuneRest(QUARTER), q(60), q(62))
        assertTrue(vm.current.isListening)
        assertTrue(vm.current.isRunning)
        assertEquals(1, vm.current.listenIndex)
        assertEquals(1, vm.current.litIndex)
        assertTrue(vm.current.marks.isEmpty())
        assertEquals(1, engine.subscriptions)
        assertEquals(2, logger.events("flute.listen").single()["notes"])
        assertEquals(1L, vm.current.beatPulse)
    }

    @Test
    fun `the right note earns a star and the animal moves on, the octave too`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        val pulse = vm.current.beatPulse
        play(72)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), vm.current.marks)
        assertEquals(1, vm.current.listenIndex)
        assertEquals(pulse + 1, vm.current.beatPulse)
        val heard = logger.events("flute.heard").single()
        assertEquals(0, heard["index"])
        assertEquals(60, heard["expected"])
        assertEquals(72, heard["heard"])
        assertEquals("right", heard["verdict"])
    }

    @Test
    fun `a wrong note shows the note heard until the right note, which then earns a faint star`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(65)
        assertEquals(WrongNote(index = 0, midi = 65), vm.current.wrong)
        assertEquals(0, vm.current.listenIndex)
        assertTrue(vm.current.marks.isEmpty())
        assertEquals("wrong", logger.events("flute.heard").single()["verdict"])
        advanceTimeBy(10_000) // no timer takes it away: the child reads it at their own pace
        assertEquals(WrongNote(index = 0, midi = 65), vm.current.wrong)
        play(60)
        assertNull(vm.current.wrong)
        assertEquals(mapOf(0 to StepMark.RETRY), vm.current.marks)
        assertEquals(1, vm.current.listenIndex)
        assertEquals("retry", logger.events("flute.heard").last()["verdict"])
    }

    @Test
    fun `another wrong note replaces the note heard, the previous note heard again leaves it`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        play(65)
        assertEquals(WrongNote(index = 1, midi = 65), vm.current.wrong)
        play(67)
        assertEquals(WrongNote(index = 1, midi = 67), vm.current.wrong)
        play(60) // the step just judged, ignored
        assertEquals("ignored", logger.events("flute.heard").last()["verdict"])
        assertEquals(WrongNote(index = 1, midi = 67), vm.current.wrong)
        play(62)
        assertNull(vm.current.wrong)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY), vm.current.marks)
    }

    @Test
    fun `the previous note heard again is ignored, a written repeat counts only once started again`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60), q(62))
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(60)) }
        assertEquals(1, vm.current.listenIndex)
        // The note just counted pops up an octave without a gap: a step inside one note, not the repeat.
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(72)) }
        assertEquals(1, vm.current.listenIndex)
        assertEquals("ignored", logger.events("flute.heard").last()["verdict"])
        // Let go of and started again: the repeat, played.
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        play(60)
        assertEquals(2, vm.current.listenIndex)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY), vm.current.marks)
    }

    @Test
    fun `a repeat the phone could not hear is forgiven when the next note comes, and said so`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60), q(62))
        play(60)
        play(62)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY, 2 to StepMark.FIRST_TRY), vm.current.marks)
        assertEquals(1, logger.events("flute.repeat_forgiven").single()["index"])
        assertEquals("right", logger.events("flute.heard").last()["verdict"])
    }

    @Test
    fun `the note heard now shows under the waited step and goes with the sound`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        assertNull(vm.current.heardNow)
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(65)) } // a wrong note, still sounding
        assertEquals(65, vm.current.heardNow)
        assertEquals(WrongNote(index = 0, midi = 65), vm.current.wrong)
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertNull("silence: nothing is heard now", vm.current.heardNow)
        assertEquals("the wrong note stays to be read", WrongNote(index = 0, midi = 65), vm.current.wrong)
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(60)) } // the right note, counted, still sounding
        assertEquals(1, vm.current.listenIndex)
        assertNull("the tail of the note just counted is not what the next step hears", vm.current.heardNow)
    }

    @Test
    fun `the note heard now stays shown when it is the written repeat waited on`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60))
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(60)) }
        assertEquals(1, vm.current.listenIndex)
        assertEquals(60, vm.current.heardNow)
    }

    @Test
    fun `holding the note on instead of starting it again asks to stop and play it again`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60))
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(60)) }
        assertEquals(TunerAdvice.NONE, vm.current.advice)
        repeat(FluteViewModel.REPEAT_ADVICE_FRAMES - 1) { emit(pitched(60)) }
        assertEquals(TunerAdvice.NONE, vm.current.advice)
        emit(pitched(60))
        assertEquals(TunerAdvice.PLAY_AGAIN, vm.current.advice)
        assertEquals(1, vm.current.listenIndex)
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertEquals(TunerAdvice.NONE, vm.current.advice)
        play(60) // started again: the repeat
        assertFalse(vm.current.isListening)
    }

    @Test
    fun `the last note ends listening once its sound ends, and keeps the stars`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        play(62)
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(64)) } // the last note, counted, still sounding
        assertEquals(3, vm.current.marks.size)
        assertTrue("the run goes on while the last note sounds", vm.current.isListening)
        assertTrue(vm.current.finishing)
        assertEquals("the animal stays over the last note", 2, vm.current.animalIndex)
        assertNull("but nothing is waited on", vm.current.litIndex)
        val done = logger.events("flute.listen_done").single()
        assertEquals(3, done["stars"])
        assertEquals(0, done["retries"])
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.isRunning)
        assertFalse(vm.current.finishing)
        assertNull(vm.current.listenIndex)
        assertEquals(3, vm.current.marks.size)
        assertTrue(logger.events("flute.listen_stop").isEmpty())
        val after = vm.current
        play(60) // the microphone is closed: nothing changes
        assertEquals(after, vm.current)
    }

    @Test
    fun `the last note ends listening by itself when the child plays on`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60))
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(60)) }
        assertTrue(vm.current.finishing)
        repeat(FluteViewModel.FINISH_FRAMES - 1) { emit(pitched(60)) }
        assertTrue(vm.current.isListening)
        emit(pitched(60))
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.finishing)
    }

    @Test
    fun `stop while the last note sounds ends the run at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60))
        repeat(TunerViewModel.LOCK_FRAMES) { emit(pitched(60)) }
        vm.stopRun()
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.finishing)
        assertEquals("pill", logger.events("flute.listen_stop").single()["reason"])
    }

    @Test
    fun `stop, erase, the speed knob and leaving the tab end listening and say why`() = runTest(dispatcher) {
        fun ended(by: FluteViewModel.() -> Unit): Pair<FluteUiState, Any?> {
            logger.events.clear()
            val vm = listeningViewModel()
            play(60)
            vm.by()
            runCurrent()
            return vm.current to logger.events("flute.listen_stop").single()["reason"]
        }
        val (stopped, pill) = ended { stopRun() }
        assertEquals("pill", pill)
        assertFalse(stopped.isListening)
        assertEquals(Hearing.CLEAR, stopped.hearing)
        assertNull(stopped.wrong)
        assertEquals(1, stopped.marks.size)
        val (erased, erase) = ended { eraseLast() }
        assertEquals("erase", erase)
        assertEquals(2, erased.tune.size)
        assertTrue(erased.marks.isEmpty())
        val (speed, knob) = ended { openSpeed() }
        assertEquals("speed", knob)
        assertFalse(speed.isListening)
        val (left, leave) = ended { leaveTab() }
        assertEquals("leave", leave)
        assertFalse(left.isListening)
        assertEquals(1, left.marks.size)
    }

    @Test
    fun `any write to the tune clears the marks`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        vm.stopRun()
        assertEquals(1, vm.current.marks.size)
        vm.onKeyPressed(67)
        assertTrue(vm.current.marks.isEmpty())
        vm.onKeyReleased(67)
        val finished = listeningViewModel()
        play(60)
        play(62)
        play(64)
        assertEquals(3, finished.current.marks.size)
        finished.eraseLast()
        assertTrue(finished.current.marks.isEmpty())
    }

    @Test
    fun `play is refused while listening and your turn is refused while playing or without a note`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        vm.togglePlayback()
        assertFalse(vm.current.isPlaying)
        assertTrue(vm.current.isListening)
        vm.stopRun()
        val playing = playingViewModel()
        playing.startListening()
        runCurrent()
        assertFalse(playing.current.isListening)
        playing.togglePlayback()
        settings.tune = listOf(TuneRest(QUARTER))
        val subscriptions = engine.subscriptions
        val rests = viewModel()
        rests.startListening()
        runCurrent()
        assertFalse(rests.current.isListening)
        assertEquals(subscriptions, engine.subscriptions)
    }

    @Test
    fun `unclear sound is reported and cleared`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        repeat(HearingTracker.UNCLEAR_FRAMES) { emit(PitchFrame(null, 0.0, level = 0.3f)) }
        assertEquals(Hearing.QUIET_UNCLEAR, vm.current.hearing)
        assertEquals(Hearing.QUIET_UNCLEAR, logger.events("flute.hearing").last()["hearing"])
        repeat(HearingTracker.UNCLEAR_FRAMES) { emit(PitchFrame(null, 0.0, level = 0.6f)) }
        assertEquals(Hearing.LOUD_UNCLEAR, vm.current.hearing)
        vm.stopRun()
        assertEquals(Hearing.CLEAR, vm.current.hearing)
    }

    @Test
    fun `a microphone failure ends listening cleanly`() = runTest(dispatcher) {
        engine.flowOverride = flow { throw IllegalStateException("Microphone is busy") }
        settings.tune = listOf(q(60))
        val vm = viewModel()
        vm.startListening()
        runCurrent()
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.isRunning)
        assertEquals("Microphone is busy", logger.events("flute.listen_error").single()["message"])
    }

    @Test
    fun `a summary line is logged every SUMMARY_FRAMES frames`() = runTest(dispatcher) {
        listeningViewModel()
        repeat(TunerViewModel.SUMMARY_FRAMES) { emit(silentFrame) }
        val summary = logger.events("flute.listen_summary").single()
        assertEquals(TunerViewModel.SUMMARY_FRAMES, summary["silent"])
        assertEquals(60, summary["expected"])
        assertEquals(TunerViewModel.MIN_PROBABILITY, summary["gate"])
    }

    @Test
    fun `the microphone result starts listening or marks the denial`() = runTest(dispatcher) {
        settings.tune = listOf(q(60))
        val vm = viewModel()
        vm.onMicResult(false)
        assertTrue(vm.current.micDenied)
        assertEquals(false, logger.events("flute.mic").single()["granted"])
        vm.dismissMic()
        assertFalse(vm.current.micDenied)
        vm.onMicResult(true)
        runCurrent()
        assertTrue(vm.current.isListening)
        assertFalse(vm.current.micDenied)
    }

    // ---- The songbook: opening the shelf and switching books ----

    @Test
    fun `creation fills the open book and the books of the state from the shelf`() {
        settings.books[2] = Book(listOf(TuneNote(64, HALF)), bpm = 90, timeSignature = TimeSignature.TWO_FOUR)
        settings.openBook = 2
        val vm = viewModel()
        assertEquals(2, vm.current.openBook)
        assertEquals(settings.books.toList(), vm.current.books)
    }

    @Test
    fun `opening another book shows its tune and hands back its speed and beat`() {
        settings.books[1] = Book(listOf(TuneNote(62, HALF)), bpm = 70, timeSignature = TimeSignature.THREE_FOUR)
        val vm = viewModel()
        assertEquals(Pulse(70, TimeSignature.THREE_FOUR), vm.openBook(1))
        assertEquals(listOf(TuneNote(62, HALF)), vm.current.tune)
        assertEquals(1, vm.current.openBook)
        assertEquals(1, settings.openBook)
        assertEquals(TimeSignature.THREE_FOUR, vm.current.timeSignature)
    }

    @Test
    fun `the book left keeps the metronome's speed and beat as they are now`() {
        val vm = viewModel()
        vm.tap(60, 500)
        settings.config = MetronomeConfig(bpm = 150, timeSignature = TimeSignature.TWO_FOUR) // changed on the metronome tab
        vm.openBook(3)
        assertEquals(Book(listOf(TuneNote(60, QUARTER)), 150, TimeSignature.TWO_FOUR), settings.books[0])
        assertEquals(settings.books[0], vm.current.books[0])
    }

    @Test
    fun `opening an empty book leaves the metronome alone and shows an empty staff`() {
        settings.config = MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 500)
        assertNull(vm.openBook(5))
        assertTrue(vm.current.isEmpty)
        assertEquals(5, vm.current.openBook)
        assertEquals(5, settings.openBook)
        assertEquals(TimeSignature.SIX_EIGHT, vm.current.timeSignature)
        assertEquals(MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT), settings.config)
    }

    @Test
    fun `opening a book brings its bpm onto the ring`() {
        settings.books[1] = Book(listOf(TuneNote(60, QUARTER)), bpm = 30)
        assertEquals(40, viewModel().openBook(1)!!.bpm)
        settings.books[2] = Book(listOf(TuneNote(60, QUARTER)), bpm = 260)
        assertEquals(200, viewModel().openBook(2)!!.bpm)
    }

    @Test
    fun `tapping the open book changes nothing and says so`() {
        val vm = viewModel()
        vm.tap(60, 500)
        val before = vm.current
        assertNull(vm.openBook(0))
        assertEquals(before, vm.current)
        assertEquals(listOf(TuneNote(60, QUARTER)), vm.current.tune)
        assertEquals(0, logger.events("flute.shelf_close").single()["book"])
        assertTrue(logger.events("flute.book_open").isEmpty())
    }

    @Test
    fun `an index off the shelf changes nothing`() {
        val vm = viewModel()
        vm.tap(60, 500)
        val before = vm.current
        assertNull(vm.openBook(-1))
        assertNull(vm.openBook(6))
        assertEquals(before, vm.current)
        assertEquals(0, settings.openBook)
        assertTrue(logger.events("flute.book_open").isEmpty())
        assertTrue(logger.events("flute.shelf_close").isEmpty())
    }

    @Test
    fun `a book opened is sanitized like the one opened at creation`() {
        settings.books[1] = Book(listOf(TuneNote(60, HALF), TuneNote(61, QUARTER), TuneNote(84, WHOLE)))
        val vm = viewModel()
        vm.openBook(1)
        assertEquals(listOf(TuneNote(60, HALF), TuneNote(84, WHOLE)), vm.current.tune)
    }

    @Test
    fun `a switch logs where from, where to and what the metronome was set to`() {
        settings.books[1] = Book(
            listOf(TuneNote(62, HALF), TuneRest(QUARTER), TuneNote(64, QUARTER)),
            bpm = 70,
            timeSignature = TimeSignature.THREE_FOUR,
        )
        val vm = viewModel()
        vm.openBook(1)
        val opened = logger.events("flute.book_open").single()
        assertEquals(0, opened["from"])
        assertEquals(1, opened["to"])
        assertEquals(3, opened["notes"])
        assertEquals(70, opened["bpm"])
        assertEquals("3/4", opened["time"])
        vm.openBook(5)
        val empty = logger.events("flute.book_open").last()
        assertEquals(1, empty["from"])
        assertEquals(5, empty["to"])
        assertEquals(0, empty["notes"])
        assertNull(empty["bpm"])
        assertNull(empty["time"])
    }

    @Test
    fun `switching back restores the first tune`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.openBook(1)
        vm.tap(67, 1000)
        vm.openBook(0)
        assertEquals(listOf(TuneNote(60, QUARTER)), vm.current.tune)
        assertEquals(0, vm.current.openBook)
        assertEquals(listOf(TuneNote(67, HALF)), settings.books[1].tune)
        assertEquals(listOf(TuneNote(67, HALF)), vm.current.books[1].tune)
    }

    @Test
    fun `opening the shelf stops playback and listening and says why`() = runTest(dispatcher) {
        val playing = playingViewModel()
        advanceTimeBy(100)
        playing.openShelf()
        assertFalse(playing.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals("book", logger.events("flute.stop").single()["reason"])
        assertEquals(0, logger.events("flute.shelf_open").single()["book"])
        logger.events.clear()
        val listening = listeningViewModel()
        play(60)
        listening.openShelf()
        runCurrent()
        assertFalse(listening.current.isListening)
        assertEquals("book", logger.events("flute.listen_stop").single()["reason"])
        assertEquals(0, logger.events("flute.shelf_open").single()["book"])
    }

    @Test
    fun `opening the shelf ends a held key and keeps its note in the book left`() {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 500
        vm.openShelf()
        assertFalse(vm.current.isHolding)
        assertNull(tonePlayer.sounding)
        vm.openBook(1)
        assertEquals(listOf(TuneNote(60, QUARTER)), settings.books[0].tune)
        assertTrue(vm.current.isEmpty)
    }

    @Test
    fun `a switch clears the marks of the last Your turn`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        vm.stopRun()
        assertEquals(1, vm.current.marks.size)
        vm.openBook(1)
        assertTrue(vm.current.marks.isEmpty())
        assertNull(vm.current.wrong)
        assertNull(vm.current.shown)
    }

    @Test
    fun `a switch while listening ends it with the reason book`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        vm.openBook(1)
        runCurrent()
        assertFalse(vm.current.isListening)
        assertTrue(vm.current.marks.isEmpty())
        assertEquals("book", logger.events("flute.listen_stop").single()["reason"])
    }

    @Test
    fun `wiping a tune to nothing leaves its book empty and open`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.eraseLast()
        assertTrue(settings.books[0].isEmpty)
        assertEquals(0, vm.current.openBook)
        assertTrue(vm.current.isEmpty)
    }
}
