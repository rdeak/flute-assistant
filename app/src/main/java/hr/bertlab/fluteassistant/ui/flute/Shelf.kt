package hr.bertlab.fluteassistant.ui.flute

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.DISABLED_ALPHA
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.NoteBubble
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.roundToInt

/**
 * The picture and name of each of the shelf's six books, in the shelf's order, so that
 * `Sweet.entries[book]` names a book. A book keeps its sweet for good: the child finds a tune by
 * its sweet, never by a number, and a screen reader says the [nameRes] ("Cookie").
 */
enum class Sweet(val emoji: String, @StringRes val nameRes: Int) {
    LOLLIPOP("🍭", R.string.book_lollipop),
    COOKIE("🍪", R.string.book_cookie),
    CUPCAKE("🧁", R.string.book_cupcake),
    DONUT("🍩", R.string.book_donut),
    ICE_CREAM("🍦", R.string.book_ice_cream),
    CANDY("🍬", R.string.book_candy),
}

/**
 * The round white button in the staff's left corner, under the treble clef and beside the names
 * row: the open book's [sweet], always shown (the child sees which tune is on the staff), the
 * speed knob's twin one corner down. A tap is [onClick] and opens the shelf. While the tune is
 * [running] (Play or Your turn) the button stays, faded and not pressable, as the knob does. A
 * screen reader hears the sweet's name ("Tune: Cookie, tap to change"), never a number.
 */
@Composable
fun BookButton(
    sweet: Sweet,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.flute_book_button, stringResource(sweet.nameRes))
    ChunkyButton(
        onClick = onClick,
        shape = CircleShape,
        color = FluteTheme.colors.raised,
        depth = StaffGeometry.CORNER_SHADOW.dp,
        enabled = !running,
        modifier = modifier
            .size(SpeedKnobSize)
            .semantics { contentDescription = description },
    ) {
        Emoji(sweet.emoji, fontSize = 26)
    }
}

/** The least a row of books may take; on a taller screen the three rows share the height. */
private val BookMinHeight = 116.dp

/** Between two rows of books, and between the two books of a row. */
private val ShelfGap = 16.dp

/**
 * Below this height the shelf cannot hold its three rows of books ([BookMinHeight] each, with the
 * gaps between them, and the 4 dp over and the 16 dp under that [FluteContent]'s column adds), so
 * it scrolls; above it the rows share whatever height there is.
 */
val ShelfMinHeight: Dp = BookMinHeight * 3 + ShelfGap * 2 + 20.dp

/**
 * The child's shelf of tunes, which takes the flute tab's stage, keys and Play row in place while
 * it is open: the [Songbook.SIZE] [books] as little books in two columns and three rows, always in
 * the sweets' order (🍭 🍪 / 🧁 🍩 / 🍦 🍬), each a [Sweet] on its cover over the tune's first
 * notes, the [openBook]'s cover yellow. One tap on a book is [onChoose] with its index, the open
 * book's too (which only closes the shelf). There is no number and no word on it; a screen
 * reader hears each book as "Cookie, 3 notes", "Cupcake, empty" or "Lollipop, open now".
 */
@Composable
fun TuneShelf(
    books: List<Book>,
    openBook: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ShelfGap),
        modifier = modifier.selectableGroup(),
    ) {
        Sweet.entries.chunked(BOOKS_IN_A_ROW).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShelfGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = BookMinHeight),
            ) {
                row.forEach { sweet ->
                    val index = sweet.ordinal
                    BookCover(
                        sweet = sweet,
                        book = books.getOrElse(index) { Book() },
                        open = index == openBook,
                        onClick = { onChoose(index) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private const val BOOKS_IN_A_ROW = 2

/** A book's cover: square at the spine, round at the edge that opens. */
private val BookShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp)
private val BookDepth = 4.dp
private val SpineWidth = 12.dp

/** How far the page edges stand out past the cover's open edge; they reach [PagesHidden] further in, behind it. */
private val PagesShow = 8.dp
private val PagesHidden = 12.dp
private val PagesShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
private val PagesOutline = 2.dp

/** One page's edge and the line after it, across the page edges. */
private val PageEdge = 3.dp
private val PageLine = 1.dp

/** The sweet's share of its cover's height: the mockup's 36 px sweet on a 112 px cover. */
private const val SWEET_SHARE = 0.32f

/** The sweet on the shortest cover ([BookMinHeight]), where a third would be smaller. */
private val SweetMinSize = 40.dp

/** The most of the cover's width (between the spine and the other edge) the sweet may take. */
private const val SWEET_WIDTH_SHARE = 0.5f

/**
 * How big a cover's sweet is drawn, in dp, on a cover whose room between the spine and the other
 * edge is [width] wide and [height] tall: about a third of the height, as the mockup draws it, so
 * it stays large in the middle when the rows grow on a tall phone, at least [SweetMinSize], and
 * never more than half the [width], so it never crowds the spine or the label. A third leaves the
 * label and the gap under the sweet their room at every height from [BookMinHeight] up.
 */
internal fun sweetSize(width: Dp, height: Dp): Dp =
    minOf(maxOf(height * SWEET_SHARE, SweetMinSize), width * SWEET_WIDTH_SHARE)

/** The least room left between the label and the spine, and between the label and the cover's other edge. */
private val LabelMargin = 6.dp

/** A cover note's bubble, smaller on a phone too narrow for five of these on the label. */
private val CoverNoteSize = 22.dp
private val CoverNoteGap = 2.dp

/** A bubble's name, as a share of the bubble. */
private const val COVER_NOTE_FONT = 0.5f
private val LabelPadding = 3.dp
private val LabelOutline = 2.dp
private val LabelShape = RoundedCornerShape(7.dp)

/**
 * One book of the shelf, drawn as a little book (`sweets-shelf-v2.html`): a cover with a dark
 * spine down its start edge and the page edges showing past its other edge, its [sweet] large in
 * the middle ([sweetSize]: about a third of the cover's height) and under it a label with the tune's first notes ([Book.coverNotes]) in their
 * letters' colours. An empty book has its sweet faded and a blank label; the [open] one's cover is
 * [Sun]. The book is one radio button whose description says it all, so the bubbles stay silent.
 */
@Composable
private fun BookCover(
    sweet: Sweet,
    book: Book,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val name = stringResource(sweet.nameRes)
    val description = when {
        open -> stringResource(R.string.flute_book_open, name)
        book.isEmpty -> stringResource(R.string.flute_book_empty, name)
        else -> pluralStringResource(R.plurals.flute_book_notes, book.noteCount, name, book.noteCount)
    }
    Box(modifier) {
        // The page edges, behind the cover: thin lines on paper, the way a closed book's pages show.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 6.dp, bottom = 3.dp)
                .width(PagesShow + PagesHidden)
                .clip(PagesShape)
                .background(colors.raised)
                .drawBehind {
                    val step = PageEdge.toPx()
                    val line = PageLine.toPx()
                    var x = step - line
                    while (x < size.width) {
                        drawRect(colors.border, topLeft = Offset(x, 0f), size = Size(line, size.height))
                        x += step
                    }
                }
                .border(PagesOutline, colors.outline, PagesShape),
        )
        ChunkyButton(
            onClick = onClick,
            shape = BookShape,
            color = if (open) Sun else colors.raised,
            depth = BookDepth,
            selected = open,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = PagesShow)
                .semantics { contentDescription = description },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(SpineWidth)
                    .background(colors.outline),
            )
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    // Centred between the spine and the cover's outline at its other edge.
                    .padding(start = SpineWidth + LabelMargin, end = OutlineWidth + LabelMargin)
                    .clearAndSetSemantics { },
            ) {
                // In dp, not sp: the sweet grows with its cover, and a large font scale cannot push it out.
                val sweetFont = with(LocalDensity.current) { sweetSize(maxWidth, maxHeight).toSp().value.roundToInt() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Emoji(sweet.emoji, fontSize = sweetFont, modifier = if (book.isEmpty) Modifier.alpha(DISABLED_ALPHA) else Modifier)
                    CoverLabel(book.coverNotes)
                }
            }
        }
    }
}

/**
 * The cover's white label: the [notes] as small bubbles, blank for an empty book. It is as wide as
 * [Songbook.COVER_NOTES] bubbles, so every book's label is the same size, an empty one too; where
 * the cover is too narrow for five [CoverNoteSize] bubbles (a 360 dp phone) the bubbles shrink to fit.
 */
@Composable
private fun CoverLabel(notes: List<Int>) {
    val colors = FluteTheme.colors
    BoxWithConstraints {
        val inset = (LabelPadding + LabelOutline) * 2
        val gaps = CoverNoteGap * (Songbook.COVER_NOTES - 1)
        val bubble = minOf(CoverNoteSize, (maxWidth - inset - gaps) / Songbook.COVER_NOTES)
        // In dp, not sp: a name must stay inside its little bubble whatever the font scale.
        val fontSize = with(LocalDensity.current) { (bubble * COVER_NOTE_FONT).toSp() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(CoverNoteGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .size(bubble * Songbook.COVER_NOTES + gaps + inset, bubble + inset)
                .clip(LabelShape)
                .background(colors.card)
                .border(LabelOutline, colors.outline, LabelShape)
                .padding(LabelPadding + LabelOutline),
        ) {
            notes.forEach { midi -> NoteBubble(midi = midi, size = bubble, fontSize = fontSize, depth = 0.dp) }
        }
    }
}
