// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.fadedUnless
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A key's height: a sure target for a small finger, and the canvas's 58 at every screen height
 * (the user, 2026-11-02: keys as on the canvas; they used to stretch to 72 dp to fill the space).
 */
internal val KeyHeight = 58.dp
internal val KeyGap = 6.dp
private val KeyShape = RoundedCornerShape(21.dp)
private val KeyDepth = KeysGeometry.DEPTH.dp

/** The keys' least room: the top row and the C4 octave's two rows, whole ([KeysGeometry.MIN_HEIGHT]). */
val FluteKeysMinHeight: Dp = KeysGeometry.MIN_HEIGHT.dp

/** The scrolling rows' test tag: their bounds are the block's, its viewport. */
internal const val KEY_ROWS_TAG = "flute_key_rows"

/**
 * A finger on a key in the scrolling rows that starts them scrolling within this long of its press was a scroll, and its
 * press is taken back; held longer, the key is a note and the rows stand still under it (2026-11-16, the user's rule A; set
 * from a phone test with a child, by `flute.press_scrolled`'s ms, dy and canMove).
 */
internal const val SCROLL_DECIDE_MS = 150L

/** The note keys a row could hold: an octave's seven white keys. */
private const val COLUMNS = 7

/** The breath, rest and erase emoji, in dp so a large font scale cannot push the breath emoji out of a 40 dp key. */
private const val SIGN_EMOJI_DP = 24f

/** The plain keys at the end of the top row: join, rest, breath, erase. */
private const val SignKeyCount = 4

/** The ⌒ key's picture: the staff's own slur ([slurPath]), from (2, 3) to (28, 3) dp in a box this size. */
private val JoinPictureWidth = 30.dp
private val JoinPictureHeight = 16.dp
private val JoinPicture by lazy { slurPath(StaffGeometry.slurCurve(1f, 3f - StaffGeometry.SLUR_END_DROP, 29f, 3f - StaffGeometry.SLUR_END_DROP, emptyList())) }

private const val SPONGE_EMOJI = "\uD83E\uDDFD" // sponge

/** After this long held, the erase key starts repeating, then every [EraseRepeatMs]: a phone keyboard's backspace. */
private const val EraseRepeatDelayMs = 400L
private const val EraseRepeatMs = 120L

/**
 * Where the scrolling key rows stand (since 2026-11-16, sharps and flats), kept by [FluteContent] beside the staff's
 * place, so it lasts through a turn of the phone and a run (the keys leave the screen while a tune runs and come back
 * where they were). The rows open at their bottom, the C4 octave, where a beginner plays most, and rows a finger left
 * at their bottom stay at their bottom when their height changes, as on a turn from a phone on its side to upright.
 */
@Stable
class KeysPlace internal constructor(internal val scroll: ScrollState, bottom: MutableState<Boolean>) {
    /** The rows stood at their bottom when a scroll last ended; saved with the scroll, so a restore keeps both. */
    internal var atBottom by bottom
}

/** A [KeysPlace] at the rows' bottom. */
@Composable
fun rememberKeysPlace(): KeysPlace {
    val scroll = rememberScrollState(Int.MAX_VALUE) // past the end: the first layout brings it to the bottom
    val atBottom = rememberSaveable { mutableStateOf(true) }
    return remember(scroll, atBottom) { KeysPlace(scroll, atBottom) }
}

/**
 * The flute's 25 keys, C4 to C6 with every sharp and flat (since 2026-11-16; spec `2026-11-16-flute-more-notes-design.md`,
 * the user's layout A). A top row that never scrolls: C6 and, apart from it with two empty columns between, four plain
 * keys. Under it, [KeysGeometry.TOP_GAP] lower, four rows that scroll as one in [place], highest on top as on the staff:
 * the C5 octave's black keys, its white keys, the C4 octave's black keys, its white keys. A black key is as wide as a
 * white key and centred on the gap between its two white keys, as on a piano (none between E and F, or B and C), and
 * shorter ([KeysGeometry.black]), its name shrinking to fit. Every key is in its letter's colour with its name; the key of
 * [litMidi] sinks onto its shadow inside a yellow ring (a scrolling row's ring is drawn over the rows, whose scroll would
 * cut it at their edge). A finger down calls [FluteActions.onKeyPressed] at once and the finger lifting, or sliding off,
 * calls [FluteActions.onKeyReleased]; sliding onto another key does not press it, so every note in the tune is a
 * deliberate tap. A screen reader activates a key through [FluteActions.onKeyActivated].
 *
 * The sizes ([KeysGeometry]): a white key is never under 58 dp nor a black one under 48, and spare height shows more rows
 * before it grows keys, up to [maxKeyHeight]; height still spare stands above and below the keys, which keep together.
 * While the rows scroll and are not at their top, their upper edge fades into the page, and a thumb in the border colour
 * beside them shows which part is in view. A key's width comes from the width given, less [keyGap] between each two, in
 * whole pixels: seven keys that each rounded up ran 4 px over the row on a 420 dpi phone, so every row is measured on one
 * grid and centred, and the tools and the black keys stand exactly over the columns below.
 *
 * The four plain keys at the end of the top row, each one note key wide and over F5 to B5's columns, so a tool is never
 * bigger than a note and a slip off C6 lands on nothing (since 2026-11-05): the join key (⌒, since 2026-11-16, at the
 * user's request, the picture the slur it draws), which calls [FluteActions.onJoin] as the finger goes down, stays down
 * while [joinWaiting] and is faded and inert unless [joinEnabled]; the rest key (💤), held like a note key
 * ([FluteActions.onRestPressed], [FluteActions.onRestReleased], [FluteActions.onRestActivated]) and sunk while [restLit];
 * the breath key (😮‍💨), which calls [FluteActions.onBreath] as the finger goes down; and the erase key (🧽), which calls
 * [FluteActions.onEraseLast] as the finger goes down and again and again while it stays, like a keyboard's backspace,
 * faded and inert while [eraseEnabled] is false.
 */
@Composable
fun FluteKeys(
    litMidi: Int?,
    restLit: Boolean,
    eraseEnabled: Boolean,
    actions: FluteActions,
    place: KeysPlace,
    modifier: Modifier = Modifier,
    maxKeyHeight: Dp = KeyHeight,
    keyGap: Dp = KeyGap,
    joinWaiting: Boolean = false,
    joinEnabled: Boolean = false,
) {
    val groupLabel = stringResource(R.string.flute_keys)
    KeepAtBottom(place)
    val touch = remember(place) { KeysTouch(place.scroll) }
    BoxWithConstraints(modifier.semantics { contentDescription = groupLabel }) {
        val density = LocalDensity.current
        val white = KeysGeometry.whiteHeight(maxHeight.value, maxKeyHeight.value)
        val topGap = with(density) { KeysGeometry.TOP_GAP.dp.roundToPx() }
        // Remembered: the grid changes only with the room, the gap and the key's size, not with every key pressed.
        val grid = remember(constraints.maxWidth, constraints.maxHeight, keyGap, white, density) {
            fun gridOf(whitePx: Int) = with(density) {
                KeysGrid(
                    width = constraints.maxWidth,
                    gap = keyGap.roundToPx(),
                    white = whitePx,
                    black = (whitePx * KeysGeometry.BLACK / KeysGeometry.WHITE).roundToInt(),
                    depth = KeyDepth.roundToPx(),
                    rowGap = KeysGeometry.ROW_GAP.dp.roundToPx(),
                )
            }
            var grid = gridOf(with(density) { white.dp.roundToPx() })
            // Grown keys fill the room exactly in dp, but each part rounds to whole pixels on its own, and a pixel or two
            // over would make five rows that fit scroll: the keys give up those pixels instead.
            if (!KeysGeometry.scrolls(maxHeight.value) && constraints.hasBoundedHeight) {
                while (grid.white > 1 && grid.white + grid.depth + topGap + grid.rowsHeight > constraints.maxHeight) grid = gridOf(grid.white - 1)
            }
            grid
        }
        val room = if (constraints.hasBoundedHeight) constraints.maxHeight - grid.white - grid.depth - topGap else grid.rowsHeight
        val block = minOf(room, grid.rowsHeight).coerceAtLeast(0)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            KeysRow(grid, listOf(grid.column(0)) + (COLUMNS - SignKeyCount until COLUMNS).map(grid::column), grid.white) {
                FluteKey(midi = FluteNotes.HIGHEST, lit = litMidi == FluteNotes.HIGHEST, actions = actions, heldBy = touch)
                JoinKey(waiting = joinWaiting, enabled = joinEnabled, onJoin = actions.onJoin)
                RestKey(lit = restLit, onPress = actions.onRestPressed, onRelease = actions.onRestReleased, onActivate = actions.onRestActivated, heldBy = touch)
                BreathKey(onBreath = actions.onBreath)
                EraseKey(enabled = eraseEnabled, onErase = actions.onEraseLast)
            }
            Spacer(Modifier.height(KeysGeometry.TOP_GAP.dp))
            ScrollingRows(grid, block, litMidi, actions, place.scroll, touch)
        }
    }
}

/**
 * The keys' grid in whole pixels, shared by every row: the [COLUMNS] white keys' places, the black keys between them,
 * and the scrolling rows' heights and tops. [width] is the room, [gap] between two keys; [white], [black] a key's
 * height, [depth] its shadow; [rowGap] between two scrolling rows.
 */
private class KeysGrid(width: Int, val gap: Int, val white: Int, val black: Int, val depth: Int, rowGap: Int) {
    val keyWidth = ((width - gap * (COLUMNS - 1)) / COLUMNS).coerceAtLeast(0)
    private val start = (width - (COLUMNS * keyWidth + (COLUMNS - 1) * gap)) / 2

    /** Where the rows end on the right: the thumb stands past it. */
    val right = start + COLUMNS * keyWidth + (COLUMNS - 1) * gap

    fun column(index: Int): Int = start + index * (keyWidth + gap)

    fun blackLeft(after: Int): Int = start + KeysGeometry.blackLeft(after, keyWidth.toFloat(), gap.toFloat()).roundToInt()

    /** The scrolling rows top to bottom, each its keys' octave (its C) and whether they are black. */
    val rows = listOf(72 to true, 72 to false, FluteNotes.LOWEST to true, FluteNotes.LOWEST to false)

    /** A row's keys' height, its shadow under them, and where it starts in the scrolling rows. */
    fun keyHeight(row: Int): Int = if (rows[row].second) black else white
    val rowTops: List<Int> = rows.indices.runningFold(0) { y, row -> y + keyHeight(row) + depth + rowGap }.dropLast(1)
    val rowsHeight: Int = rowTops.last() + keyHeight(rows.lastIndex) + depth

    /** The row and the left of the key of [midi] in the scrolling rows, or null for a key not in them. */
    fun place(midi: Int): Pair<Int, Int>? {
        for ((row, octave) in rows.withIndex()) {
            val (c, black) = octave
            if (black) {
                FluteNotes.blacksOf(c).firstOrNull { it.first == midi }?.let { return row to blackLeft(it.second) }
            } else {
                FluteNotes.whitesOf(c).indexOf(midi).takeIf { it >= 0 }?.let { return row to column(it) }
            }
        }
        return null
    }
}

/** One row of keys [keyHeight] px tall at [lefts], [KeysGrid.keyWidth] wide each, with their shadow's room under them. */
@Composable
private fun KeysRow(grid: KeysGrid, lefts: List<Int>, keyHeight: Int, keys: @Composable () -> Unit) {
    Layout(content = keys, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints.fixed(grid.keyWidth, keyHeight)) }
        layout(constraints.maxWidth, keyHeight + grid.depth) {
            placeables.forEachIndexed { index, key -> key.place(lefts[index], 0) }
        }
    }
}

/**
 * The four rows that scroll, in a viewport [height] px tall ([KEY_ROWS_TAG]). The lit key's ring is drawn here, over the
 * rows and outside their viewport's clip, as far as a whole key's ring reaches past it; then, while the rows scroll and
 * are not at their top, the fade at their top; and, while they scroll, the thumb right of them.
 */
@Composable
private fun ScrollingRows(grid: KeysGrid, height: Int, litMidi: Int?, actions: FluteActions, scroll: ScrollState, touch: KeysTouch) {
    val colors = FluteTheme.colors
    val heightDp = with(LocalDensity.current) { height.toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .testTag(KEY_ROWS_TAG)
            .drawWithContent {
                drawContent()
                val max = grid.rowsHeight - height
                val value = scroll.value.coerceIn(0, maxOf(max, 0))
                litMidi?.let(grid::place)?.let { (row, left) ->
                    val top = (grid.rowTops[row] - value).toFloat()
                    // Scrolled, the rows' top edge cuts the ring as it cuts the key (the final review): no ring above it.
                    val ringTop = if (value > 0) 0f else -RingAbove.toPx()
                    clipRect(left = -RingReach.toPx(), top = ringTop, right = size.width + RingReach.toPx(), bottom = size.height + RingBelow.toPx()) {
                        translate(left.toFloat(), top) { drawKeyRing(Size(grid.keyWidth.toFloat(), grid.keyHeight(row).toFloat())) }
                    }
                }
                if (max <= 0) return@drawWithContent
                if (value > 0) {
                    val fade = KeysGeometry.FADE.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(0f to colors.page, 1f to colors.page.copy(alpha = 0f), startY = 0f, endY = fade),
                        topLeft = Offset(-RingReach.toPx(), 0f),
                        size = Size(size.width + 2 * RingReach.toPx(), fade),
                    )
                }
                val thumb = size.height * size.height / grid.rowsHeight
                val barWidth = KeysGeometry.BAR_WIDTH.dp.toPx()
                drawRoundRect(
                    colors.border,
                    topLeft = Offset(grid.right + BarGap.toPx(), (size.height - thumb) * value / max),
                    size = Size(barWidth, thumb),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(KeysGeometry.ROW_GAP.dp),
            modifier = Modifier
                .fillMaxWidth()
                // Rows that fit do not scroll, so a drag on a key there is a press as on the top row; and while a key
                // holds, the rows stand still under it. No stretch at their ends: while one relaxes the rows take every
                // touch, and a key tapped right after a flick to the C5 octave made no sound (the final review).
                .verticalScroll(scroll, overscrollEffect = null, enabled = grid.rowsHeight > height && touch.holding == 0),
        ) {
            for ((row, octave) in grid.rows.withIndex()) {
                val (c, black) = octave
                if (black) {
                    val keys = FluteNotes.blacksOf(c)
                    KeysRow(grid, keys.map { grid.blackLeft(it.second) }, grid.keyHeight(row)) {
                        for ((midi, _) in keys) FluteKey(midi, lit = midi == litMidi, actions = actions, ring = false, fitName = true, touch = touch)
                    }
                } else {
                    val keys = FluteNotes.whitesOf(c)
                    KeysRow(grid, keys.indices.map(grid::column), grid.keyHeight(row)) {
                        for (midi in keys) FluteKey(midi, lit = midi == litMidi, actions = actions, ring = false, touch = touch)
                    }
                }
            }
        }
    }
}

/**
 * The fingers on the keys: how many keys are down, and how many of them hold the scrolling rows still; and the rows'
 * [scroll], which says whether a drag could move them.
 */
@Stable
private class KeysTouch(val scroll: ScrollState) {
    var down by mutableIntStateOf(0)
    var holding by mutableIntStateOf(0)

    /** A key outside the rows (C6, 💤) held: it is down and holds the rows still from the first moment. */
    fun holdOutside() {
        down++
        holding++
    }

    fun letGoOutside() {
        down--
        holding--
    }
}

/** Rows a scroll left at their bottom go back to it when their height changes and the bottom moves (see [KeysPlace]). */
@Composable
private fun KeepAtBottom(place: KeysPlace) {
    LaunchedEffect(place) {
        val scroll = place.scroll
        launch {
            snapshotFlow { scroll.isScrollInProgress }.drop(1).collect { moving ->
                if (!moving) place.atBottom = scroll.value >= scroll.maxValue
            }
        }
        snapshotFlow { scroll.maxValue }.collect { max ->
            if (place.atBottom && max != Int.MAX_VALUE && scroll.value < max) scroll.scrollTo(max)
        }
    }
}

/** How far a lit key's ring reaches past the key: to its sides, above it and below its row's shadow ([drawKeyRing]). */
private val RingReach = 7.dp
private val RingAbove = 4.dp
private val RingBelow = 5.dp

/** Between the rows' right end and their thumb. */
private val BarGap = 2.dp

/** The rest key: a plain key with 💤, held like a note key, the rest growing while it is down. */
@Composable
private fun RestKey(
    lit: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    heldBy: KeysTouch? = null,
) {
    val label = stringResource(R.string.flute_rest)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    SignKey(
        emoji = SLEEP_EMOJI,
        label = label,
        pressed = lit,
        onActivate = onActivate,
        modifier = modifier.pointerInput(heldBy) {
            detectTapGestures(
                onPress = {
                    heldBy?.holdOutside() // a rest held keeps the rows still, as a note key does
                    currentOnPress()
                    try {
                        tryAwaitRelease() // up, or the finger slid off: both let go of the key
                    } finally {
                        heldBy?.letGoOutside()
                        currentOnRelease() // as does the key leaving the screen (see FluteKey)
                    }
                },
            )
        },
    )
}

/**
 * The join key (since 2026-11-16): a plain key with the slur it draws, the staff's own crescent. A finger going down
 * joins the next note to the last one, or cancels a join waiting ([FluteActions.onJoin]); while one waits the key stays
 * down in its yellow ring and a screen reader hears it selected. Faded and inert with nothing to join from.
 */
@Composable
private fun JoinKey(waiting: Boolean, enabled: Boolean, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.flute_join)
    val currentOnJoin by rememberUpdatedState(onJoin)
    val ink = FluteTheme.colors.text
    val active = enabled || waiting
    SignKey(
        label = label,
        pressed = waiting,
        onActivate = { currentOnJoin() },
        enabled = active,
        // Selected while a join waits; nothing said otherwise, so a screen reader does not hear "not selected" on every visit.
        selected = if (waiting) true else null,
        picture = {
            Canvas(Modifier.size(JoinPictureWidth, JoinPictureHeight)) {
                scale(density, pivot = Offset.Zero) {
                    drawPath(JoinPicture, ink)
                    drawPath(JoinPicture, ink, style = Stroke(1.5f, join = StrokeJoin.Round))
                }
            }
        },
        modifier = modifier.then(
            if (active) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { currentOnJoin() }) } else Modifier,
        ),
    )
}

/** The breath key: a plain key with 😮‍💨 that marks a breath as the finger goes down and sinks while it stays. */
@Composable
private fun BreathKey(onBreath: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.flute_breath)
    val currentOnBreath by rememberUpdatedState(onBreath)
    var pressed by remember { mutableStateOf(false) }
    SignKey(
        emoji = BREATH_EMOJI,
        label = label,
        pressed = pressed,
        onActivate = onBreath,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    currentOnBreath()
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                    }
                },
            )
        },
    )
}

/**
 * The erase key: a plain key with a sponge that erases the last step as the finger goes down and
 * keeps erasing while it stays, so a long line is cleared by holding; it sinks while held, like the
 * breath key. Faded and inert while [enabled] is false; the gesture is attached only while it is
 * enabled, so a hold that empties the tune stops repeating and lets the key up at once.
 */
@Composable
private fun EraseKey(enabled: Boolean, onErase: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.flute_erase)
    val scope = rememberCoroutineScope()
    val currentOnErase by rememberUpdatedState(onErase)
    var pressed by remember { mutableStateOf(false) }
    SignKey(
        emoji = SPONGE_EMOJI,
        label = label,
        pressed = pressed,
        onActivate = { currentOnErase() },
        enabled = enabled,
        modifier = modifier.then(
            if (enabled) {
                Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            currentOnErase()
                            val repeat = scope.launch {
                                delay(EraseRepeatDelayMs)
                                while (isActive) {
                                    currentOnErase()
                                    delay(EraseRepeatMs)
                                }
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                                repeat.cancel()
                            }
                        },
                    )
                }
            } else {
                Modifier
            },
        ),
    )
}

/**
 * A plain (not a note's colour) key showing [emoji], named [label] for a screen reader; sunk in a
 * yellow ring while [pressed]; faded, and refusing [onActivate], while [enabled] is false.
 */
@Composable
private fun SignKey(
    label: String,
    pressed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    enabled: Boolean = true,
    selected: Boolean? = null,
    picture: (@Composable () -> Unit)? = null,
) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fadedUnless(enabled, KeyDepth)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
                if (selected != null) this.selected = selected
                onClick {
                    if (enabled) onActivate()
                    enabled
                }
            }
            .drawBehind { if (pressed) drawKeyRing() }
            .chunky(KeyShape, colors.raised, colors.outline, KeyDepth, pressed = pressed),
    ) {
        if (picture != null) picture() else Emoji(checkNotNull(emoji), fontSize = emojiSizeFor(SIGN_EMOJI_DP))
    }
}

/** The yellow ring around a pressed key of [key]'s size, a little outside its outline. */
private fun DrawScope.drawKeyRing(key: Size = size) {
    val gap = 2.dp.toPx() + 2.5.dp.toPx()
    drawRoundRect(
        color = Sun,
        topLeft = Offset(-gap, -gap + 3.dp.toPx()),
        size = Size(key.width + gap * 2, key.height + gap * 2),
        cornerRadius = CornerRadius(21.dp.toPx() + gap),
        style = Stroke(5.dp.toPx()),
    )
}

/**
 * The key of [midi], sunk while [lit], in a yellow ring unless [ring] is false (a scrolling row's ring is drawn over the
 * rows, [ScrollingRows]); [fitName] shrinks its name to fit on one line, as a black key's must. A key in the scrolling
 * rows is given their [touch] and their gesture ([rowKeyGesture]); the top row's C6 keeps the plain press.
 */
@Composable
private fun FluteKey(
    midi: Int,
    lit: Boolean,
    actions: FluteActions,
    modifier: Modifier = Modifier,
    ring: Boolean = true,
    fitName: Boolean = false,
    touch: KeysTouch? = null,
    heldBy: KeysTouch? = null,
) {
    val label = noteLabel(midi)
    val colors = FluteTheme.colors
    val onActivate = actions.onKeyActivated
    // The gesture block is keyed on the note alone, so it reads the latest callbacks through state.
    val currentOnPress by rememberUpdatedState(actions.onKeyPressed)
    val currentOnRelease by rememberUpdatedState(actions.onKeyReleased)
    val currentOnScrolled by rememberUpdatedState(actions.onKeyScrolled)
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick {
                    onActivate(midi)
                    true
                }
            }
            .drawBehind { if (lit && ring) drawKeyRing() }
            .chunky(KeyShape, noteColor(midi), colors.outline, KeyDepth, pressed = lit)
            .pointerInput(midi, touch, heldBy) {
                if (touch != null) {
                    rowKeyGesture(
                        touch = touch,
                        onPress = { currentOnPress(midi) },
                        onRelease = { currentOnRelease(midi) },
                        onScrolled = { ms, dy, canMove -> currentOnScrolled(midi, ms, dy, canMove) },
                    )
                    return@pointerInput
                }
                detectTapGestures(
                    onPress = {
                        heldBy?.holdOutside() // C6 held keeps the rows still, as a key in them does
                        currentOnPress(midi)
                        try {
                            tryAwaitRelease() // up, or the finger slid off: both let go of the key
                        } finally {
                            // Turning the phone takes the key off the screen and cancels the gesture
                            // here, with no up to come; the Activity lives on, so nothing else would
                            // end the note. The ViewModel ignores a key that is no longer the one held.
                            heldBy?.letGoOutside()
                            currentOnRelease(midi)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (fitName) {
            Text(
                noteText(midi),
                style = MaterialTheme.typography.titleLarge,
                color = InkOnFill,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 22.sp),
                modifier = Modifier.padding(horizontal = OutlineWidth),
            )
        } else {
            Text(noteText(midi), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = InkOnFill, maxLines = 1)
        }
    }
}

/**
 * A key in the scrolling rows (since 2026-11-16, the user's rule A): the finger going down calls [onPress] at once, as on
 * any key. If the rows take the finger for a scroll before [SCROLL_DECIDE_MS] have passed, the press was a scroll:
 * [onScrolled] with the ms since the press and how far, in dp, the finger had moved, and nothing more. At
 * [SCROLL_DECIDE_MS] with the finger still down, or at once for a finger landing while another key is down (the newest
 * press ended that key's hold, so this one can never be taken back), the key holds: the rows stand still ([touch]) until
 * every key is up. The finger lifting, sliding off the key, or the key leaving the screen calls [onRelease].
 */
private suspend fun PointerInputScope.rowKeyGesture(
    touch: KeysTouch,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onScrolled: (ms: Long, dy: Float, canMove: Boolean) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown()
    down.consume()
    // Where the rows stand as the finger lands: at an end, a drag that way cannot move them (`canMove`).
    val atTop = touch.scroll.value <= 0
    val atBottom = touch.scroll.value >= touch.scroll.maxValue
    var holds = false
    fun hold() {
        if (!holds) {
            holds = true
            touch.holding++
        }
    }
    if (touch.down > 0) hold()
    touch.down++
    onPress()
    var letGo = true
    try {
        var waited = 0L
        while (true) {
            val event = if (holds) {
                awaitPointerEvent(PointerEventPass.Main)
            } else {
                // Read after the rows have had their say: a change they consumed is the rows scrolling under the finger.
                withTimeoutOrNull((SCROLL_DECIDE_MS - waited).coerceAtLeast(1)) { awaitPointerEvent(PointerEventPass.Final) }
            }
            if (event == null) {
                hold()
                continue
            }
            // The press's own down comes round again on the Final pass, consumed by this key: it is not the rows.
            val change = event.changes.firstOrNull { it.id == down.id && !it.changedToDownIgnoreConsumed() } ?: continue
            waited = change.uptimeMillis - down.uptimeMillis
            if (!change.pressed) break
            if (!holds && change.isConsumed && change.positionChangeIgnoreConsumed() != Offset.Zero) {
                letGo = false
                val dy = (change.position.y - down.position.y).toDp().value
                onScrolled(waited, dy, if (dy > 0) !atTop else !atBottom) // a finger going down shows the rows above
                break
            }
            if (change.isOutOfBounds(size, extendedTouchPadding)) break
            if (holds) change.consume() // read before the rows: they never see a held key's finger move
            if (!holds && waited >= SCROLL_DECIDE_MS) hold()
        }
    } finally {
        touch.down--
        if (holds) touch.holding--
        if (letGo) onRelease() // as the plain key's: a turn of the phone cancels the gesture with no up to come
    }
}
