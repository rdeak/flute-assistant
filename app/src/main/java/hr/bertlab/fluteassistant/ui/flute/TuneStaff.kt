package hr.bertlab.fluteassistant.ui.flute

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.Bars
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.drawTrebleClef
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_ALPHA
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_BOTTOM
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_RADIUS
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_TOP
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAR_LINE_WIDTH
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAR_SLOT
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BOTTOM_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BREATH_FACE_SIZE
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BREATH_FACE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BREATH_SLOT
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.HEAD_RX
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.HEAD_RY
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.HEAD_X
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.MIDDLE_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.TOP_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.slotWidth
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.yOf
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Nunito
import hr.bertlab.fluteassistant.ui.theme.Sun

/** The staff with its names under it ([StaffGeometry.TOTAL_HEIGHT]): always the same, so nothing below it moves when notes come. */
val TuneStaffHeight = StaffGeometry.TOTAL_HEIGHT.dp

/** The rest key's sign, and a rest's name pill. */
internal const val SLEEP_EMOJI = "💤" // zzz
/** The breath key's sign, and a breath's over the staff. */
internal const val BREATH_EMOJI = "😮‍💨" // face exhaling

/** The theme's colours as the staff uses them, read once per composition. */
private class StaffInk(
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
 * same room, so notes and names line up and scroll as one): the faint treble clef
 * and the metronome tab's time signature stand at the left while the steps scroll past after
 * them, written in bars of [timeSignature] by [Bars.write]. Each note sits at its true height,
 * its head filled with its letter's colour (an open head with a coloured ring from a half note
 * up), a stem up on its right, ledger lines where it needs them, a flag on an eighth and a dot
 * on a dotted half, and as much room after it as it is long ([StaffGeometry.slotWidth]). A rest
 * is its rest symbol in grey over a grey 💤 pill; a breath the breath mark with 😮‍💨 over the
 * staff and no pill; a bar line follows every full bar, through the names too, and a note running
 * past one is drawn as heads tied under them and pills tied under those, a rest just split. The
 * step at [litIndex] (sounding now, or held) gets a yellow ring on every head and every pill, and
 * the one at [heldIndex], still growing under a finger, a soft yellow band across its room,
 * covering its notes and its names together. The staff follows the newest step and the playing one, and the child
 * can drag it back to look. While [tune] is empty a dashed hint sits on the lines instead.
 */
@Composable
fun TuneStaff(
    tune: List<TuneItem>,
    litIndex: Int?,
    heldIndex: Int?,
    timeSignature: TimeSignature,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val ink = StaffInk(line = colors.text, outline = colors.outline, card = colors.card, rest = colors.mutedText)
    val measurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TuneStaffHeight)
            .drawBehind {
                scale(density, pivot = Offset.Zero) {
                    val width = size.width / density
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
        TimeSignatureMark(timeSignature, ink, measurer)
        if (tune.isEmpty()) {
            EmptyHint()
        } else {
            TuneMarks(tune, litIndex, heldIndex, timeSignature, ink, measurer)
        }
    }
}

/**
 * [text] measured to be drawn [size] dp tall inside a drawing scaled to dp, whatever the font
 * scale: the time signature and the breath's face are part of the staff and must stay in it.
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
    val description = stringResource(R.string.flute_time_signature, signature.beatsPerBar, signature.noteValue)
    val top = staffText(measurer, signature.beatsPerBar.toString(), StaffGeometry.SIGNATURE_SIZE)
    val bottom = staffText(measurer, signature.noteValue.toString(), StaffGeometry.SIGNATURE_SIZE)
    val half = StaffGeometry.SIGNATURE_SIZE / 2
    Spacer(
        modifier = Modifier
            .offset(x = (StaffGeometry.SIGNATURE_X - half).dp, y = TOP_LINE_Y.dp)
            .size(StaffGeometry.SIGNATURE_SIZE.dp, (BOTTOM_LINE_Y - TOP_LINE_Y).dp)
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                scale(density, pivot = Offset.Zero) {
                    // A figure's middle on the space between its two lines: its baseline about a third of its size below.
                    val drop = StaffGeometry.SIGNATURE_SIZE * 0.35f
                    drawCentred(top, half, StaffGeometry.SIGNATURE_TOP_Y - TOP_LINE_Y + drop, ink.line)
                    drawCentred(bottom, half, StaffGeometry.SIGNATURE_BOTTOM_Y - TOP_LINE_Y + drop, ink.line)
                }
            },
    )
}

/** The dashed hint on the empty staff, after the time signature. */
@Composable
private fun EmptyHint() {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = StaffGeometry.HINT_START.dp, end = StaffGeometry.HINT_END_PADDING.dp, top = StaffGeometry.HINT_TOP.dp)
            .fillMaxWidth()
            .height(StaffGeometry.HINT_HEIGHT.dp)
            .drawBehind {
                val radius = CornerRadius(StaffGeometry.HINT_RADIUS.dp.toPx())
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
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.mutedText,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 20.sp),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** The scrolling row of what [Bars.write] makes of [tune], after the clef and the time signature. */
@Composable
private fun TuneMarks(
    tune: List<TuneItem>,
    litIndex: Int?,
    heldIndex: Int?,
    signature: TimeSignature,
    ink: StaffInk,
    measurer: TextMeasurer,
) {
    val notes = tune.count { it is TuneNote }
    val description = pluralStringResource(R.plurals.flute_tune_line, notes, notes)
    val marks = remember(tune, signature) { Bars.write(tune, signature) }
    // The lit step's mark, else the staff's end (a bar line, once a bar is full).
    val target = marks.indexOfFirst { it is LineMark.Step && it.index == litIndex }.takeIf { it >= 0 } ?: marks.lastIndex
    val listState = rememberLazyListState()
    // Keyed on the marks themselves, so a held note that grows (or splits at a bar line) stays in view.
    LaunchedEffect(target, marks) {
        listState.animateScrollToItem(target)
    }
    LazyRow(
        state = listState,
        // The row starts a little before the first step, so a ring round it is never cut off.
        modifier = Modifier
            .padding(start = StaffGeometry.NOTES_CLIP.dp)
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(
            start = (StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP).dp,
            end = StaffGeometry.NOTES_END_PADDING.dp,
        ),
    ) {
        items(marks) { mark ->
            when (mark) {
                is LineMark.Step -> StepMark(
                    item = tune[mark.index],
                    parts = mark.parts,
                    lit = mark.index == litIndex,
                    growing = mark.index == heldIndex,
                    ink = ink,
                )
                is LineMark.Breath -> BreathMark(ink, measurer)
                LineMark.BarLine -> BarLineMark(ink)
            }
        }
    }
}

/** What a screen reader says for a note or a rest called [name] lasting [length]. */
@Composable
private fun lengthDescription(name: String, length: NoteLength): String = when (length) {
    NoteLength.EIGHTH -> stringResource(R.string.flute_note_half_beat, name)
    else -> pluralStringResource(R.plurals.flute_note_beats, length.wholeBeats, name, length.wholeBeats)
}

private fun partWidth(part: StepPart): Float = when (part) {
    is StepPart.Length -> slotWidth(part.length)
    LineMark.BarLine -> BAR_SLOT
}

/**
 * One note or rest as written: its [parts], a piece per length with a bar line between two of
 * them where it crosses one, a note's heads tied, and its names under them ([NameRow]). A screen
 * reader hears it once, by its whole length. The lit step is drawn under its neighbours, so its
 * yellow rings never cover the edge of the head or the pill beside it.
 */
@Composable
private fun StepMark(
    item: TuneItem,
    parts: List<StepPart>,
    lit: Boolean,
    growing: Boolean,
    ink: StaffInk,
) {
    val description = when (item) {
        is TuneNote -> lengthDescription(noteLabel(item.midi), item.length)
        is TuneRest -> lengthDescription(stringResource(R.string.flute_rest), item.length)
        TuneBreath -> stringResource(R.string.flute_breath_here)
    }
    val width = parts.fold(0f) { sum, part -> sum + partWidth(part) }
    Box(
        modifier = Modifier
            .zIndex(if (lit || growing) -1f else 0f)
            .size(width.dp, TuneStaffHeight)
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                scale(density, pivot = Offset.Zero) {
                    drawStep(item, parts, lit, growing, width, ink)
                }
            },
    ) {
        NameRow(item, parts, lit, Modifier.offset(y = StaffGeometry.NAMES_TOP.dp))
    }
}

/**
 * The names of one step under its notes: a pill per piece in its letter's colour, as wide as the
 * piece's room on the staff less [StaffGeometry.PILL_GAP], the name under the head (in the first
 * beat's room of a longer pill); a grey 💤 pill for a rest; the bar line it crosses; a note's
 * pills tied under them.
 */
@Composable
private fun NameRow(item: TuneItem, parts: List<StepPart>, lit: Boolean, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val tied = item is TuneNote && parts.count { it is StepPart.Length } > 1
    Row(
        modifier = modifier
            .height(StaffGeometry.PILL_HEIGHT.dp)
            .then(if (tied) Modifier.drawBehind { scale(density, pivot = Offset.Zero) { drawPillTies(parts, colors.mutedText) } } else Modifier),
    ) {
        for (part in parts) {
            when (part) {
                is StepPart.Length -> Box(Modifier.width(slotWidth(part.length).dp).fillMaxHeight()) {
                    NamePill(item, part.length, lit, Modifier.padding(start = (StaffGeometry.PILL_GAP / 2).dp))
                }
                LineMark.BarLine -> Box(Modifier.width(BAR_SLOT.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    PillBarLine()
                }
            }
        }
    }
}

/** One pill: the note's name in its letter's colour, or 💤 on grey for a rest. */
@Composable
private fun NamePill(item: TuneItem, length: NoteLength, lit: Boolean, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(StaffGeometry.PILL_RADIUS.dp)
    val fill = (item as? TuneNote)?.let { noteColor(it.midi) } ?: colors.track
    Box(
        modifier = modifier
            .size((slotWidth(length) - StaffGeometry.PILL_GAP).dp, StaffGeometry.PILL_HEIGHT.dp)
            .drawBehind { if (lit) drawPillRing() }
            .clip(shape)
            .background(fill)
            .border(OutlineWidth, colors.outline, shape),
    ) {
        // The name stands in the first beat's room, under the head; a longer pill runs on after it.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width((StaffGeometry.BEAT_WIDTH - StaffGeometry.PILL_GAP).dp)
                .fillMaxHeight()
                .padding(horizontal = OutlineWidth), // inside the outline, so the octave figure never meets it
        ) {
            if (item is TuneNote) {
                Text(
                    noteText(item.midi),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkOnFill,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp),
                )
            } else {
                Emoji(SLEEP_EMOJI, fontSize = 14)
            }
        }
    }
}

/** A bar line through the names, as tall as a pill, in the text colour like the staff's. */
@Composable
private fun PillBarLine() {
    Box(
        modifier = Modifier
            .size(3.dp, StaffGeometry.PILL_HEIGHT.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(FluteTheme.colors.text),
    )
}

/** The yellow ring round a lit pill, a little outside it. */
private fun DrawScope.drawPillRing() {
    val stroke = 4.dp.toPx()
    val outset = 2.dp.toPx() + stroke / 2
    drawRoundRect(
        color = Sun,
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius(StaffGeometry.PILL_RADIUS.dp.toPx() + outset),
        style = Stroke(stroke),
    )
}

/** The ties between the pills of one note, in dp: an arc under them from each pill's middle to the next one's. */
private fun DrawScope.drawPillTies(parts: List<StepPart>, color: Color) {
    val middles = mutableListOf<Float>()
    var x = 0f
    for (part in parts) {
        if (part is StepPart.Length) middles += x + slotWidth(part.length) / 2
        x += partWidth(part)
    }
    val top = StaffGeometry.PILL_HEIGHT + StaffGeometry.PILL_TIE_GAP
    for ((from, to) in middles.zipWithNext()) {
        drawPath(
            Path().apply {
                moveTo(from + 3f, top)
                quadraticTo((from + to) / 2, top + StaffGeometry.PILL_TIE_DIP * 2, to - 3f, top)
            },
            color,
            style = Stroke(2f, cap = StrokeCap.Round),
        )
    }
}

/** Draws one step, in dp: the band and the rings first, so no piece's ring covers another piece's head. */
private fun DrawScope.drawStep(
    item: TuneItem,
    parts: List<StepPart>,
    lit: Boolean,
    growing: Boolean,
    width: Float,
    ink: StaffInk,
) {
    if (growing) {
        drawRoundRect(
            Sun.copy(alpha = BAND_ALPHA),
            topLeft = Offset(1f, BAND_TOP),
            size = Size(width - 2f, BAND_BOTTOM - BAND_TOP),
            cornerRadius = CornerRadius(BAND_RADIUS),
        )
    }
    val pieces = mutableListOf<Pair<Float, NoteLength>>() // each piece's head centre and length
    var x = 0f
    for (part in parts) {
        if (part is StepPart.Length) pieces += x + HEAD_X to part.length
        else drawLine(ink.line, Offset(x + BAR_SLOT / 2, TOP_LINE_Y), Offset(x + BAR_SLOT / 2, BOTTOM_LINE_Y), BAR_LINE_WIDTH)
        x += partWidth(part)
    }
    when (item) {
        is TuneNote -> {
            val position = StaffNotes.positionOf(item.midi)
            if (lit) pieces.forEach { (cx, _) -> drawCircle(Sun, StaffGeometry.LIT_RADIUS, Offset(cx, yOf(position))) }
            pieces.forEach { (cx, length) -> drawNote(cx, item.midi, position, length, ink) }
            drawTies(pieces.map { it.first }, yOf(position), noteColor(item.midi))
        }
        is TuneRest -> {
            if (lit) pieces.forEach { (cx, _) -> drawCircle(Sun, StaffGeometry.REST_LIT_RADIUS, Offset(cx, MIDDLE_LINE_Y)) }
            pieces.forEach { (cx, length) -> drawRest(cx, length, ink.rest) }
        }
        TuneBreath -> Unit
    }
}

/**
 * One note at [position] as printed music draws it, all of it in its letter's colour: its ledger
 * lines (in the staff's ink), a tilted oval head (filled for an eighth and a quarter, a ring for a
 * half and a dotted half, a wider ring for a whole note) with a thin edge, a stem up on the right
 * (none for a whole note), a flag on an eighth and a dot on a dotted half.
 */
private fun DrawScope.drawNote(cx: Float, midi: Int, position: Int, length: NoteLength, ink: StaffInk) {
    val y = yOf(position)
    for (ledger in StaffNotes.ledgerLinesFor(position)) {
        val ly = yOf(ledger)
        drawLine(ink.line, Offset(cx - StaffGeometry.LEDGER_HALF, ly), Offset(cx + StaffGeometry.LEDGER_HALF, ly), StaffGeometry.LINE_WIDTH)
    }
    val color = noteColor(midi)
    if (length != NoteLength.WHOLE) {
        val stemX = cx + StaffGeometry.STEM_X
        val top = y - StaffGeometry.STEM_REACH
        drawLine(color, Offset(stemX, y - StaffGeometry.STEM_START), Offset(stemX, top), StaffGeometry.STEM_WIDTH, cap = StrokeCap.Round)
        if (length == NoteLength.EIGHTH) {
            // The flag, to the right of the stem, curving down from its top.
            drawPath(
                Path().apply {
                    moveTo(stemX, top)
                    cubicTo(stemX + 1f, top + 8f, stemX + 8f, top + 10f, stemX + 6f, top + 20f)
                },
                color,
                style = Stroke(2.75f, cap = StrokeCap.Round),
            )
        }
    }
    val (rx, ry) = if (length == NoteLength.WHOLE) StaffGeometry.WHOLE_RX to StaffGeometry.WHOLE_RY else HEAD_RX to HEAD_RY
    rotate(StaffGeometry.HEAD_TILT, pivot = Offset(cx, y)) {
        val edge = StaffGeometry.HEAD_EDGE
        if (length.halfBeats >= NoteLength.HALF.halfBeats) {
            // A ring: the staff shows through the hole, as on paper; the edge drawn on both sides of it.
            val ring = StaffGeometry.OPEN_RING
            drawOval(color, Offset(cx - rx + ring / 2, y - ry + ring / 2), Size((rx - ring / 2) * 2, (ry - ring / 2) * 2), style = Stroke(ring))
            drawOval(ink.outline, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2), style = Stroke(edge))
            drawOval(ink.outline, Offset(cx - rx + ring, y - ry + ring), Size((rx - ring) * 2, (ry - ring) * 2), style = Stroke(edge))
        } else {
            drawOval(color, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2))
            drawOval(ink.outline, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2), style = Stroke(edge))
        }
    }
    if (length == NoteLength.DOTTED_HALF) {
        val dotY = if (StaffGeometry.onLine(position)) y - StaffGeometry.STEP else y
        drawCircle(color, StaffGeometry.DOT_RADIUS, Offset(cx + StaffGeometry.DOT_X, dotY))
    }
}

/** The ties between the heads of one note, centred at [heads] at height [y], in its [color]: always under them, away from the stems. */
private fun DrawScope.drawTies(heads: List<Float>, y: Float, color: Color) {
    for ((from, to) in heads.zipWithNext()) {
        drawPath(
            Path().apply {
                moveTo(from + StaffGeometry.TIE_INSET, y + StaffGeometry.TIE_START)
                quadraticTo((from + to) / 2, y + StaffGeometry.TIE_CONTROL, to - StaffGeometry.TIE_INSET, y + StaffGeometry.TIE_START)
            },
            color,
            style = Stroke(StaffGeometry.TIE_WIDTH, cap = StrokeCap.Round),
        )
    }
}

/**
 * One piece of a rest centred on [cx], as written in the treble staff, with 💤 under the staff:
 * the eighth's dot and slanting stem, the quarter's zigzag (drawn on the canvas), a block on the
 * middle line (half, with a dot for the dotted half) or hanging from the line above it (whole).
 */
private fun DrawScope.drawRest(cx: Float, length: NoteLength, color: Color) {
    val m = MIDDLE_LINE_Y
    val block = Size(15f, StaffGeometry.STEP)
    when (length) {
        NoteLength.EIGHTH -> {
            drawCircle(color, 3.4f, Offset(cx - 3f, m - 8.25f))
            drawPath(
                Path().apply {
                    moveTo(cx - 3f, m - 6f)
                    quadraticTo(cx + 0.75f, m - 3f, cx + 5.25f, m - 10.5f)
                    lineTo(cx, m + 13.5f)
                },
                color,
                style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        NoteLength.QUARTER -> drawPath(
            Path().apply {
                moveTo(cx - 3f, m - 18f)
                lineTo(cx + 3.75f, m - 9f)
                lineTo(cx - 2.25f, m - 1.5f)
                lineTo(cx + 4.5f, m + 7.5f)
                cubicTo(cx - 3f, m + 3.75f, cx - 4.5f, m + 9f, cx + 0.75f, m + 15f)
            },
            color,
            style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // A half rest sits on the middle line; a whole rest hangs from the line above it.
        NoteLength.HALF -> drawRect(color, Offset(cx - block.width / 2, m - block.height), block)
        NoteLength.DOTTED_HALF -> {
            drawRect(color, Offset(cx - block.width / 2, m - block.height), block)
            drawCircle(color, StaffGeometry.DOT_RADIUS, Offset(cx + 13.5f, m - StaffGeometry.STEP))
        }
        NoteLength.WHOLE -> drawRect(color, Offset(cx - block.width / 2, m - StaffGeometry.LINE_GAP), block)
    }
}

/** A bar line across the staff, and through the names under it. */
@Composable
private fun BarLineMark(ink: StaffInk) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .size(BAR_SLOT.dp, TuneStaffHeight)
            .drawBehind {
                scale(density, pivot = Offset.Zero) {
                    drawLine(ink.line, Offset(BAR_SLOT / 2, TOP_LINE_Y), Offset(BAR_SLOT / 2, BOTTOM_LINE_Y), BAR_LINE_WIDTH)
                }
            },
    ) {
        Box(Modifier.offset(y = StaffGeometry.NAMES_TOP.dp)) { PillBarLine() }
    }
}

/** A breath: no time, only a little room for the breath mark in the top space and the breathing face over the staff; no name. */
@Composable
private fun BreathMark(ink: StaffInk, measurer: TextMeasurer) {
    val description = stringResource(R.string.flute_breath_here)
    val face = staffText(measurer, BREATH_EMOJI, BREATH_FACE_SIZE)
    Spacer(
        modifier = Modifier
            .size(BREATH_SLOT.dp, TuneStaffHeight)
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                scale(density, pivot = Offset.Zero) {
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
