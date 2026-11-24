// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.domain.flute.JoinArc
import hr.bertlab.fluteassistant.domain.flute.Joins
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneWriter
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.HEAD_RY
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.yOf
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

// The slurs and ties over the staff's row, and the arcs under its name pills: where each stands and how it is drawn.
// The curves themselves are SlurPath.kt's.

/** Where a step's pieces stand along the row, in dp at rest from its first mark: each piece's head and its pill's middle. */
internal class StepPlace(val heads: List<Float>, val pills: List<Float>, val signed: Boolean = false)

internal fun stepPlaces(lineMarks: List<LineMark>, starts: List<Float>): Map<Int, StepPlace> =
    lineMarks.withIndex().filter { it.value is LineMark.Step }.associate { (at, mark) ->
        val step = mark as LineMark.Step
        val layout = StaffGeometry.layout(step)
        step.index to StepPlace(layout.heads.map { starts[at] + it }, layout.pills.map { starts[at] + it }, signed = step.sign != null)
    }

/**
 * The arcs over joined notes (since 2026-11-16, the ⌒ key, [Joins.arcs]): a slur under the heads in ink, a crescent
 * ([StaffGeometry.slurCurve]) passing under the heads and ties between its ends, with one muted arc under the name pills
 * of its notes, as a tie's pills are joined; a tie between two notes of one pitch drawn as the ties inside a note are;
 * and, while [joinWaiting], a dashed half arc running on from the last note, on the staff and under its pill. One layer
 * over the row, placed from the row's own layout at every frame as [WalkingAnimal] is, so an arc whose first note has
 * scrolled away is still drawn, and clipped at the row's sides only.
 */
@Composable
internal fun SlurLayer(
    tune: List<TuneItem>,
    lineMarks: List<LineMark>,
    starts: List<Float>,
    listState: LazyListState,
    joinWaiting: Boolean,
    ink: StaffInk,
    selectedIndex: Int? = null,
) {
    val arcs = remember(tune) { Joins.arcs(tune) }
    // From the last note, or, with a step chosen (since 2026-11-24), from the note before it.
    val waitingFrom = TuneWriter.joinFrom(tune, selectedIndex)?.takeIf { joinWaiting }
    val waitingOn = waitingFrom?.let { tune[it] as TuneNote }
    if (arcs.isEmpty() && waitingOn == null) return
    val s = LocalStaffScale.current
    val room = LocalStaffRoom.current
    val muted = FluteTheme.colors.mutedText
    // Built once per tune and look, not on every frame the row scrolls or the animal hops.
    val drawings = remember(tune, lineMarks, starts, waitingFrom, ink.line, muted) {
        val places = stepPlaces(lineMarks, starts)
        arcs.flatMap { joinArc(it, tune, places, ink.line, muted) } +
            (waitingOn?.let { note -> places[waitingFrom]?.let { waitingArc(it, note, ink.line, muted) } } ?: emptyList())
    }
    Box(
        Modifier
            .fillMaxSize()
            .clippedAtRowSides(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // The row's place is read here, so a scroll only moves this layer: the arcs are not drawn again, and
                // neither is the stage under them.
                .graphicsLayer {
                    val info = listState.layoutInfo
                    val first = info.visibleItemsInfo.firstOrNull()
                    val firstStart = first?.let { starts.getOrNull(it.index) }
                    alpha = if (firstStart == null) 0f else 1f
                    // The first item shown is the reference, as for the walking animal.
                    if (first != null && firstStart != null) translationX = info.beforeContentPadding + first.offset - firstStart * s * density
                }
                .drawBehind {
                    atStaff(s, room) {
                        for (drawing in drawings) drawPath(drawing.path, drawing.color, style = drawing.style)
                    }
                },
        )
    }
}

/**
 * Clips a layer over the row (the slurs, the walking animal) at the row's sides only: the animal's hop, a slur's
 * belly and the highest note's stem may reach above or below the box.
 */
internal fun Modifier.clippedAtRowSides(): Modifier =
    drawWithContent { clipRect(top = -size.height, bottom = size.height * 2) { this@drawWithContent.drawContent() } }

/** The y of a note's head on the staff. */
private fun headY(note: TuneNote): Float = yOf(FluteNotes.positionOf(note.midi))

/** The top of the arcs under the name pills, as a tie's pills have them. */
private const val PILL_ARC_TOP = StaffGeometry.NAMES_TOP + StaffGeometry.PILL_HEIGHT + StaffGeometry.PILL_TIE_GAP

/** One stroke or fill of the slur layer, in dp, made once and drawn on every frame. */
private class ArcDrawing(val path: Path, val color: Color, val style: DrawStyle = Fill)

private fun pillArc(from: Float, to: Float, color: Color) =
    ArcDrawing(pillArcPath(from, to, PILL_ARC_TOP), color, Stroke(2f, cap = StrokeCap.Round))

/** What one arc of [Joins.arcs] draws, in dp: a slur and its arc under the pills, or a tie between two steps of one note. */
private fun joinArc(arc: JoinArc, tune: List<TuneItem>, places: Map<Int, StepPlace>, ink: Color, muted: Color): List<ArcDrawing> {
    val from = places[arc.from] ?: return emptyList()
    val to = places[arc.to] ?: return emptyList()
    val first = tune[arc.from] as TuneNote
    if (arc.tie) {
        val tie = ArcDrawing(
            tiePath(from.heads.last(), to.heads.first(), headY(first)),
            noteColor(first.midi),
            Stroke(StaffGeometry.TIE_WIDTH, cap = StrokeCap.Round),
        )
        return listOf(tie, pillArc(from.pills.last(), to.pills.first(), muted))
    }
    return listOf(ArcDrawing(slurPath(slurFor(arc, tune, places)), ink), pillArc(from.pills.first(), to.pills.last(), muted))
}

/** The slur of [arc] (not a tie), from its first note's head to its last's, passing under what lies between. */
internal fun slurFor(arc: JoinArc, tune: List<TuneItem>, places: Map<Int, StepPlace>): StaffGeometry.SlurCurve {
    val from = places.getValue(arc.from)
    val to = places.getValue(arc.to)
    val first = tune[arc.from] as TuneNote
    val last = tune[arc.to] as TuneNote
    // Everything between the slur's two end heads it must pass under: the other heads, and the ties under them.
    val tieLow = StaffGeometry.TIE_LOW
    val clear = mutableListOf<Pair<Float, Float>>()
    for (index in arc.from..arc.to) {
        val note = tune[index] as TuneNote
        val place = places[index] ?: continue
        val y = headY(note)
        place.heads.forEachIndexed { k, x ->
            val end = (index == arc.from && k == 0) || (index == arc.to && k == place.heads.lastIndex)
            if (!end) clear += x to y + StaffGeometry.HEAD_RY
        }
        place.heads.zipWithNext().forEach { (a, b) -> clear += (a + b) / 2 to y + tieLow }
        // A sign before a head the slur reaches, its foot cleared by SIGN_SLUR_AIR rather than a head's SLUR_CLEAR
        // (the final review's look: a slur coming down onto F♯ ran through its sharp). The first note's sign is behind it.
        if (place.signed && index != arc.from) {
            val sign = place.heads.first() - StaffGeometry.SIGN_X
            val foot = y + StaffGeometry.SIGN_BELOW - StaffGeometry.SLUR_CLEAR + StaffGeometry.SIGN_SLUR_AIR
            for (x in listOf(sign - StaffGeometry.SIGN_HALF_WIDTH + 2f, sign + StaffGeometry.SIGN_HALF_WIDTH - 2f)) clear += x to foot
        }
        val next = places[index + 1]
        if (index < arc.to && next != null && (tune[index + 1] as TuneNote).midi == note.midi) {
            clear += (place.heads.last() + next.heads.first()) / 2 to y + tieLow
        }
    }
    return StaffGeometry.slurCurve(from.heads.first(), headY(first), to.heads.last(), headY(last), clear)
}

/** What the join waiting for its next note draws: a dashed half arc running on from the last note's head, and from under its pill. */
private fun waitingArc(place: StepPlace, note: TuneNote, ink: Color, muted: Color): List<ArcDrawing> {
    val dashes = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
    val x = place.heads.last() + StaffGeometry.SLUR_END_INSET
    val y = headY(note) + StaffGeometry.SLUR_END_DROP
    val staff = ArcDrawing(
        Path().apply {
            val l = StaffGeometry.WAITING_ARC_LENGTH
            moveTo(x, y)
            cubicTo(x + l * 0.3f, y + 7f, x + l * 0.7f, y + 8f, x + l, y + 6f)
        },
        ink,
        Stroke(2.2f, cap = StrokeCap.Round, pathEffect = dashes),
    )
    val pill = place.pills.last() + 3f
    val pills = ArcDrawing(
        Path().apply {
            val l = StaffGeometry.WAITING_ARC_LENGTH - 2f
            moveTo(pill, PILL_ARC_TOP)
            quadraticTo(pill + l * 0.45f, PILL_ARC_TOP + 8f, pill + l, PILL_ARC_TOP + 6f)
        },
        muted,
        Stroke(2f, cap = StrokeCap.Round, pathEffect = dashes),
    )
    return listOf(staff, pills)
}
