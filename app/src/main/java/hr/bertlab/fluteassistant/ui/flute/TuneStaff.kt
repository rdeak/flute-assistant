// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.Bars
import hr.bertlab.fluteassistant.domain.flute.Joins
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.Listening
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pitch
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.FLASH_DURATION_MS
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.drawTrebleClef
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.common.hopOnBeat
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAR_LINE_WIDTH
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAR_SLOT
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BOTTOM_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BREATH_FACE_SIZE
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BREATH_FACE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BREATH_SLOT
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.TOP_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.slotWidth
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Nunito
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.roundToInt

/** The staff with its names and marks under it ([StaffGeometry.TOTAL_HEIGHT]) at rest: always the same, so nothing below it moves when notes come. */
val TuneStaffHeight = StaffGeometry.TOTAL_HEIGHT.dp

/**
 * How much the staff grows while the tune runs (Play or Your turn): the keys are gone, the phone
 * lies on a table or a music stand at arm's length, and lines 24 dp apart (the canvas's original
 * spacing) read from there.
 */
const val RunningStaffScale = 4f / 3f

/**
 * The staff's height at [scale] with [room] over it ([StaffGeometry.roomOver], since 2026-11-18; none for a tune up to
 * c³): [TuneStaffHeight] at rest, a third more while the tune runs.
 */
fun tuneStaffHeight(scale: Float, room: Float = 0f): Dp = ((StaffGeometry.TOTAL_HEIGHT + room) * scale).dp

/**
 * The knob's animal while the tune runs: over which step it stands and how many half beats into it
 * ([halfBeat], the last click's place: in Play the tune's, in Your turn the walk's along the note
 * the child plays, 0 while it waits at a head), which animal, its hop ([flash], 1 just after a beat
 * or a landing), and whether it waits for the child to play that step (Your turn) or runs with the
 * tune (Play).
 */
data class StaffAnimal(
    val index: Int,
    val emoji: String,
    val flash: () -> Float,
    val waiting: Boolean,
    val halfBeat: Int = 0,
)

/** The rest key's sign, and a rest's name pill. */
internal const val SLEEP_EMOJI = "💤" // zzz
/** The breath key's sign, and a breath's over the staff. */
internal const val BREATH_EMOJI = "😮‍💨" // face exhaling

/** How much bigger than [StaffGeometry]'s dp everything on the staff is drawn: 1 at rest, [RunningStaffScale] while the tune runs. */
internal val LocalStaffScale = compositionLocalOf { 1f }

/**
 * The room over the staff in its box, in dp at rest: everything is drawn that much lower. What a tune above c³ needs
 * ([StaffGeometry.roomOver]) while it runs and on a phone on its side; upright while the child writes, all the room the
 * box has (`FluteContent`: the tab's spare height, up to [StaffGeometry.HIGH_ROOM]).
 */
internal val LocalStaffRoom = compositionLocalOf { 0f }

/** The walking animal's box, the knob's size, for the tests to find where it stands. */
internal const val WALKING_ANIMAL_TAG = "staff_animal"

/**
 * Draws [block] in the staff's own dp, at rest, with the staff drawn at [scale] and [room] over it: the staff stands
 * that much lower in its box, which is that much taller, so the released numbers hold for everything on it.
 */
internal inline fun DrawScope.atStaff(scale: Float, room: Float, crossinline block: DrawScope.() -> Unit) {
    scale(density * scale, pivot = Offset.Zero) {
        translate(top = room) { block() }
    }
}

/**
 * The theme's colours as the staff uses them. A data class, so a staff recomposed with the same theme hands every
 * step an equal ink and the steps that did not change skip: the walking animal recomposes the staff on each hop.
 */
internal data class StaffInk(
    /** The lines, stems, ledger and bar lines, ties and dots: the text colour. */
    val line: Color,
    /** Round a note head, like everything outlined. */
    val outline: Color,
    /** Inside an open head: the card behind the staff. */
    val card: Color,
    /** A rest's symbol, quieter than a note. */
    val rest: Color,
)

/**
 * The child's tune written on a five-line staff, as the canvas drew it on 2026-11-02 and the user
 * changed it the same evening (no letters in the heads, every stem up, the staff at three
 * quarters of the drawn size), with each note's name in a pill right under it ([NameRow]: the
 * same room, so notes and names line up and scroll as one) and, under the names, a row for what
 * each note earned in "Your turn" ([MarkRow]: a star in [marks] under the head, faint after a
 * retry; under the step waited on the note the phone hears right now ([Listening.heardNow] of [listening]) as a bubble in
 * its own colour, so the child sees it listening, else the wrong note last heard for it
 * ([Listening.wrong]) until the next note is judged, else grey listening dots). While the tune runs the knob's [animal] walks the
 * staff at the knob's height ([WalkingAnimal]), hopping on to each beat's place. The faint treble clef
 * and the metronome tab's time signature stand at the left while the steps scroll past after
 * them, written in bars of [timeSignature] by [Bars.write]. Each note sits at its true height,
 * its head filled with its letter's colour (an open head with a coloured ring from a half note
 * up), a stem up on its right, ledger lines where it needs them, a flag on an eighth and a dot
 * on a dotted half, and as much room after it as it is long ([StaffGeometry.slotWidth]). A rest
 * is its rest symbol in grey over a grey 💤 pill; a breath the breath mark with 😮‍💨 over the
 * staff and no pill; a bar line follows every full bar, through the names too, and a note running
 * past one is drawn as heads tied under them and pills tied under those, a rest just split. The
 * step at [litIndex] (sounding now, waited for, or held) gets a yellow ring on every head and
 * every pill, and the one at [heldIndex], still growing under a finger, a soft yellow band across
 * its room, covering its notes and its names together. Everything is drawn [scale] times its
 * resting size (the geometry is one table in dp, so the running staff is one multiplication).
 * The staff follows the newest step and the lit one, keeping the last note before the lit one in
 * view ([Bars.anchor]: the note just played stays on the staff with what it earned, and the lit
 * step is the second thing in view), and on from there by as much as the walking [animal] needs to
 * stay in view ([StaffGeometry.followScroll]: a whole note's last beat after a whole note would be
 * past a phone's edge); the child can drag it back to look. The row's place is
 * [listState], which a caller that draws the staff in more than one place (a phone on its side
 * lays Play and a run out differently) keeps above them, so the staff does not scroll in again
 * from its start each time it moves.
 * While [tune] is empty a dashed hint sits on the lines instead.
 */
@Composable
fun TuneStaff(
    tune: List<TuneItem>,
    litIndex: Int?,
    heldIndex: Int?,
    timeSignature: TimeSignature,
    modifier: Modifier = Modifier,
    marks: Map<Int, StepMark> = emptyMap(),
    animal: StaffAnimal? = null,
    listening: Listening? = null,
    scale: Float = 1f,
    listState: LazyListState = rememberLazyListState(),
    joinWaiting: Boolean = false,
    room: Float = 0f,
) {
    val colors = FluteTheme.colors
    val ink = StaffInk(line = colors.text, outline = colors.outline, card = colors.card, rest = colors.mutedText)
    val measurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tuneStaffHeight(scale, room))
            .drawBehind {
                atStaff(scale, room) {
                    val width = size.width / (density * scale)
                    for (line in 0..4) {
                        val y = TOP_LINE_Y + line * StaffGeometry.STEP * 2
                        drawLine(ink.line, Offset(0f, y), Offset(width, y), StaffGeometry.LINE_WIDTH)
                    }
                    drawTrebleClef(
                        unit = StaffGeometry.CLEF_UNIT,
                        origin = Offset(StaffGeometry.CLEF_X, TOP_LINE_Y),
                        color = ink.line.copy(alpha = StaffGeometry.CLEF_ALPHA),
                        strokeUnits = StaffGeometry.CLEF_STROKE,
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalStaffScale provides scale, LocalStaffRoom provides room) {
            TimeSignatureMark(timeSignature, ink, measurer)
            if (tune.isEmpty()) {
                EmptyHint()
            } else {
                TuneMarks(
                    tune = tune, litIndex = litIndex, heldIndex = heldIndex, signature = timeSignature, marks = marks, animal = animal,
                    listening = listening, ink = ink, measurer = measurer, listState = listState, joinWaiting = joinWaiting,
                )
            }
        }
    }
}

/**
 * [text] measured to be drawn [size] dp tall (at rest) inside a drawing scaled to dp, whatever
 * the font scale: the time signature and the breath's face are part of the staff and must stay in
 * it, and they grow with the drawing when the staff does.
 */
@Composable
private fun staffText(measurer: TextMeasurer, text: String, size: Float): TextLayoutResult {
    val density = LocalDensity.current
    return remember(measurer, text, size, density) {
        measurer.measure(
            text,
            TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = with(density) { size.toSp() }),
        )
    }
}

/** Draws [layout] centred on [x] with its baseline at [baseline]. */
private fun DrawScope.drawCentred(layout: TextLayoutResult, x: Float, baseline: Float, color: Color = Color.Unspecified) {
    drawText(layout, color = color, topLeft = Offset(x - layout.size.width / 2f, baseline - layout.firstBaseline))
}

/** Draws an emoji's [layout] with its box centred on ([x], [y]). */
private fun DrawScope.drawEmoji(layout: TextLayoutResult, x: Float, y: Float) {
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
}

/** The time signature as written after the clef, its two figures each across two spaces. */
@Composable
private fun TimeSignatureMark(signature: TimeSignature, ink: StaffInk, measurer: TextMeasurer) {
    val s = LocalStaffScale.current
    val room = LocalStaffRoom.current
    val description = stringResource(R.string.flute_time_signature, signature.beatsPerBar, signature.noteValue)
    val top = staffText(measurer, signature.beatsPerBar.toString(), StaffGeometry.SIGNATURE_SIZE)
    val bottom = staffText(measurer, signature.noteValue.toString(), StaffGeometry.SIGNATURE_SIZE)
    val half = StaffGeometry.SIGNATURE_SIZE / 2
    Spacer(
        modifier = Modifier
            .offset(x = ((StaffGeometry.SIGNATURE_X - half) * s).dp, y = ((TOP_LINE_Y + room) * s).dp)
            .size((StaffGeometry.SIGNATURE_SIZE * s).dp, ((BOTTOM_LINE_Y - TOP_LINE_Y) * s).dp)
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                scale(density * s, pivot = Offset.Zero) {
                    // A figure's middle on the space between its two lines: its baseline about a third of its size below.
                    val drop = StaffGeometry.SIGNATURE_SIZE * 0.35f
                    drawCentred(top, half, StaffGeometry.SIGNATURE_TOP_Y - TOP_LINE_Y + drop, ink.line)
                    drawCentred(bottom, half, StaffGeometry.SIGNATURE_BOTTOM_Y - TOP_LINE_Y + drop, ink.line)
                }
            },
    )
}

/**
 * The dashed hint on the empty staff, after the time signature. A phone on its side can leave it
 * half of upright's room (about 116 dp at 640 dp wide), where one line would lose its last words at
 * any size, so there it may take a second line, its line height following its size, as the style's
 * fixed one leaves the pill no room for two. It stops shrinking at a size in dp, as a floor in sp
 * would grow with the text size and push it out of the pill.
 */
@Composable
private fun EmptyHint() {
    val s = LocalStaffScale.current
    val room = LocalStaffRoom.current
    val colors = FluteTheme.colors
    val sideways = LocalSideways.current
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    val smallest = if (sideways) HintFloor.asTextSize() else 14.sp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(
                start = (StaffGeometry.HINT_START * s).dp,
                end = (StaffGeometry.HINT_END_PADDING * s).dp,
                top = ((StaffGeometry.HINT_TOP + room) * s).dp,
            )
            .fillMaxWidth()
            .height((StaffGeometry.HINT_HEIGHT * s).dp)
            .drawBehind {
                val radius = CornerRadius((StaffGeometry.HINT_RADIUS * s).dp.toPx())
                drawRoundRect(colors.card, cornerRadius = radius)
                drawRoundRect(
                    colors.border,
                    cornerRadius = radius,
                    style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            },
    ) {
        Text(
            text = stringResource(R.string.flute_hint),
            style = if (sideways) style.copy(lineHeight = 1.15.em) else style,
            color = colors.mutedText,
            textAlign = if (sideways) TextAlign.Center else TextAlign.Unspecified,
            maxLines = if (sideways) 2 else 1,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = 20.sp),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** The smallest the hint gets on a phone on its side. */
private val HintFloor = 14.dp

/** The scrolling row of what [Bars.write] makes of [tune], after the clef and the time signature. */
@Composable
private fun TuneMarks(
    tune: List<TuneItem>,
    litIndex: Int?,
    heldIndex: Int?,
    signature: TimeSignature,
    marks: Map<Int, StepMark>,
    animal: StaffAnimal?,
    listening: Listening?,
    ink: StaffInk,
    measurer: TextMeasurer,
    listState: LazyListState,
    joinWaiting: Boolean,
) {
    val s = LocalStaffScale.current
    val notes = tune.count { it is TuneNote }
    val description = pluralStringResource(R.plurals.flute_tune_line, notes, notes)
    val lineMarks = remember(tune, signature) { Bars.write(tune, signature) }
    // Where each mark starts along the row, in dp at rest, from the end of the row's start padding.
    val starts = remember(lineMarks) { lineMarks.runningFold(0f) { x, mark -> x + StaffGeometry.markWidth(mark) } }
    // The last note before the lit step (so the note just played stays in view with its mark and
    // the lit step comes second), else the lit step, else the staff's end (a bar line, once a bar is full).
    val target = Bars.anchor(lineMarks, tune, litIndex)
    val density = LocalDensity.current
    var rowWidth by remember { mutableFloatStateOf(0f) }
    // Where the walking animal stands along the row, and how far on the staff scrolls for it to stay in the row:
    // 0 while it is in view, so its hops inside the row scroll nothing (a staff the child dragged back stays until
    // the next note, as before).
    val animalX = animal?.let { animalX(it, tune, lineMarks, starts) }
    val follow = animalX?.takeIf { rowWidth > 0f }?.let { StaffGeometry.followScroll(starts[target], it, rowWidth, s) } ?: 0f
    // Keyed on the marks themselves, so a held note that grows (or splits at a bar line) stays in view.
    LaunchedEffect(target, lineMarks, follow) {
        listState.animateScrollToItem(target, with(density) { follow.dp.roundToPx() })
    }
    // The row starts a little before the first step, so a ring round it is never cut off.
    Box(
        Modifier
            .padding(start = (StaffGeometry.NOTES_CLIP * s).dp)
            .fillMaxSize()
            .onSizeChanged { rowWidth = with(density) { it.width.toDp().value } },
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description },
            contentPadding = PaddingValues(
                start = ((StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP) * s).dp,
                end = (StaffGeometry.endPadding(running = animal != null) * s).dp,
            ),
        ) {
            items(lineMarks) { mark ->
                when (mark) {
                    is LineMark.Step -> {
                        val waiting = animal?.waiting == true && animal.index == mark.index
                        StaffStep(
                            item = tune[mark.index],
                            step = mark,
                            lit = mark.index == litIndex,
                            growing = mark.index == heldIndex,
                            mark = marks[mark.index],
                            waiting = waiting,
                            heard = listening?.wrong?.takeIf { it.index == mark.index }?.midi,
                            heardNow = listening?.heardNow.takeIf { waiting },
                            pitch = listening?.pitch.takeIf { waiting },
                            ink = ink,
                        )
                    }
                    is LineMark.Breath -> BreathMark(ink, measurer)
                    LineMark.BarLine -> BarLineMark(ink)
                }
            }
        }
        SlurLayer(tune, lineMarks, starts, listState, joinWaiting, ink)
        if (animal != null && animalX != null) WalkingAnimal(animal, animalX, starts, listState)
    }
}

/** Where [animal] stands along the row, in dp at rest from its first mark ([starts]); null when its step is not on the line. */
internal fun animalX(animal: StaffAnimal, tune: List<TuneItem>, lineMarks: List<LineMark>, starts: List<Float>): Float? {
    if (tune.getOrNull(animal.index) == null) return null
    // Listen to me walks a tie (or an octave slur) as one note: past its first note the animal goes on onto the next.
    val (index, halfBeat) = Joins.placeOf(tune, animal.index, animal.halfBeat)
    val step = lineMarks.indexOfFirst { it is LineMark.Step && it.index == index }
    if (step < 0) return null
    val mark = lineMarks[step] as LineMark.Step
    return starts[step] + StaffGeometry.signRoom(mark) + StaffGeometry.beatX(mark.parts, halfBeat)
}

/**
 * The knob's animal while the tune runs, walking the staff at the knob's height
 * ([StaffGeometry.animalY]: [StaffGeometry.ANIMAL_Y], or higher over a staff drawn smaller) over the step it stands for (the user's pick of the evening of
 * 2026-11-04, "A": like the metronome's beat bubbles, never two hops in one place): over the
 * step's head when the step begins, then on each click over that beat's place
 * ([StaffGeometry.beatX]), so a half note is two places and a whole note four; it slides to each
 * new place as it hops ([FLASH_DURATION_MS], the hop's own fall, so it lands as the hop ends),
 * across bar lines and breaths too, and in Your turn from the note just played to the next. It is
 * one layer over the row, placed from the row's own layout at every frame, so it moves with the
 * staff's scrolling and never recomposes for it; the row's edges clip it, so a staff dragged back
 * hides it rather than sliding it over the knob. Facing right, sized in dp like the staff's other
 * text, and silent: a screen reader hears "play this one" on the step itself.
 */
@Composable
private fun WalkingAnimal(animal: StaffAnimal, target: Float, starts: List<Float>, listState: LazyListState) {
    val s = LocalStaffScale.current
    val x = remember { Animatable(target) }
    LaunchedEffect(target) {
        x.animateTo(target, tween(FLASH_DURATION_MS, easing = FastOutSlowInEasing))
    }
    val box = StaffGeometry.CORNER_BUTTON.dp // the knob's own box, so the animal's middle is the knob's
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clippedAtRowSides(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(box)
                .offset {
                    val info = listState.layoutInfo
                    val first = info.visibleItemsInfo.firstOrNull()
                    val firstStart = first?.let { starts.getOrNull(it.index) } ?: return@offset IntOffset(-box.roundToPx() * 4, 0)
                    // The first item shown is the reference, so a pixel rounded per item never adds up.
                    val middle = info.beforeContentPadding + first.offset + ((x.value - firstStart) * s).dp.toPx()
                    IntOffset((middle - box.toPx() / 2).roundToInt(), (StaffGeometry.animalY(s).dp - box / 2).roundToPx())
                }
                .testTag(WALKING_ANIMAL_TAG),
        ) {
            Emoji(
                animal.emoji,
                size = (StaffGeometry.ANIMAL_SIZE * s).dp,
                modifier = Modifier.facingRight(animal.emoji).hopOnBeat(animal.flash),
            )
        }
    }
}

/** What a screen reader says for a note or a rest called [name] lasting [length]. */
@Composable
private fun lengthDescription(name: String, length: NoteLength): String = when (length) {
    NoteLength.EIGHTH -> stringResource(R.string.flute_note_half_beat, name)
    else -> pluralStringResource(R.plurals.flute_note_beats, length.wholeBeats, name, length.wholeBeats)
}

/**
 * One note or rest as written: its [parts], a piece per length with a bar line between two of
 * them where it crosses one, a note's heads tied, its names under them ([NameRow]) and, under
 * those, what it earned ([MarkRow]: [mark], or the wrong note [heard] for it). A screen reader
 * hears it once, by its whole length, then what it earned, or that the animal is [waiting] on it
 * in Your turn ("play this one"). The lit step is drawn under its neighbours, so its yellow rings
 * never cover the edge of the head or the pill beside it.
 */
@Composable
private fun StaffStep(
    item: TuneItem,
    step: LineMark.Step,
    lit: Boolean,
    growing: Boolean,
    mark: StepMark?,
    waiting: Boolean,
    heard: Int?,
    heardNow: Int?,
    pitch: Pitch?,
    ink: StaffInk,
) {
    val s = LocalStaffScale.current
    val room = LocalStaffRoom.current
    val name = when (item) {
        is TuneNote -> lengthDescription(noteLabel(item.midi), item.length).let {
            if (item.joined) stringResource(R.string.flute_note_joined, it) else it
        }
        is TuneRest -> lengthDescription(stringResource(R.string.flute_rest), item.length)
        TuneBreath -> stringResource(R.string.flute_breath_here)
    }
    val earned = when {
        heard != null -> stringResource(R.string.flute_mark_heard, noteLabel(heard))
        waiting -> stringResource(R.string.flute_listen_here)
        mark != null -> stringResource(mark.spoken)
        else -> null
    }
    val description = if (earned == null) name else "$name, $earned"
    // A sign (♯ ♭ ♮, since 2026-11-16) takes its room before the first head; everything after it moves right by it.
    val layout = remember(step) { StaffGeometry.layout(step) }
    Box(
        modifier = Modifier
            .zIndex(if (lit || growing) -1f else 0f)
            .size((layout.width * s).dp, tuneStaffHeight(s, room))
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                atStaff(s, room) {
                    drawStep(item, step, layout, lit, growing, ink)
                }
            },
    ) {
        NameRow(item, step.parts, lit, layout, Modifier.offset(y = ((StaffGeometry.NAMES_TOP + room) * s).dp))
        MarkRow(mark, heard, heardNow, pitch, waiting, Modifier.offset(x = (layout.signRoom * s).dp, y = ((StaffGeometry.MARKS_TOP + room) * s).dp))
    }
}

/**
 * The names of one step under its notes: a pill per piece in its letter's colour, as wide as the
 * piece's room on the staff less [StaffGeometry.PILL_GAP], the name under the head (in the first
 * beat's room of a longer pill); a grey 💤 pill for a rest; the bar line it crosses; a note's
 * pills tied under them.
 */
@Composable
private fun NameRow(item: TuneItem, parts: List<StepPart>, lit: Boolean, layout: StaffGeometry.StepLayout, modifier: Modifier = Modifier) {
    val s = LocalStaffScale.current
    val colors = FluteTheme.colors
    val tied = item is TuneNote && parts.count { it is StepPart.Length } > 1
    Row(
        modifier = modifier
            .height((StaffGeometry.PILL_HEIGHT * s).dp)
            .then(if (tied) Modifier.drawBehind { scale(density * s, pivot = Offset.Zero) { drawPillTies(layout.pills, colors.mutedText) } } else Modifier),
    ) {
        val first = parts.indexOfFirst { it is StepPart.Length }
        for ((at, part) in parts.withIndex()) {
            // The first pill is as much wider as the sign's room, so the name with its sign fits under sign and head.
            val extra = if (at == first) layout.signRoom else 0f
            when (part) {
                is StepPart.Length -> Box(Modifier.width(((slotWidth(part.length) + extra) * s).dp).fillMaxHeight()) {
                    NamePill(item, part.length, lit, Modifier.padding(start = (StaffGeometry.PILL_GAP / 2 * s).dp), extra)
                }
                LineMark.BarLine -> Box(Modifier.width((BAR_SLOT * s).dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    PillBarLine()
                }
            }
        }
    }
}

/** One pill: the note's name in its letter's colour, or 💤 on grey for a rest. */
@Composable
private fun NamePill(item: TuneItem, length: NoteLength, lit: Boolean, modifier: Modifier = Modifier, extra: Float = 0f) {
    val s = LocalStaffScale.current
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape((StaffGeometry.PILL_RADIUS * s).dp)
    val fill = (item as? TuneNote)?.let { noteColor(it.midi) } ?: colors.track
    Box(
        modifier = modifier
            .size(((slotWidth(length) + extra - StaffGeometry.PILL_GAP) * s).dp, (StaffGeometry.PILL_HEIGHT * s).dp)
            .drawBehind { if (lit) drawPillRing(s) }
            .clip(shape)
            .background(fill)
            .border(OutlineWidth, colors.outline, shape),
    ) {
        // The name stands in the first beat's room, under the head; a longer pill runs on after it.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(((StaffGeometry.BEAT_WIDTH + extra - StaffGeometry.PILL_GAP) * s).dp)
                .fillMaxHeight()
                .padding(horizontal = OutlineWidth), // inside the outline, so the octave figure never meets it
        ) {
            if (item is TuneNote) {
                Text(
                    noteText(item.midi),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkOnFill,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = (17 * s).sp),
                )
            } else {
                Emoji(SLEEP_EMOJI, fontSize = (14 * s).roundToInt())
            }
        }
    }
}

/** A bar line through the names, as tall as a pill, in the text colour like the staff's. */
@Composable
private fun PillBarLine() {
    val s = LocalStaffScale.current
    Box(
        modifier = Modifier
            .size(3.dp, (StaffGeometry.PILL_HEIGHT * s).dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(FluteTheme.colors.text),
    )
}

/** The yellow ring round a lit pill, a little outside it, its corners following the pill's at [s]. */
private fun DrawScope.drawPillRing(s: Float) {
    val stroke = 4.dp.toPx()
    val outset = 2.dp.toPx() + stroke / 2
    drawRoundRect(
        color = Sun,
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius((StaffGeometry.PILL_RADIUS * s).dp.toPx() + outset),
        style = Stroke(stroke),
    )
}

/** The ties between the pills of one note, in dp: an arc under them from each pill's middle ([pills]) to the next one's. */
private fun DrawScope.drawPillTies(pills: List<Float>, color: Color) {
    val top = StaffGeometry.PILL_HEIGHT + StaffGeometry.PILL_TIE_GAP
    for ((from, to) in pills.zipWithNext()) drawPath(pillArcPath(from, to, top), color, style = Stroke(2f, cap = StrokeCap.Round))
}

/** A bar line across the staff, and through the names under it. */
@Composable
private fun BarLineMark(ink: StaffInk) {
    val s = LocalStaffScale.current
    val room = LocalStaffRoom.current
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .size((BAR_SLOT * s).dp, tuneStaffHeight(s, room))
            .drawBehind {
                atStaff(s, room) {
                    drawLine(ink.line, Offset(BAR_SLOT / 2, TOP_LINE_Y), Offset(BAR_SLOT / 2, BOTTOM_LINE_Y), BAR_LINE_WIDTH)
                }
            },
    ) {
        Box(Modifier.offset(y = ((StaffGeometry.NAMES_TOP + room) * s).dp)) { PillBarLine() }
    }
}

/** A breath: no time, only a little room for the breath mark in the top space and the breathing face over the staff; no name. */
@Composable
private fun BreathMark(ink: StaffInk, measurer: TextMeasurer) {
    val s = LocalStaffScale.current
    val room = LocalStaffRoom.current
    val description = stringResource(R.string.flute_breath_here)
    val face = staffText(measurer, BREATH_EMOJI, BREATH_FACE_SIZE)
    Spacer(
        modifier = Modifier
            .size((BREATH_SLOT * s).dp, tuneStaffHeight(s, room))
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                atStaff(s, room) {
                    val x = BREATH_SLOT / 2
                    drawPath(
                        Path().apply {
                            moveTo(x - 1.5f, TOP_LINE_Y + 3f)
                            quadraticTo(x + 2.25f, TOP_LINE_Y + 4.5f, x + 0.75f, TOP_LINE_Y + 9.75f)
                        },
                        ink.line,
                        style = Stroke(2.5f, cap = StrokeCap.Round),
                    )
                    drawEmoji(face, x, BREATH_FACE_Y)
                }
            },
    )
}
