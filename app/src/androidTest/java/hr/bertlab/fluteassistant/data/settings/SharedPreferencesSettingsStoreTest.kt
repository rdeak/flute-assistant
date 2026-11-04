package hr.bertlab.fluteassistant.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * The songbook's storage and its one-time migration of the old single `tune` key, on a real
 * SharedPreferences file. The store is built over a [ContextWrapper] that hands out a throwaway
 * file, one per test, so the app's own settings are never touched. A "fresh store" over the same
 * file name is given the framework's cached SharedPreferences instance (the process keeps one per
 * file), so these tests check the key layout and the encoding, not that the bytes reached the disk.
 */
@RunWith(AndroidJUnit4::class)
class SharedPreferencesSettingsStoreTest {

    @get:Rule
    val testName = TestName()

    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fileName: String
    private lateinit var raw: SharedPreferences

    @Before
    fun setUp() {
        fileName = "settings_test_" + testName.methodName
        target.deleteSharedPreferences(fileName)
        raw = target.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    @After
    fun tearDown() {
        target.deleteSharedPreferences(fileName)
    }

    /** A new store over the throwaway file: what the app builds again after a restart. */
    private fun store() = SharedPreferencesSettingsStore(
        object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(fileName, mode)
        },
    )

    /** The old app's single tune, written the way it wrote it, with a stored metronome that is not the default. */
    private fun seedOldApp(tune: String = "60:2,67:4") {
        store().saveMetronomeConfig(MetronomeConfig(bpm = 90, timeSignature = TimeSignature.THREE_FOUR))
        assertTrue(raw.edit().putString("tune", tune).commit())
    }

    @Test
    fun the_old_tune_moves_into_book_0_with_the_stored_speed_and_beat() {
        seedOldApp()

        val shelf = store().loadShelf()

        assertEquals(0, shelf.open)
        assertEquals(Songbook.SIZE, shelf.books.size)
        assertEquals(
            Book(listOf(TuneNote(60, QUARTER), TuneNote(67, HALF)), bpm = 90, timeSignature = TimeSignature.THREE_FOUR),
            shelf.books[0],
        )
        assertTrue(shelf.books.drop(1).all { it.isEmpty })
        assertFalse("the old key is removed", raw.contains("tune"))
        assertTrue("the open book is recorded", raw.contains("open_book"))
        assertEquals("the book's key and encoding", "90;THREE_FOUR;60:2,67:4", raw.getString("book_0", null))
    }

    @Test
    fun the_migration_runs_once() {
        seedOldApp()
        val first = store().loadShelf()

        // The metronome moves on; a second start must not read it into book 0 again.
        store().saveMetronomeConfig(MetronomeConfig(bpm = 60, timeSignature = TimeSignature.SIX_EIGHT))
        val again = store().loadShelf()

        assertEquals(first.open, again.open)
        assertEquals(first.books[0], again.books[0])
        assertEquals(90, again.books[0].bpm)
        assertEquals(TimeSignature.THREE_FOUR, again.books[0].timeSignature)
        assertFalse(raw.contains("tune"))
    }

    @Test
    fun a_shelf_the_child_wiped_never_gets_the_old_tune_back() {
        seedOldApp()
        store().loadShelf() // the migration writes open_book
        val wiping = store()
        repeat(Songbook.SIZE) { wiping.saveBook(it, Book()) }
        // Even if a "tune" key turned up again, the shelf has been stored, so it is ignored.
        assertTrue(raw.edit().putString("tune", "60:2").commit())

        val shelf = store().loadShelf()

        assertEquals(Songbook.SIZE, shelf.books.size)
        assertTrue(shelf.books.all { it.isEmpty })
        assertEquals(0, shelf.open)
    }

    @Test
    fun a_fresh_install_gets_six_empty_books_with_the_first_open() {
        val shelf = store().loadShelf()

        assertEquals(0, shelf.open)
        assertEquals(Songbook.SIZE, shelf.books.size)
        assertTrue(shelf.books.all { it.isEmpty })
        assertTrue("the shelf counts as stored from now on", raw.contains("open_book"))
    }

    @Test
    fun books_and_the_open_book_round_trip_through_a_fresh_store() {
        store().loadShelf()
        val written = store()
        val cookie = Book(
            listOf(TuneNote(62, QUARTER), TuneRest(HALF), TuneBreath, TuneNote(72, WHOLE)),
            bpm = 80,
            timeSignature = TimeSignature.SIX_EIGHT,
        )
        val donut = Book(listOf(TuneNote(65, HALF)), bpm = 140, timeSignature = TimeSignature.TWO_FOUR)
        written.saveBook(1, cookie)
        written.saveBook(3, donut)
        written.saveOpenBook(3)

        val shelf = store().loadShelf()

        assertEquals(3, shelf.open)
        assertEquals(cookie, shelf.books[1])
        assertEquals(donut, shelf.books[3])
        assertTrue(listOf(0, 2, 4, 5).all { shelf.books[it].isEmpty })
    }
}
