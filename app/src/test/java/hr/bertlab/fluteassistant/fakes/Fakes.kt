package hr.bertlab.fluteassistant.fakes

import hr.bertlab.fluteassistant.audio.flute.FluteTonePlayer
import hr.bertlab.fluteassistant.audio.metronome.BeatEvent
import hr.bertlab.fluteassistant.audio.metronome.MetronomePlayer
import hr.bertlab.fluteassistant.audio.metronome.PlaybackState
import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Shelf
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.Preset
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.repository.PresetRepository
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.playback.BackgroundPlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeMetronomePlayer : MetronomePlayer {
    val events = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 64)
    override val beatEvents: Flow<BeatEvent> = events

    val endedSessions = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    override val sessionEnded: Flow<Long> = endedSessions

    private val _playback = MutableStateFlow<PlaybackState?>(null)
    override val playback: StateFlow<PlaybackState?> = _playback
    var playing = false

    var started: MetronomeConfig? = null
    var startVolume: Float? = null
    val updates = mutableListOf<MetronomeConfig>()
    var volume: Float? = null
    var stopCount = 0
    var refuseStart = false
    var lastSession = 0L

    override fun start(config: MetronomeConfig, volume: Float): Long {
        if (refuseStart) return MetronomePlayer.NO_SESSION
        started = config
        startVolume = volume
        playing = true
        _playback.value = PlaybackState(++lastSession, config, volume)
        return lastSession
    }

    override fun update(config: MetronomeConfig) {
        updates += config
        _playback.update { it?.copy(config = config) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        _playback.update { it?.copy(volume = volume) }
    }

    override fun stop() {
        stopCount++
        if (playing) endedSessions.tryEmit(lastSession)
        playing = false
        _playback.value = null
    }

    /** Simulates the engine stopping by itself (focus loss, headphones unplugged, failure). */
    fun simulateEngineStop() {
        playing = false
        _playback.value = null
        endedSessions.tryEmit(lastSession)
    }
}

class FakeBackgroundPlayback : BackgroundPlayback {
    var startCount = 0
    override fun start() { startCount++ }
}

class FakePresetRepository : PresetRepository {
    val presets = MutableStateFlow<List<Preset>>(emptyList())
    var failWrites = false
    private var nextId = 1L

    override fun observePresets(): Flow<List<Preset>> = presets

    override suspend fun save(name: String, config: MetronomeConfig): Long {
        if (failWrites) throw IllegalStateException("disk full")
        val id = nextId++
        presets.update { listOf(Preset(id, name, config, createdAt = id)) + it }
        return id
    }

    override suspend fun delete(id: Long) {
        if (failWrites) throw IllegalStateException("disk full")
        presets.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeTunerEngine : TunerEngine {
    val frames = MutableSharedFlow<PitchFrame>(extraBufferCapacity = 64)
    var flowOverride: Flow<PitchFrame>? = null
    var subscriptions = 0

    override fun pitchFrames(): Flow<PitchFrame> {
        subscriptions++
        return flowOverride ?: frames
    }
}

class FakeReferenceTonePlayer : ReferenceTonePlayer {
    data class Played(val frequencyHz: Double, val durationMs: Int, val volume: Float)

    val played = mutableListOf<Played>()
    var stopCount = 0

    override fun play(frequencyHz: Double, durationMs: Int, volume: Float) {
        played += Played(frequencyHz, durationMs, volume)
    }

    override fun stop() { stopCount++ }
}

/** Counts every call; [sounding] is the note a real player would be holding right now. */
class FakeFluteTonePlayer : FluteTonePlayer {
    data class NoteOn(val frequencyHz: Double, val volume: Float)

    val notesOn = mutableListOf<NoteOn>()
    var sounding: NoteOn? = null
    var offCount = 0
    var openCount = 0
    var closeCount = 0
    var isOpen = false

    override fun open() {
        openCount++
        isOpen = true
    }

    override fun noteOn(frequencyHz: Double, volume: Float) {
        if (!isOpen) open()
        sounding = NoteOn(frequencyHz, volume).also { notesOn += it }
    }

    override fun noteOff() {
        offCount++
        sounding = null
    }

    override fun close() {
        closeCount++
        isOpen = false
        sounding = null
    }
}

class FakeSettingsStore(
    var config: MetronomeConfig = MetronomeConfig(),
    var volume: Float = 0.8f,
    var referenceA4: Double = NoteMath.DEFAULT_A4,
    var targetNote: Int = FluteRange.DEFAULT_TARGET,
    var tunerMode: TunerMode = TunerMode.IDENTIFY,
    var practiceNotes: Set<Int> = StaffNotes.DEFAULT_PRACTICE,
    var noteSound: Boolean = true,
    val books: MutableList<Book> = MutableList(Songbook.SIZE) { Book() },
    var openBook: Int = 0,
) : SettingsStore {
    /** The tune of the open book, so a test can set or read it without going through the shelf. */
    var tune: List<TuneItem>
        get() = books[openBook].tune
        set(value) { books[openBook] = books[openBook].copy(tune = value) }

    override fun loadMetronomeConfig() = config
    override fun saveMetronomeConfig(config: MetronomeConfig) { this.config = config }
    override fun loadVolume() = volume
    override fun saveVolume(volume: Float) { this.volume = volume }
    override fun loadReferenceA4() = referenceA4
    override fun saveReferenceA4(hz: Double) { referenceA4 = hz }
    override fun loadTargetNote() = targetNote
    override fun saveTargetNote(midi: Int) { targetNote = midi }
    override fun loadTunerMode() = tunerMode
    override fun saveTunerMode(mode: TunerMode) { tunerMode = mode }
    override fun loadPracticeNotes() = practiceNotes
    override fun savePracticeNotes(notes: Set<Int>) { practiceNotes = notes }
    override fun loadNoteSound() = noteSound
    override fun saveNoteSound(enabled: Boolean) { noteSound = enabled }
    override fun loadShelf() = Shelf(openBook, books.toList())
    override fun saveBook(index: Int, book: Book) { books[index] = book }
    override fun saveOpenBook(index: Int) { openBook = index }
}

class FakeClock(var now: Long = 0L) : MonotonicClock {
    override fun nowMillis(): Long = now
}

/** Records every event with its raw field values; verbose events only while [verbose] is on. */
class FakeLogger(var verbose: Boolean = false) : Logger {
    data class Event(val level: Char, val tag: String, val event: String, val fields: Map<String, Any?>, val error: Throwable? = null) {
        operator fun get(key: String): Any? = fields[key]
    }

    val events = mutableListOf<Event>()

    fun events(name: String): List<Event> = events.filter { it.event == name }

    override fun isVerbose(tag: String): Boolean = verbose
    override fun v(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        if (verbose) events += Event('v', tag, event, mapOf(*fields))
    }
    override fun d(tag: String, event: String, vararg fields: Pair<String, Any?>) { events += Event('d', tag, event, mapOf(*fields)) }
    override fun i(tag: String, event: String, vararg fields: Pair<String, Any?>) { events += Event('i', tag, event, mapOf(*fields)) }
    override fun w(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable?) {
        events += Event('w', tag, event, mapOf(*fields), error)
    }
    override fun e(tag: String, event: String, vararg fields: Pair<String, Any?>, error: Throwable?) {
        events += Event('e', tag, event, mapOf(*fields), error)
    }
}
