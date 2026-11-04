package hr.bertlab.fluteassistant.ui.flute

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
 * The open book in the staff's left corner, under the treble clef and beside the names row: the
 * shelf's little book at about half its size ([CornerBook]: spine, page edges, the [sweet] on the
 * cover, no label, since the tune is on the staff beside it), in [Sun] as the open book is on the
 * shelf, so the child sees on the staff the very book they picked, and the yellow speed knob's
 * twin one corner down. It is [StaffGeometry.CORNER_BUTTON] wide with its page edges and
 * [StaffGeometry.BOOK_BUTTON_HEIGHT] tall, always shown. A tap on the cover is [onClick] and
 * opens the shelf. While the tune is [running] (Play or Your turn) the book stays, faded as one
 * and not pressable, as the knob does. A screen reader hears the sweet's name ("Tune: Cookie,
 * tap to change"), never a number.
 */
@Composable
fun BookButton(
    sweet: Sweet,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.flute_book_button, stringResource(sweet.nameRes))
    // In dp, not sp, as on the shelf: a large font scale cannot push the sweet off the narrow cover.
    val sweetFont = with(LocalDensity.current) { CornerSweetSize.toSp().value.roundToInt() }
    LittleBook(
        look = CornerBook,
        color = Sun,
        onClick = onClick,
        enabled = !running,
        coverModifier = Modifier.semantics { contentDescription = description },
        modifier = modifier.size(StaffGeometry.CORNER_BUTTON.dp, StaffGeometry.BOOK_BUTTON_HEIGHT.dp),
    ) {
        Emoji(sweet.emoji, fontSize = sweetFont, modifier = Modifier.align(Alignment.Center))
    }
}

/** The sweet on the book button: the round button's 26, which fits the room between the spine and the cover's edge. */
private val CornerSweetSize = 26.dp

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

/**
 * The sizes of a little book. The shelf's covers ([ShelfBook]) and the staff's book button
 * ([CornerBook]) are the same book drawn at two sizes by [LittleBook].
 */
private class BookLook(
    val spine: Dp,
    /** How far the page edges stand out past the cover's open edge; they reach [pagesHidden] further in, behind it. */
    val pagesShow: Dp,
    val pagesHidden: Dp,
    /** The page edges stand this much in from the cover's top, and [pagesBottom] from its bottom. */
    val pagesTop: Dp,
    val pagesBottom: Dp,
    spineCorner: Dp,
    edgeCorner: Dp,
    pagesCorner: Dp,
    val depth: Dp,
) {
    /** The cover: square at the spine, round at the edge that opens. */
    val shape = RoundedCornerShape(topStart = spineCorner, bottomStart = spineCorner, topEnd = edgeCorner, bottomEnd = edgeCorner)
    val pagesShape = RoundedCornerShape(topEnd = pagesCorner, bottomEnd = pagesCorner)
}

/** A book on the shelf (`sweets-shelf-v2.html`). */
private val ShelfBook = BookLook(
    spine = 12.dp, pagesShow = 8.dp, pagesHidden = 12.dp, pagesTop = 6.dp, pagesBottom = 3.dp,
    spineCorner = 6.dp, edgeCorner = 18.dp, pagesCorner = 8.dp, depth = 4.dp,
)

/**
 * The book button under the staff's clef, about half a shelf book (`book-button-shape.html`, the
 * user's pick of 2026-11-04), with the speed knob's shadow.
 */
private val CornerBook = BookLook(
    spine = 7.dp, pagesShow = 6.dp, pagesHidden = 8.dp, pagesTop = 4.dp, pagesBottom = 2.dp,
    spineCorner = 4.dp, edgeCorner = 12.dp, pagesCorner = 6.dp, depth = StaffGeometry.CORNER_SHADOW.dp,
)

private val PagesOutline = 2.dp

/** One page's edge and the line after it, across the page edges. */
private val PageEdge = 3.dp
private val PageLine = 1.dp

/**
 * A little book drawn at [look]'s sizes: the page edges (thin lines on paper, the way a closed
 * book's pages show) standing out past the cover's open edge, and over them the cover, a
 * [ChunkyButton] in [color] with a dark spine down its start edge, [content] in the room between
 * the spine and the cover's outline at its other edge. [coverModifier] goes on the cover, the
 * pressable part. A disabled book fades once, page edges and cover together as one layer, so the
 * page edges never show through a faded cover.
 */
@Composable
private fun LittleBook(
    look: BookLook,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = FluteTheme.colors
    Box(modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = look.pagesTop, bottom = look.pagesBottom)
                .width(look.pagesShow + look.pagesHidden)
                .clip(look.pagesShape)
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
                .border(PagesOutline, colors.outline, look.pagesShape),
        )
        ChunkyButton(
            onClick = onClick,
            shape = look.shape,
            color = color,
            depth = look.depth,
            enabled = enabled,
            selected = selected,
            fade = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = look.pagesShow)
                .then(coverModifier),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(look.spine)
                    .background(colors.outline),
            )
            Box(Modifier.fillMaxSize().padding(start = look.spine, end = OutlineWidth), content = content)
        }
    }
}

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
    LittleBook(
        look = ShelfBook,
        color = if (open) Sun else colors.raised,
        onClick = onClick,
        selected = open,
        coverModifier = Modifier.semantics { contentDescription = description },
        modifier = modifier,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // Centred between the spine and the cover's outline at its other edge.
                .padding(horizontal = LabelMargin)
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
