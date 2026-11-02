package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.dashedBorder
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteLabelParts
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun

/** The line's height: a row of note pills, each with its length's symbol under it and room for a tie under that. */
val TuneLineHeight = 70.dp

private val PillHeight = 36.dp
private val PillGap = 4.dp
private val SymbolGap = 4.dp
private val SymbolHeight = 18.dp

/** A pill with its symbol under it: the height a bar line spans. */
private val MarkHeight = PillHeight + SymbolGap + SymbolHeight
private val BarLineWidth = 3.dp
private val BarLineMargin = 3.dp

/** How far a tie dips below the note symbols it joins. */
private val TieDip = 6.dp

/** A pill's width per beat; a longer note also covers the gaps between its beats. */
private val BeatWidth = 36.dp
private val EighthWidth = 20.dp

/** How wide a note of [length] is drawn: longer pill = longer note. */
private fun pillWidth(length: NoteLength): Dp =
    if (length == NoteLength.EIGHTH) EighthWidth else BeatWidth * length.wholeBeats + PillGap * (length.wholeBeats - 1)

/**
 * The child's tune as a line of pills written in bars of [timeSignature] ([Bars.write]), the
 * time signature itself standing at the left end while the pills scroll. Each note is a pill
 * in its letter's colour as wide as the note is long ([pillWidth]), with the length's note
 * symbol faint under it (a hint, not a control); a rest is a pill of the same width in the
 * quiet track colour with 💤, its rest symbol under it, and a breath takes no time, so it is no
 * pill: the breathing face over the breath mark. A bar line follows every full bar; a note
 * that runs past one is drawn as pieces on either side of it, joined by a tie under their
 * symbols, and a rest likewise without the tie. The step at [litIndex] (sounding now, or held
 * and still growing) is ringed in yellow, every piece of it. The line scrolls sideways: it
 * follows the newest step and the playing one, and the child can drag it back to look. While
 * [tune] is empty it shows one hint in a dashed pill instead, at the same height, so nothing
 * below moves when the first note comes.
 */
@Composable
fun TuneLine(
    tune: List<TuneItem>,
    litIndex: Int?,
    timeSignature: TimeSignature,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .height(TuneLineHeight),
    ) {
        TimeSignatureMark(timeSignature)
        if (tune.isEmpty()) {
            EmptyHint(Modifier.weight(1f))
        } else {
            TuneMarks(tune, litIndex, timeSignature, Modifier.weight(1f))
        }
    }
}

/** The dashed pill with the hint before the first note. */
@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    Box(modifier = modifier.padding(top = 2.dp).height(MarkHeight), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .dashedBorder(OutlineWidth / 2, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.flute_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.mutedText,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** The time signature as written at the start of a staff, its two figures stacked, with a dashed line after it. */
@Composable
private fun TimeSignatureMark(signature: TimeSignature) {
    val colors = FluteTheme.colors
    val description = stringResource(R.string.flute_time_signature, signature.beatsPerBar, signature.noteValue)
    val style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(top = 2.dp)
            .height(MarkHeight)
            .drawBehind {
                val x = size.width - 1.dp.toPx()
                drawLine(
                    colors.border,
                    Offset(x, 4.dp.toPx()),
                    Offset(x, size.height - 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            .padding(start = 2.dp, end = 10.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(signature.beatsPerBar.toString(), style = style, color = colors.text)
        Text(signature.noteValue.toString(), style = style, color = colors.text)
    }
}

/** The scrolling row of what [Bars.write] makes of [tune]. */
@Composable
private fun TuneMarks(tune: List<TuneItem>, litIndex: Int?, signature: TimeSignature, modifier: Modifier = Modifier) {
    val notes = tune.count { it is TuneNote }
    val description = pluralStringResource(R.plurals.flute_tune_line, notes, notes)
    val marks = remember(tune, signature) { Bars.write(tune, signature) }
    // The lit step's mark, else the line's end (a bar line, once a bar is full).
    val target = marks.indexOfFirst { it is LineMark.Step && it.index == litIndex }.takeIf { it >= 0 } ?: marks.lastIndex
    val listState = rememberLazyListState()
    // Keyed on the marks themselves, so a held note that grows (or splits at a bar line) stays in view.
    LaunchedEffect(target, marks) {
        listState.animateScrollToItem(target)
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(PillGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        items(marks) { mark ->
            when (mark) {
                is LineMark.Step -> StepMark(item = tune[mark.index], parts = mark.parts, lit = mark.index == litIndex)
                is LineMark.Breath -> BreathMark()
                LineMark.BarLine -> BarLineMark()
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

/**
 * One note or rest as written: its [parts], a piece per length with a bar line between two of
 * them where it crosses one, a note's pieces tied under their symbols. A screen reader hears it
 * once, by its whole length.
 */
@Composable
private fun StepMark(item: TuneItem, parts: List<StepPart>, lit: Boolean) {
    val colors = FluteTheme.colors
    val description = when (item) {
        is TuneNote -> lengthDescription(noteLabel(item.midi), item.length)
        is TuneRest -> lengthDescription(stringResource(R.string.flute_rest), item.length)
        TuneBreath -> stringResource(R.string.flute_breath_here)
    }
    val tied = item is TuneNote && parts.count { it is StepPart.Length } > 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(PillGap),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .then(if (tied) Modifier.drawBehind { drawTies(parts, colors.mutedText) } else Modifier)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        for (part in parts) {
            when (part) {
                is StepPart.Length -> when (item) {
                    is TuneNote -> NotePiece(midi = item.midi, length = part.length, lit = lit)
                    else -> RestPiece(length = part.length, lit = lit)
                }
                LineMark.BarLine -> BarLineMark()
            }
        }
    }
}

/**
 * The ties between the pieces of one note, laid out as [StepMark] lays them (fixed widths, a
 * [PillGap] between): an arc under the symbols from each piece's middle to the next one's.
 */
private fun DrawScope.drawTies(parts: List<StepPart>, color: Color) {
    val middles = mutableListOf<Float>()
    var x = 0f
    for ((i, part) in parts.withIndex()) {
        if (i > 0) x += PillGap.toPx()
        val width = when (part) {
            is StepPart.Length -> pillWidth(part.length).toPx()
            LineMark.BarLine -> (BarLineWidth + BarLineMargin * 2).toPx()
        }
        if (part is StepPart.Length) middles += x + width / 2
        x += width
    }
    val top = MarkHeight.toPx() + 1.dp.toPx()
    val inset = 3.dp.toPx()
    for ((from, to) in middles.zipWithNext()) {
        drawPath(
            Path().apply {
                moveTo(from + inset, top)
                quadraticTo((from + to) / 2, top + TieDip.toPx() * 2, to - inset, top)
            },
            color,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** A bar line, as tall as a pill with its symbol, in the text colour so it shows on the dark card too. */
@Composable
private fun BarLineMark() {
    Box(
        modifier = Modifier
            .padding(horizontal = BarLineMargin)
            .size(BarLineWidth, MarkHeight)
            .clip(RoundedCornerShape(BarLineWidth / 2))
            .background(FluteTheme.colors.text), // the ink in light mode; the outline colour would vanish on the dark card
    )
}

/** One piece of a note: a pill in its letter's colour as wide as [length], and the length's symbol under it. */
@Composable
private fun NotePiece(midi: Int, length: NoteLength, lit: Boolean) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(PillHeight / 2)
    val longNote = length.halfBeats > NoteLength.QUARTER.halfBeats
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SymbolGap),
    ) {
        Box(
            contentAlignment = if (longNote) Alignment.CenterStart else Alignment.Center,
            modifier = Modifier
                .size(pillWidth(length), PillHeight)
                .drawBehind { if (lit) drawRing() }
                .clip(shape)
                .background(noteColor(midi))
                .border(OutlineWidth, colors.outline, shape)
                .padding(horizontal = if (longNote) 9.dp else 0.dp),
        ) {
            val style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp)
            if (length == NoteLength.EIGHTH) {
                // Too narrow for the octave figure: the letter alone.
                Text(noteLabelParts(LocalResources.current, midi).first, style = style, color = InkOnFill, maxLines = 1)
            } else {
                Text(noteText(midi), style = style, color = InkOnFill, maxLines = 1)
            }
        }
        LengthSymbol(length, colors.mutedText)
    }
}

/** One piece of a rest: a quiet pill as wide as a note of [length] with 💤 in it, and the rest's symbol under it. */
@Composable
private fun RestPiece(length: NoteLength, lit: Boolean) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(PillHeight / 2)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SymbolGap),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(pillWidth(length), PillHeight)
                .drawBehind { if (lit) drawRing() }
                .clip(shape)
                .background(colors.track)
                .border(OutlineWidth, colors.outline, shape),
        ) {
            Emoji(SLEEP_EMOJI, fontSize = if (length == NoteLength.EIGHTH) 11 else 16)
        }
        RestSymbol(length, colors.mutedText)
    }
}

/** A breath: no time, so no pill; the breathing face where a pill would be, over the breath mark. */
@Composable
private fun BreathMark() {
    val colors = FluteTheme.colors
    val description = stringResource(R.string.flute_breath_here)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SymbolGap),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(PillHeight)) {
            Emoji(BREATH_EMOJI, fontSize = 18)
        }
        BreathSymbol(colors.mutedText)
    }
}

/** The rest key's sign, and a rest's in the line and the bubble. */
internal const val SLEEP_EMOJI = "\uD83D\uDCA4" // zzz
/** The breath key's sign, and a breath's in the line and the bubble. */
internal const val BREATH_EMOJI = "\uD83D\uDE2E\u200D\uD83D\uDCA8" // face exhaling

/** The yellow ring round the lit pill, a little outside it. */
private fun DrawScope.drawRing() {
    val stroke = 4.dp.toPx()
    val outset = 2.dp.toPx() + stroke / 2
    drawRoundRect(
        color = Sun,
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius(size.height / 2 + outset),
        style = Stroke(stroke),
    )
}

/**
 * The note symbol for [length], small and faint, drawn in an 18 x 20 box like the canvas's:
 * a filled head with a flag (eighth), a filled head (quarter), an open head (half), an open
 * head with a dot (dotted half), an open head without a stem (whole).
 */
@Composable
private fun LengthSymbol(length: NoteLength, color: Color) {
    Canvas(Modifier.size(16.dp, 18.dp)) {
        scale(size.width / 18f, size.height / 20f, pivot = Offset.Zero) {
            val line = 2f
            if (length == NoteLength.WHOLE) {
                rotate(-20f, pivot = Offset(9f, 12f)) {
                    drawOval(color, topLeft = Offset(3.4f, 8.2f), size = Size(11.2f, 7.6f), style = Stroke(2.2f))
                }
                return@scale
            }
            val filled = length == NoteLength.EIGHTH || length == NoteLength.QUARTER
            rotate(-20f, pivot = Offset(8.5f, 15f)) {
                val head = Offset(3.7f, 11.4f)
                val headSize = Size(9.6f, 7.2f)
                if (filled) drawOval(color, topLeft = head, size = headSize)
                drawOval(color, topLeft = head, size = headSize, style = Stroke(line))
            }
            drawLine(color, Offset(12.5f, 13.5f), Offset(12.5f, 3f), strokeWidth = line, cap = StrokeCap.Round)
            when (length) {
                NoteLength.EIGHTH -> drawPath(
                    Path().apply {
                        moveTo(12.5f, 3f)
                        cubicTo(13f, 6f, 17.5f, 6.5f, 16f, 11f)
                    },
                    color,
                    style = Stroke(line, cap = StrokeCap.Round),
                )
                NoteLength.DOTTED_HALF -> drawCircle(color, radius = 1.7f, center = Offset(16.4f, 16f))
                else -> Unit
            }
        }
    }
}

/**
 * The rest symbol for [length], small and faint, in the same 18 x 20 box as [LengthSymbol]:
 * a dot with a slanting stem (eighth), the zigzag (quarter), a block sitting on a line (half,
 * with a dot for the dotted half) and a block hanging from a line (whole).
 */
@Composable
private fun RestSymbol(length: NoteLength, color: Color) {
    Canvas(Modifier.size(16.dp, 18.dp)) {
        scale(size.width / 18f, size.height / 20f, pivot = Offset.Zero) {
            val line = 2f
            when (length) {
                NoteLength.EIGHTH -> {
                    drawCircle(color, radius = 2.4f, center = Offset(6.5f, 6.5f))
                    drawPath(
                        Path().apply {
                            moveTo(6.5f, 8.5f)
                            quadraticTo(9.5f, 9.5f, 13f, 5.5f)
                            lineTo(8.5f, 18f)
                        },
                        color,
                        style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                NoteLength.QUARTER -> drawPath(
                    Path().apply {
                        moveTo(7f, 2f)
                        lineTo(11.5f, 7f)
                        lineTo(7.5f, 10.5f)
                        lineTo(11.5f, 15f)
                        quadraticTo(6f, 13.5f, 8.5f, 18.5f)
                    },
                    color,
                    style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                NoteLength.HALF -> {
                    drawLine(color, Offset(1.5f, 13f), Offset(16.5f, 13f), strokeWidth = 1.6f)
                    drawRect(color, topLeft = Offset(5f, 8f), size = Size(8f, 5f))
                }
                NoteLength.DOTTED_HALF -> {
                    drawLine(color, Offset(0.5f, 13f), Offset(13f, 13f), strokeWidth = 1.6f)
                    drawRect(color, topLeft = Offset(2.5f, 8f), size = Size(7f, 5f))
                    drawCircle(color, radius = 1.7f, center = Offset(15.5f, 11f))
                }
                NoteLength.WHOLE -> {
                    drawLine(color, Offset(1.5f, 7f), Offset(16.5f, 7f), strokeWidth = 1.6f)
                    drawRect(color, topLeft = Offset(5f, 7f), size = Size(8f, 5f))
                }
            }
        }
    }
}

/** The breath mark, a comma as written above the staff, small and faint like [RestSymbol]. */
@Composable
private fun BreathSymbol(color: Color) {
    Canvas(Modifier.size(16.dp, 18.dp)) {
        scale(size.width / 18f, size.height / 20f, pivot = Offset.Zero) {
            drawCircle(color, radius = 3f, center = Offset(9f, 6f))
            drawPath(
                Path().apply {
                    moveTo(11.6f, 6.5f)
                    quadraticTo(11.5f, 11.5f, 7f, 14f)
                },
                color,
                style = Stroke(2f, cap = StrokeCap.Round),
            )
        }
    }
}
