package hr.bertlab.fluteassistant.domain.repository

import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.tuner.TunerMode

/**
 * Small key-value persistence for "where the user left off": the last metronome settings,
 * the volume, the tuner's reference pitch and the note it tunes to, the notes the reading
 * game asks, the flute tab's tune. Synchronous by design
 * (tiny values, read once at ViewModel creation).
 */
interface SettingsStore {
    fun loadMetronomeConfig(): MetronomeConfig
    fun saveMetronomeConfig(config: MetronomeConfig)
    fun loadVolume(): Float
    fun saveVolume(volume: Float)
    fun loadReferenceA4(): Double
    fun saveReferenceA4(hz: Double)
    /** The note the tuner measures against, as a MIDI number (see FluteRange); not clamped here. */
    fun loadTargetNote(): Int
    fun saveTargetNote(midi: Int)
    fun loadTunerMode(): TunerMode
    fun saveTunerMode(mode: TunerMode)
    /** The notes the reading game asks, as MIDI numbers (see StaffNotes); not sanitised here. */
    fun loadPracticeNotes(): Set<Int>
    fun savePracticeNotes(notes: Set<Int>)
    /** Whether the reading game sounds the note when it is found; true by default. */
    fun loadNoteSound(): Boolean
    fun saveNoteSound(enabled: Boolean)
    /** The flute tab's tune, as stored (decoded leniently, see Tune.decode); the caller sanitises. */
    fun loadTune(): List<TuneItem>
    fun saveTune(items: List<TuneItem>)
}
