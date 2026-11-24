// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.Pitch
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.RETRY_STAR_ALPHA
import hr.bertlab.fluteassistant.ui.common.STAR_EMOJI
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill

// The marks' row under the names: what a step earned in "Your turn", the note heard instead, and the bubble while the
// phone listens.

/** The picture a mark is drawn with; a retry's star is the first try's, drawn faint ([RETRY_STAR_ALPHA]). */
private val StepMark.emoji: String
    get() = when (this) {
        StepMark.FIRST_TRY, StepMark.RETRY -> STAR_EMOJI
        StepMark.HIGH -> HIGH_EMOJI
        StepMark.LOW -> LOW_EMOJI
        StepMark.SHORT -> SHORT_EMOJI
        StepMark.LONG -> LONG_EMOJI
        StepMark.TONGUED -> TONGUED_EMOJI
    }

/** What a screen reader says a step earned. */
@get:StringRes
internal val StepMark.spoken: Int
    get() = when (this) {
        StepMark.FIRST_TRY -> R.string.flute_mark_first_try
        StepMark.RETRY -> R.string.flute_mark_retry
        StepMark.HIGH -> R.string.flute_mark_high
        StepMark.LOW -> R.string.flute_mark_low
        StepMark.SHORT -> R.string.flute_mark_short
        StepMark.LONG -> R.string.flute_mark_long
        StepMark.TONGUED -> R.string.flute_mark_tongued
    }

/**
 * The marks row of one step, in the first beat's room so what stands there stands under the head
 * and the name: under the step waited on, the note the phone hears right now ([heardNow]) as a
 * bubble, else the wrong note [heard] for it as a bubble, else, while [waiting] in silence, the
 * grey listening dots; otherwise the star the step earned ([mark], faint after a retry) or, since
 * 2026-11-14, the sign in its place for the right note played off (too high, too low, let go too
 * early), else nothing. A note sounding off its pitch ([pitch]) has its arrow just right of its
 * bubble, over the next step's marks room, which is empty then; the bubble does not move. Sized in
 * dp like the staff's other text, so large text never pushes it out of the row.
 */
@Composable
internal fun MarkRow(mark: StepMark?, heard: Int?, heardNow: Int?, pitch: Pitch?, waiting: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStaffScale.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size((StaffGeometry.BEAT_WIDTH * s).dp, (StaffGeometry.MARKS_HEIGHT * s).dp),
    ) {
        when {
            heardNow != null -> {
                HeardBubble(heardNow)
                if (pitch != null) PitchArrow(pitch)
            }
            heard != null -> HeardBubble(heard)
            waiting -> ListeningBubble()
            mark != null -> Emoji(
                mark.emoji,
                size = (StaffGeometry.MARK_SIZE * s).dp,
                modifier = if (mark == StepMark.RETRY) Modifier.alpha(RETRY_STAR_ALPHA) else Modifier,
            )
        }
    }
}

/**
 * A note heard for a step, under it in the marks row: the note the phone hears right now while
 * the animal waits over the step (the child sees the phone listening, and what it makes of their
 * sound, before anything is judged), or the wrong note last heard for it, left until the next
 * note is judged so there is time to read it: a round bubble as tall as the marks row, filled in
 * the heard letter's colour with its name, the way the tuner shows another note under the home
 * ring. The child reads "written G, heard A" from the pill and the bubble. Chosen on 2026-11-04
 * over the ❌ (which said nothing about what came out) and over the names in green (which doubled
 * the pill above and broke the one-colour-per-letter code); live since the same night, after a
 * child whose repeat went unheard saw nothing happen at all.
 */
@Composable
private fun HeardBubble(midi: Int) {
    // The name stands inside the outline, so the octave figure never meets it.
    MarkBubble(noteText(midi), fill = noteColor(midi), edge = FluteTheme.colors.outline, ink = InkOnFill, inset = OutlineWidth)
}

/** A round bubble in the marks' row under a step: [text] in [ink] on [fill] inside an [edge], the text [inset] from the edge. */
@Composable
private fun MarkBubble(text: AnnotatedString, fill: Color, edge: Color, ink: Color, inset: Dp = 0.dp) {
    val s = LocalStaffScale.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size((StaffGeometry.MARKS_HEIGHT * s).dp)
            .clip(CircleShape)
            .background(fill)
            .border(OutlineWidth, edge, CircleShape)
            .padding(horizontal = inset),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = (14 * s).sp),
        )
    }
}

/**
 * The arrow beside the live bubble of the right note sounding off its pitch ([Pitch.HIGH]: down, a little lower), its
 * middle just right of the bubble's edge, overlapping it by a dp as in the mockup the user picked from on 2026-11-14.
 */
@Composable
private fun PitchArrow(pitch: Pitch) {
    val s = LocalStaffScale.current
    val x = StaffGeometry.PITCH_ARROW_X
    Emoji(
        pitch.mark.emoji, // the arrow the note will earn if it stays there
        size = (StaffGeometry.PITCH_ARROW_SIZE * s).dp,
        modifier = Modifier.offset(x = (x * s).dp),
    )
}

/**
 * The phone listening and hearing nothing yet, under the step the animal waits over: the
 * [HeardBubble]'s shape in grey with "…", the tuner's home ring in silence, so the child sees the
 * phone is listening even before a sound reaches it.
 */
@Composable
private fun ListeningBubble() {
    val colors = FluteTheme.colors
    MarkBubble(AnnotatedString("…"), fill = colors.card, edge = colors.border, ink = colors.mutedText)
}


/**
 * The signs a step of "Your turn" earns in the star's place when the right note was played off (since 2026-11-14, the
 * user's pick "2C" and the hourglass): too high (and the arrow beside a note sounding high), too low, let go before it
 * was long enough. Drawn as the star is.
 */
private const val HIGH_EMOJI = "⬇️" // down arrow: a little lower
private const val LOW_EMOJI = "⬆️" // up arrow: a little higher
private const val SHORT_EMOJI = "⏳" // hourglass: hold it longer

/** The sign of a note held past its written length (since 2026-11-16, the user's pick "3A"), drawn as the others. */
private const val LONG_EMOJI = "✋" // raised hand: let go sooner
private const val TONGUED_EMOJI = "✂️" // scissors: a joined note started with the tongue (since 2026-11-16)
