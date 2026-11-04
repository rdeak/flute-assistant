package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.flute.Songbook
import org.junit.Assert.assertEquals
import org.junit.Test

class SweetTest {

    @Test
    fun `every book on the shelf has its own sweet`() {
        assertEquals(Songbook.SIZE, Sweet.entries.size)
    }

    @Test
    fun `the sweets keep the shelf's order`() {
        assertEquals(listOf("🍭", "🍪", "🧁", "🍩", "🍦", "🍬"), Sweet.entries.map { it.emoji })
    }

    @Test
    fun `the sweet is about a third of its cover's height, never under 40 dp nor over half its width`() {
        // A tall phone's cover (412 dp wide phone): a third of the height, as the mockup draws it.
        assertEquals(68.8f, sweetSize(width = 143.dp, height = 215.dp).value, 0.01f)
        // The shortest cover keeps the sweet it always had.
        assertEquals(40.dp, sweetSize(width = 143.dp, height = 116.dp))
        // A narrow cover bounds it, so it never crowds the spine or the label.
        assertEquals(50.dp, sweetSize(width = 100.dp, height = 300.dp))
    }
}
