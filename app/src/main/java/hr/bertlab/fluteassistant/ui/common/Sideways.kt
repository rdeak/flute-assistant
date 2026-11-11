// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.floor

/**
 * Whether the screen is drawn for a phone on its side (spec `2026-11-05-landscape-design.md`).
 * `MainScreen` provides it from the window, not the device, so a split screen keeps the upright
 * layouts. Static, as it changes only when the window does and then the whole screen is laid out
 * again anyway.
 */
val LocalSideways = staticCompositionLocalOf { false }

/**
 * The sideways layout's numbers, in dp as plain floats like the flute's `StaffGeometry`, so a unit test can
 * check the sizes the three phones it was drawn for (915 x 411, 800 x 360, 640 x 360) get. The
 * frame, the card, the keys, the staff, the ring and the tuner's window are read from here; a few
 * sizes stay with the screen that draws them (the metronome's panel and well, the picker's keys),
 * and `bigBeatUnit` is what the tests hold `BeatBubbles` to. "Content" is the window less the
 * rail; the card is the stage's white rounded box, whose inside is [CARD_CHROME] smaller than it.
 */
internal object SidewaysGeometry {
    /** The air around the card and the right column, and between them. */
    const val MARGIN = 8f

    /** What the card's padding (16 each side) and border (3 each side) take from its inside. */
    const val CARD_CHROME = 38f

    /** The window height under which the rail and the column are narrower to leave the card its room. */
    private const val SHORT = 400f

    /** The window must be this wide to be sideways: under it the upright layouts already scroll. */
    private const val MIN_WIDTH = 600f

    /** The width of content under which the keys narrow, as upright does on a phone that narrow. */
    private const val NARROW_CONTENT = 630f

    private const val RAIL = 92f
    private const val RAIL_SHORT = 84f
    private const val COLUMN = 116f
    private const val COLUMN_SHORT = 100f

    /** Seven keys a row: 48 wide with 6 gaps (7 * 48 + 6 * 6), or 40 wide with 4 gaps below [NARROW_CONTENT]. */
    private const val KEYS = 372f
    private const val KEYS_NARROW = 304f
    private const val KEY_GAP = 6f
    private const val KEY_GAP_NARROW = 4f

    /** The button row under the keys: Play and Listen to me are 84 tall, and their 8 dp shadow ends level with the page's margin. */
    private const val KEYS_BUTTONS = 84f + 8f

    /** The room between the keys and the button row under them: the page's margin, as `FluteWritingSideways` spaces them. */
    private const val KEYS_TO_BUTTONS = MARGIN

    /** The keys' three rows have two gaps between them, of upright's `KeyRowGap` (12). */
    private const val KEY_ROW_GAPS = 12f * 2

    /**
     * What the keys' column spends besides the three rows' keys: the two margins, the button row
     * under the keys, its gap and the gaps between the rows.
     */
    private const val KEYS_SPENT = MARGIN * 2 + KEYS_BUTTONS + KEYS_TO_BUTTONS + KEY_ROW_GAPS

    /** Every key row has its own 5 dp shadow, which the mockup counted only once. */
    private const val KEY_SHADOW = 5f
    private const val KEY_HEIGHT_MIN = 58f
    private const val KEY_HEIGHT_MAX = 88f

    /**
     * The window height under which writing cannot give its keys their least (three rows of
     * [KEY_HEIGHT_MIN] and their shadows) and still have the buttons under them: 329. Under it the
     * keys' column scrolls, as upright's tab does; the phones the layout was drawn for are all taller.
     */
    const val WRITING_MIN_HEIGHT = KEYS_SPENT + 3 * (KEY_HEIGHT_MIN + KEY_SHADOW)

    private const val RING_MAX = 304f

    /** The first bubble is a little bigger than the rest, as upright (`BeatBubbles`' `unit * 1.16`). */
    private const val FIRST_BEAT_EXTRA = 0.16f

    /** The most a bubble is while the metronome plays; the takeover's cap (`MetronomeSideways`) is this. */
    const val BIG_BEAT_MAX = 150f

    /** The room between the bubbles while the metronome plays, up to [FEW_BEATS] beats and with more. */
    const val BIG_BEAT_GAP_FEW = 16f
    const val BIG_BEAT_GAP_MANY = 10f

    /** The common bars (2/4, 3/4, 4/4) have up to this many beats, and `BeatBubbles` spaces them more widely. */
    private const val FEW_BEATS = 4

    /** The pitch window is 300 dp tall at scale 1 (`PitchWindow`'s `WINDOW_HEIGHT`); sideways its scale is the card's inside height over it. */
    private const val PITCH_WINDOW_HEIGHT = 300f

    /** The ‹ and › buttons either side of the pitch window (52, as upright's `TargetNote`). */
    const val ARROW_SIZE = 52f

    /** The room between the window and each arrow button: upright's `ButtonGap`, so the sound bubble's arrow clears the › button. */
    const val ARROW_GAP = 20f

    /** The room between the window block and the words beside it, with the fingering under them. */
    const val FINGERING_GAP = 20f

    /** What the tuner's row spends across besides the window: an arrow and its gap, either side. */
    private const val PITCH_WINDOW_SIDES = 2 * (ARROW_SIZE + ARROW_GAP)

    /** The window is 132 dp wide at scale 1 (`PitchWindow`'s `WINDOW_WIDTH`). */
    private const val PITCH_WINDOW_WIDTH = 132f

    /**
     * The least the fingering is given beside the window, 205 dp wide and about 30 tall. It is not
     * higher because a camera hole takes width from the tab: an 800 wide phone with a hole of 45 dp
     * (the emulator's) or a 780 wide one with 30 leave 210 or 205, and both must keep the full-size
     * ring. The edge is a window 750 wide at 360 tall.
     */
    private const val FINGERING_MIN = 205f

    /** Wider than tall, and at least [MIN_WIDTH] wide, in either sideways direction. */
    fun isSideways(windowWidth: Float, windowHeight: Float): Boolean =
        windowWidth > windowHeight && windowWidth >= MIN_WIDTH

    /** Whether the frame draws its narrower rail, column and smaller rail contents. */
    fun isShort(height: Float): Boolean = height < SHORT

    fun railWidth(height: Float): Float = if (isShort(height)) RAIL_SHORT else RAIL

    fun columnWidth(height: Float): Float = if (isShort(height)) COLUMN_SHORT else COLUMN

    /** The sideways Pick button's dots are 11 dp where they fit (upright's are 10, and not sized by any column). */
    private const val PICK_DOT_MAX = 11f

    /** The room between the dots on the Pick button; the sideways button gives it to `LetterDots` (upright keeps the 3 dp it has by default). */
    const val PICK_DOT_GAP = 3f

    /**
     * What the Pick button keeps free of dots on each side: its outline, the curve of its corner beside
     * the dots, and some air. The row of dots is then at most the column's width less twice this.
     */
    private const val PICK_DOT_INSET = 9f

    /**
     * The size of a dot on the Pick button, for [letters] dots in a column [columnWidth] wide: the
     * usual 11 where the row of them keeps [PICK_DOT_INSET] clear either side, otherwise the largest
     * whole number that does. Only seven letters (all fifteen notes) on the 100 dp column shrink,
     * to 9; the usual five stay 11 on every phone.
     */
    fun pickDotSize(columnWidth: Float, letters: Int): Float {
        val count = letters.coerceAtLeast(1)
        val room = columnWidth - 2 * PICK_DOT_INSET - PICK_DOT_GAP * (count - 1)
        return minOf(PICK_DOT_MAX, floor(room / count))
    }

    /** The card: the content less the right column and the margins left of it, between and right of it. */
    fun cardWidth(contentWidth: Float, height: Float): Float = contentWidth - columnWidth(height) - 3 * MARGIN

    /** The width the card's content has. */
    private fun innerWidth(cardWidth: Float): Float = cardWidth - CARD_CHROME

    /** The height the card's content has: the window less the card's margins above and below and its chrome. */
    private fun innerHeight(height: Float): Float = height - 2 * MARGIN - CARD_CHROME

    fun keysWidth(contentWidth: Float): Float = if (contentWidth < NARROW_CONTENT) KEYS_NARROW else KEYS

    fun keyGap(contentWidth: Float): Float = if (contentWidth < NARROW_CONTENT) KEY_GAP_NARROW else KEY_GAP

    /** A third of what the height leaves for the rows, less each row's shadow: 85 at 411, 68 at 360. */
    fun keyHeight(height: Float): Float =
        ((height - KEYS_SPENT) / 3 - KEY_SHADOW).coerceIn(KEY_HEIGHT_MIN, KEY_HEIGHT_MAX)

    /**
     * The running staff's scale: the upright one's 4/3 where the card's height allows it, below that
     * the largest at which a staff [staffHeight] tall at rest fits (about 1.19 at 360 dp tall).
     */
    fun runningStaffScale(height: Float, staffHeight: Float): Float =
        minOf(4f / 3f, innerHeight(height) / staffHeight)

    /** The tempo ring, sideways: full size, or the card's height, or half its width where those are less. */
    fun ringSize(cardWidth: Float, height: Float): Float =
        minOf(RING_MAX, innerHeight(height), innerWidth(cardWidth) / 2)

    /** The room between the bubbles while the metronome plays: more for the four beats of the common bars. */
    fun bigBeatGap(beats: Int): Float = if (beats <= FEW_BEATS) BIG_BEAT_GAP_FEW else BIG_BEAT_GAP_MANY

    /**
     * A plain bubble's diameter while the metronome plays: `BeatBubbles`' formula (the first bubble
     * counts [FIRST_BEAT_EXTRA] more) across [innerWidth], but never past [BIG_BEAT_MAX]. No screen
     * calls it: `BeatBubbles` works its size out itself, from the same gaps ([bigBeatGap]) and the
     * same cap, and this is what the tests hold it to.
     */
    fun bigBeatUnit(innerWidth: Float, beats: Int): Float =
        ((innerWidth - bigBeatGap(beats) * (beats - 1)) / (beats + FIRST_BEAT_EXTRA)).coerceAtMost(BIG_BEAT_MAX)

    /** The tuner's pitch window grows with the card's height; unlike upright it is not capped at scale 1. */
    fun pitchWindowScale(height: Float): Float = innerHeight(height) / PITCH_WINDOW_HEIGHT

    /**
     * What the card's width leaves the fingering beside the pitch window: the card's inside less the
     * window with its arrows and the gap after it. [fingeringBeside] asks whether that is enough, and
     * `TunerSideways` gives the fingering its column from the same numbers.
     */
    fun fingeringRoom(cardWidth: Float, height: Float): Float {
        val window = PITCH_WINDOW_SIDES + PITCH_WINDOW_WIDTH * pitchWindowScale(height)
        return innerWidth(cardWidth) - window - FINGERING_GAP
    }

    /**
     * Whether the fingering stands beside the pitch window: when [fingeringRoom] is [FINGERING_MIN] or
     * more. At 360 tall a window under 750 wide says no, which is the 640 wide phone and any narrower.
     */
    fun fingeringBeside(cardWidth: Float, height: Float): Boolean = fingeringRoom(cardWidth, height) >= FINGERING_MIN
}
