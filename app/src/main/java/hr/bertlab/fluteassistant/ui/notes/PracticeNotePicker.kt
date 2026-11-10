// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.dashedBorder
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.common.noteTint
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Go
import hr.bertlab.fluteassistant.ui.theme.InkOnFill

/**
 * The game's notes in rows by octave, highest on top as on the staff and the flute: c3
 * alone, then c2 to h2, then c1 to h1, left-aligned so the letters line up.
 */
private val OCTAVE_ROWS: List<List<Int>> = StaffNotes.ALL.chunked(7).reversed()

/**
 * The notes the game may ask, one pill each in [OCTAVE_ROWS]: the chosen ones a sticker in
 * their letter's colour that fills as the note is learned ([progress]), the rest dashed. A tap calls [onToggle]; the ViewModel keeps the
 * last note from being switched off. Under them one switch row for the sound of a found
 * note ([soundOn], [onToggleSound]): a setting, so it lives here rather than on the game
 * screen. Last, one big OK button ([onDone]) closes the picker: choices apply as they are
 * tapped, so OK only brings the game back.
 */
@Composable
fun PracticeNotePicker(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    progress: NoteProgress,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        NoteRows(selected = selected, onToggle = onToggle, progress = progress)
        SoundSettingRow(soundOn = soundOn, onToggleSound = onToggleSound)
        BigActionButton(
            icon = AppIcons.Done,
            text = stringResource(R.string.choose_notes_done),
            color = ActionGo,
            onClick = onDone,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * The notes in [OCTAVE_ROWS]: one pill each, the chosen ones a [NoteKey] of the sticker book (how far
 * each is learned, from [progress]), the rest dashed. The upright picker draws them 44 x 50 dp, 4 dp
 * apart; sideways the keys are bigger and fit the card. A row is its keys' height and the shadow under
 * them; the rows are one group for a screen reader. The rows do not clip, so a known key's star can
 * hang over the corner.
 */
@Composable
internal fun NoteRows(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    progress: NoteProgress,
    modifier: Modifier = Modifier,
    keyWidth: Dp = 44.dp,
    keyHeight: Dp = 50.dp,
    gap: Dp = 4.dp,
) {
    val colors = FluteTheme.colors
    val groupLabel = stringResource(R.string.practice_notes)
    val shape = keyShape(keyHeight)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.semantics { contentDescription = groupLabel },
    ) {
        OCTAVE_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.height(keyHeight + 4.dp)) {
                row.forEach { midi ->
                    if (midi in selected) {
                        NoteKey(
                            midi = midi,
                            progress = progress,
                            width = keyWidth,
                            height = keyHeight,
                            onClick = { onToggle(midi) },
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = keyWidth, height = keyHeight)
                                .dashedBorder(OutlineWidth / 2, colors.border, shape)
                                .clip(shape)
                                .toggleable(value = false, role = Role.Checkbox) { onToggle(midi) },
                        ) { PillLabel(midi, muted = true) }
                    }
                }
            }
        }
    }
}

/** The shape of every key of the rows, chosen or not: round at 0.4 of its height. */
private fun keyShape(height: Dp) = RoundedCornerShape(height * 0.4f)

/**
 * A chosen note as a sticker of the sticker book: the pale tint of its letter's colour, which the
 * colour itself fills from the bottom as far as the note is learned (nothing at 0, all of it at
 * [NoteProgress.KNOWN_AT]), and a star over the corner once it is known. The progress is a picture,
 * so no number is on the key; a screen reader hears the name, and "learned" after it once the star is
 * there. With [onClick] it is the picker's button, to switch the note off; without, a plain key in the
 * same look, for the summary. [grew] is a note that moved up this round: a sparkle over the other corner
 * and the trophy's hop.
 *
 * The star and the sparkle hang outside the outline, so they are drawn beside the key and not in it
 * ([chunky] clips what it holds to the key's shape): whatever holds a [NoteKey] must not clip it.
 */
@Composable
internal fun NoteKey(
    midi: Int,
    progress: NoteProgress,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 50.dp,
    grew: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = FluteTheme.colors
    val shape = keyShape(height)
    val letter = noteColor(midi)
    val tint = noteTint(midi)
    val share = progress.countOf(midi).coerceIn(0, NoteProgress.KNOWN_AT).toFloat() / NoteProgress.KNOWN_AT
    val known = progress.isKnown(midi)
    // Ink on a light ground, the theme's text on the dark tint of night. The name sits across the fill's edge while the
    // note is under way, so where the two grounds want different colours (night) it is drawn twice, each copy clipped to its ground.
    val onTint = labelOn(tint, colors.text)
    val onFill = labelOn(letter, colors.text)
    val oneColor = when {
        share <= 0f -> onTint
        share >= 1f || onFill == onTint -> onFill
        else -> null
    }
    // The star and the sparkle are pictures, in dp, so large text does not grow them past the room kept over a key.
    val corner = with(LocalDensity.current) { CornerEmoji.toSp().value.toInt() }
    // Under the name. The key itself clips to its shape, so the fill needs no clip of its own, and its outline is drawn over it.
    val inside: @Composable BoxScope.() -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    if (share > 0f) {
                        val filled = size.height * share
                        drawRect(letter, topLeft = Offset(0f, size.height - filled), size = Size(size.width, filled))
                    }
                },
        )
        if (oneColor != null) {
            PillLabel(midi, known = known, color = oneColor)
        } else {
            // The copy a screen reader and a test find is the tint's; the fill's is a picture only.
            ClippedToGround(share, onFill = false) { PillLabel(midi, known = known, color = onTint) }
            ClippedToGround(share, onFill = true) { PillLabel(midi, color = onFill, hidden = true) }
        }
    }
    val hop = if (grew) Modifier.trophyHop() else Modifier
    Box(modifier.size(width = width, height = height).then(hop)) {
        if (onClick != null) {
            ChunkyButton(
                onClick = onClick,
                checked = true,
                shape = shape,
                color = tint,
                modifier = Modifier.fillMaxSize(),
                content = inside,
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().chunky(shape, tint, colors.outline, KeyDepth),
                content = inside,
            )
        }
        if (known) {
            Emoji(STAR_EMOJI, fontSize = corner, modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-10).dp))
        }
        if (grew) {
            Emoji(SPARKLES_EMOJI, fontSize = corner, modifier = Modifier.align(Alignment.TopStart).offset(x = (-9).dp, y = (-10).dp))
        }
    }
}

/**
 * A key-sized box that draws [content], centred, only on one ground of the key: the filled part, the
 * lower [share] of the height, when [onFill], else the tint above it. Each box is the key's own size,
 * so the clip's edge is the fill's, whatever the size of the name inside.
 */
@Composable
private fun ClippedToGround(share: Float, onFill: Boolean, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val edge = size.height * (1f - share)
                if (onFill) {
                    clipRect(top = edge) { this@drawWithContent.drawContent() }
                } else {
                    clipRect(bottom = edge) { this@drawWithContent.drawContent() }
                }
            },
    ) { content() }
}

/** The colour of a key's name on [ground]: ink on a bright one, [text] (the theme's) on a dark one. */
private fun labelOn(ground: Color, text: Color): Color = if (ground.luminance() > LIGHT_GROUND) InkOnFill else text

/** The shadow under a key: what [ChunkyButton] has by default, for the plain key to match. */
private val KeyDepth = 4.dp

/** A ground lighter than this takes ink; a darker one the theme's own text colour. */
private const val LIGHT_GROUND = 0.2f

/** The size of a learned key's star and a grown key's sparkle, over its corners. */
private val CornerEmoji = 14.dp

/**
 * The note's name. [muted] for a key that is not chosen. A key that is [known] is described as
 * learned: the one description a key has, so a screen reader hears it once. A [hidden] copy (the
 * second colour of a half-filled key's name) has no semantics at all, in either tree.
 */
@Composable
private fun PillLabel(
    midi: Int,
    muted: Boolean = false,
    known: Boolean = false,
    color: Color = Color.Unspecified,
    hidden: Boolean = false,
) {
    val label = noteLabel(midi)
    val description = if (known) stringResource(R.string.note_known, label) else label
    Text(
        text = noteText(midi),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
        color = if (muted) FluteTheme.colors.mutedText else color,
        maxLines = 1,
        modifier = if (hidden) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description },
    )
}

/**
 * One rounded settings row, the shape every phone uses for an on/off choice: a speaker
 * (muted while off) and the word at the start, a chunky switch at the end. The whole row
 * toggles and is the one accessible node.
 */
@Composable
private fun SoundSettingRow(soundOn: Boolean, onToggleSound: () -> Unit) {
    val colors = FluteTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(colors.track)
            .border(OutlineWidth, colors.border, CircleShape)
            .toggleable(value = soundOn, role = Role.Switch, onValueChange = { onToggleSound() })
            .padding(start = 20.dp, end = 10.dp),
    ) {
        Emoji(if (soundOn) SPEAKER_EMOJI else MUTED_EMOJI, fontSize = 22)
        Text(
            text = stringResource(R.string.note_sound),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        )
        SoundSwitch(soundOn = soundOn, size = DpSize(76.dp, 44.dp), knob = 32.dp, inset = 4.dp)
    }
}

/**
 * The sound setting for the column of a phone on its side: the speaker and the word over a chunky
 * switch, in a tile as wide as the column. Like [SoundSettingRow] the whole tile toggles and is the
 * one accessible node.
 */
@Composable
internal fun SoundTile(soundOn: Boolean, onToggleSound: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(28.dp)
    // In dp: a floor in sp doubles with the text, and at the largest size "Sound" then has no room beside the speaker in the 100 dp column.
    val wordFloor = with(LocalDensity.current) { 9.dp.toSp() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(colors.track)
            .border(OutlineWidth, colors.border, shape)
            .toggleable(value = soundOn, role = Role.Switch, onValueChange = { onToggleSound() })
            .padding(horizontal = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Emoji(if (soundOn) SPEAKER_EMOJI else MUTED_EMOJI, fontSize = 20)
            Text(
                text = stringResource(R.string.note_sound),
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 1.25.em),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = wordFloor, maxFontSize = 19.sp),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        SoundSwitch(soundOn = soundOn, size = DpSize(58.dp, 32.dp), knob = 22.dp, inset = 2.dp)
    }
}

/** The chunky on/off switch of both sound settings: green with the knob at the end while on. */
@Composable
private fun SoundSwitch(soundOn: Boolean, size: DpSize, knob: Dp, inset: Dp) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = if (soundOn) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (soundOn) Go else colors.raised)
            .border(OutlineWidth, colors.outline, CircleShape)
            .padding(horizontal = inset),
    ) {
        Box(
            Modifier
                .size(knob)
                .clip(CircleShape)
                .background(colors.card)
                .border(OutlineWidth, colors.outline, CircleShape),
        )
    }
}

private const val SPEAKER_EMOJI = "🔊" // speaker, loud
private const val MUTED_EMOJI = "🔇" // speaker, muted
private const val SPARKLES_EMOJI = "✨" // sparkles, a note that moved up
