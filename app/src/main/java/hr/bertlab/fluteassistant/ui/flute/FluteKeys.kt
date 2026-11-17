// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import hr.bertlab.fluteassistant.ui.common.ChunkyKey
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.KeyDepth
import hr.bertlab.fluteassistant.ui.common.KeyGap
import hr.bertlab.fluteassistant.ui.common.KeyHeight
import hr.bertlab.fluteassistant.ui.common.KeyName
import hr.bertlab.fluteassistant.ui.common.SharpFlatKey
import hr.bertlab.fluteassistant.ui.common.TapKey
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val KeyRowGap = 12.dp

/** The keys' height when the screen is too short to give them more: three rows at their minimum. */
val FluteKeysMinHeight: Dp = (KeyHeight + KeyDepth) * 3 + KeyRowGap * 2

/** The breath, rest and erase emoji, in dp so a large font scale cannot push the breath emoji out of a 40 dp key. */
private const val TOOL_EMOJI_DP = 24f

/** The plain keys at the end of the top row: join, rest, breath, erase. */
private const val ToolKeyCount = 4

/** The ♯♭ key beside C6 (since 2026-11-17), one note key wide, in the top row's second column. */
private const val SharpFlatKeyCount = 1

/** The ⌒ key's picture: the staff's own slur ([slurPath]), from (2, 3) to (28, 3) dp in a box this size. */
private val JoinPictureWidth = 30.dp
private val JoinPictureHeight = 16.dp
private val JoinPicture by lazy { slurPath(StaffGeometry.slurCurve(1f, 3f - StaffGeometry.SLUR_END_DROP, 29f, 3f - StaffGeometry.SLUR_END_DROP, emptyList())) }

private const val SPONGE_EMOJI = "\uD83E\uDDFD" // sponge

/** After this long held, the erase key starts repeating, then every [EraseRepeatMs]: a phone keyboard's backspace. */
private const val EraseRepeatDelayMs = 400L
private const val EraseRepeatMs = 120L

/**
 * The fifteen keys ([StaffNotes.ALL]) in three rows, one per octave, highest on top as on
 * the staff (c3 alone, then c2 to h2, then c1 to h1), each key in its letter's colour with
 * its name; the key of [litMidi] (E's key for an E♭) sinks onto its shadow inside a yellow ring. While
 * [signWaiting] each key shows the black note it will write, or is faded when it writes none ([FluteKey]). A finger down
 * calls [FluteActions.onKeyPressed] at once and the finger lifting, or sliding off, calls [FluteActions.onKeyReleased]; sliding
 * onto another key does not press it, so every note in the tune is a deliberate tap. A
 * screen reader activates a key through [FluteActions.onKeyActivated]. Every row is [keyHeight] tall, [KeyHeight] as
 * on the canvas unless the phone is on its side, and the three rows sit centred in the height they
 * are given. A key's width comes from the width given, less [keyGap] between each two keys.
 *
 * Beside c3 stands the ♯♭ key ([SharpFlatKey]), over d2, which calls [FluteActions.onSign] as the finger goes down and
 * stays down while [signWaiting]; a slip off c3 lands on it. At the end of the top row, after one empty column, stand
 * four plain keys, each one note key wide and over a note key of the row below (f2, g2, a2, h2), so a tool is never
 * bigger than a note:
 * - the join key (⌒, its picture the slur it draws), which calls [FluteActions.onJoin] as the finger goes down, stays
 *   down while [joinWaiting] and is faded and inert unless [joinEnabled];
 * - the rest key (💤), held like a note key ([FluteActions.onRestPressed], [FluteActions.onRestReleased],
 *   [FluteActions.onRestActivated]) and sunk while [restLit];
 * - the breath key (😮‍💨), which calls [FluteActions.onBreath] as the finger goes down;
 * - the erase key (🧽), which calls [FluteActions.onEraseLast] as the finger goes down and again and again while it
 *   stays, like a keyboard's backspace, faded and inert while [eraseEnabled] is false.
 *
 * When each key came and where it stood before is in `DESIGN.md`, "The keys".
 */
@Composable
fun FluteKeys(
    litMidi: Int?,
    restLit: Boolean,
    eraseEnabled: Boolean,
    actions: FluteActions,
    modifier: Modifier = Modifier,
    keyHeight: Dp = KeyHeight,
    keyGap: Dp = KeyGap,
    joinWaiting: Boolean = false,
    joinEnabled: Boolean = false,
    signWaiting: Boolean = false,
) {
    val groupLabel = stringResource(R.string.flute_keys)
    val rows = remember { StaffNotes.ALL.groupBy { Math.floorDiv(it, 12) }.toSortedMap().values.reversed() }
    val keysPerRow = remember(rows) { rows.maxOf { it.size } }
    BoxWithConstraints(modifier = modifier) {
        // One note key's width, in whole pixels: seven keys that each rounded up ran 4 px over the
        // row on a 420 dpi phone, so the rows are measured in pixels and centred, and the top row's
        // tools stand exactly over the columns below (the rows end up to 6 px short, split between
        // the sides).
        val keyWidth = with(LocalDensity.current) {
            ((constraints.maxWidth - keyGap.roundToPx() * (keysPerRow - 1)) / keysPerRow).toDp()
        }
        val key = Modifier.width(keyWidth).fillMaxHeight() // every key of every row: one column wide, as tall as its row
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = groupLabel },
            verticalArrangement = Arrangement.spacedBy(KeyRowGap, Alignment.CenterVertically),
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keyHeight + KeyDepth)
                        .padding(bottom = KeyDepth),
                    horizontalArrangement = Arrangement.spacedBy(keyGap, Alignment.CenterHorizontally),
                ) {
                    row.forEach { midi ->
                        FluteKey(
                            midi = midi,
                            // The key of the note held or playing: E's key for an E♭, as the white key wrote it.
                            lit = litMidi != null && midi == NoteSpelling.naturalOf(litMidi),
                            signWaiting = signWaiting,
                            onPress = actions.onKeyPressed,
                            onRelease = actions.onKeyReleased,
                            onActivate = actions.onKeyActivated,
                            modifier = key,
                        )
                    }
                    if (row.size < keysPerRow) {
                        // The short top row: c3, the ♯♭ key, the empty middle, then the join, the rest, the
                        // breath and the erase key, each one note key wide. The middle is as wide as the key
                        // columns it covers, so the row measures as the full rows do and the four stand
                        // over the last four keys of the row below, the sponge ending where h2 does.
                        SharpFlatKey(
                            waiting = signWaiting,
                            onSign = actions.onSign,
                            modifier = key,
                        )
                        val emptyColumns = keysPerRow - row.size - SharpFlatKeyCount - ToolKeyCount
                        if (emptyColumns > 0) {
                            Spacer(Modifier.width(keyWidth * emptyColumns + keyGap * (emptyColumns - 1)))
                        }
                        JoinKey(
                            waiting = joinWaiting,
                            enabled = joinEnabled,
                            onJoin = actions.onJoin,
                            modifier = key,
                        )
                        RestKey(
                            lit = restLit,
                            onPress = actions.onRestPressed,
                            onRelease = actions.onRestReleased,
                            onActivate = actions.onRestActivated,
                            modifier = key,
                        )
                        BreathKey(
                            onBreath = actions.onBreath,
                            modifier = key,
                        )
                        EraseKey(
                            enabled = eraseEnabled,
                            onErase = actions.onEraseLast,
                            modifier = key,
                        )
                    }
                }
            }
        }
    }
}

/** The rest key: a plain key with 💤, held like a note key, the rest growing while it is down. */
@Composable
private fun RestKey(
    lit: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.flute_rest)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    ToolKey(
        label = label,
        pressed = lit,
        onActivate = onActivate,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    currentOnPress()
                    try {
                        tryAwaitRelease() // up, or the finger slid off: both let go of the key
                    } finally {
                        currentOnRelease() // as does the key leaving the screen (see FluteKey)
                    }
                },
            )
        },
    ) { ToolEmoji(SLEEP_EMOJI) }
}

/**
 * The join key (since 2026-11-16): a plain key with the slur it draws, the staff's own crescent. A finger going down
 * joins the next note to the last one, or cancels a join waiting ([FluteActions.onJoin]); while one waits the key stays
 * down in its yellow ring and a screen reader hears it selected. Faded and inert with nothing to join from.
 */
@Composable
private fun JoinKey(waiting: Boolean, enabled: Boolean, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    val ink = FluteTheme.colors.text
    TapKey(stringResource(R.string.flute_join), down = waiting, onTap = onJoin, modifier = modifier, enabled = enabled) {
        Canvas(Modifier.size(JoinPictureWidth, JoinPictureHeight)) {
            scale(density, pivot = Offset.Zero) {
                drawPath(JoinPicture, ink)
                drawPath(JoinPicture, ink, style = Stroke(1.5f, join = StrokeJoin.Round))
            }
        }
    }
}

/** The breath key: a plain key with 😮‍💨 that marks a breath as the finger goes down and sinks while it stays. */
@Composable
private fun BreathKey(onBreath: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.flute_breath)
    val currentOnBreath by rememberUpdatedState(onBreath)
    var pressed by remember { mutableStateOf(false) }
    ToolKey(
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
    ) { ToolEmoji(BREATH_EMOJI) }
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
    ToolKey(
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
    ) { ToolEmoji(SPONGE_EMOJI) }
}

/**
 * A plain (not a note's colour) key showing [content] (an emoji, [ToolEmoji]), named [label] for a screen reader; sunk
 * in a yellow ring while [pressed]; faded, and refusing [onActivate], while [enabled] is false ([ChunkyKey]).
 */
@Composable
private fun ToolKey(
    label: String,
    pressed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    ChunkyKey(label, FluteTheme.colors.raised, down = pressed, onActivate = onActivate, modifier = modifier, enabled = enabled, content = content)
}

/** A tool key's emoji, at the size every tool key shows its own. */
@Composable
private fun ToolEmoji(emoji: String) = Emoji(emoji, fontSize = emojiSizeFor(TOOL_EMOJI_DP))


/**
 * A note key: the white key [midi], in its letter's colour. While the ♯♭ key waits ([signWaiting], since 2026-11-17) its
 * face and name are the black note it writes ([FluteNotes.signed]), or it is faded and a screen reader hears it disabled
 * when it writes none (D, A, C6). It always reports [midi], the white key, and its gesture is keyed on that alone: the
 * face changes under a finger the moment a press spends the sign, and a finger already on D when the sign is tapped
 * holds its note on, so neither may restart the gesture (whose `finally` would let the key go). The view model ignores
 * a press of a key that writes nothing.
 */
@Composable
private fun FluteKey(
    midi: Int,
    lit: Boolean,
    signWaiting: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val face = FluteNotes.written(midi, signWaiting)
    val writes = face != null
    // The gesture block is keyed on the key alone, so it reads the latest callbacks through state.
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    ChunkyKey(
        label = noteLabel(face ?: midi),
        color = noteColor(midi),
        down = lit,
        onActivate = { onActivate(midi) },
        enabled = writes,
        modifier = modifier.pointerInput(midi) {
            detectTapGestures(
                onPress = {
                    currentOnPress(midi)
                    try {
                        tryAwaitRelease() // up, or the finger slid off: both let go of the key
                    } finally {
                        // Turning the phone takes the key off the screen and cancels the gesture
                        // here, with no up to come; the Activity lives on, so nothing else would
                        // end the note. The ViewModel ignores a key that is no longer the one held.
                        currentOnRelease(midi)
                    }
                },
            )
        },
    ) {
        if (face != null && face != midi) {
            // A signed name is longer ("gis¹", "C♯⁴"): it shrinks to fit on one line, as the black keys' names did.
            KeyName(noteText(face), maxSize = 22.sp)
        } else {
            Text(noteText(face ?: midi), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = InkOnFill, maxLines = 1)
        }
    }
}
