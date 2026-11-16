// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.fadedUnless
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A key's height: a sure target for a small finger, and the canvas's 58 at every screen height
 * (the user, 2026-11-02: keys as on the canvas; they used to stretch to 72 dp to fill the space).
 */
internal val KeyHeight = 58.dp
private val KeyRowGap = 12.dp
internal val KeyGap = 6.dp
private val KeyShape = RoundedCornerShape(21.dp)
private val KeyDepth = 5.dp

/** The keys' height when the screen is too short to give them more: three rows at their minimum. */
val FluteKeysMinHeight: Dp = (KeyHeight + KeyDepth) * 3 + KeyRowGap * 2

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
 * The fifteen keys ([StaffNotes.ALL]) in three rows, one per octave, highest on top as on
 * the staff (c3 alone, then c2 to h2, then c1 to h1), each key in its letter's colour with
 * its name; the key of [litMidi] sinks onto its shadow inside a yellow ring. A finger down
 * calls [FluteActions.onKeyPressed] at once and the finger lifting, or sliding off, calls [FluteActions.onKeyReleased]; sliding
 * onto another key does not press it, so every note in the tune is a deliberate tap. A
 * screen reader activates a key through [FluteActions.onKeyActivated]. Every row is [keyHeight] tall, [KeyHeight] as
 * on the canvas unless the phone is on its side, and the three rows sit centred in the height they
 * are given. A key's width comes from the width given, less [keyGap] between each two keys.
 *
 * At the end of the top row, apart from c3 with two empty columns between, stand four plain keys,
 * each one note key wide and over a note key of the row below (f2, g2, a2, h2), so a tool is never
 * bigger than a note and a slip off c3 lands on nothing (since 2026-11-05; before that they were two
 * keys wide each and filled the row): the join key (⌒, since 2026-11-16, at the user's request, the
 * picture the slur it draws), which calls [FluteActions.onJoin] as the finger goes down, stays down
 * while [joinWaiting] and is faded and inert unless [joinEnabled]; the rest key (💤), held like a note key ([FluteActions.onRestPressed],
 * [FluteActions.onRestReleased], [FluteActions.onRestActivated]) and sunk while [restLit], the breath key (😮‍💨), which calls
 * [FluteActions.onBreath] as the finger goes down (the rest before the breath since 2026-11-05, at the user's
 * request; the other way round before), and the erase key (🧽), which calls [FluteActions.onEraseLast] as the
 * finger goes down and again and again while it stays, like a keyboard's backspace, faded and inert
 * while [eraseEnabled] is false (in the key row since 2026-11-04, at the user's request; it was a
 * round button at the end of the Play row).
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
                            lit = midi == litMidi,
                            onPress = actions.onKeyPressed,
                            onRelease = actions.onKeyReleased,
                            onActivate = actions.onKeyActivated,
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight(),
                        )
                    }
                    if (row.size < keysPerRow) {
                        // The short top row: c3, the empty middle, then the join, the rest, the breath
                        // and the erase key, each one note key wide. The middle is as wide as the key
                        // columns it covers, so the row measures as the full rows do and the four stand
                        // over the last four keys of the row below, the sponge ending where h2 does.
                        val emptyColumns = keysPerRow - row.size - SignKeyCount
                        if (emptyColumns > 0) {
                            Spacer(Modifier.width(keyWidth * emptyColumns + keyGap * (emptyColumns - 1)))
                        }
                        JoinKey(
                            waiting = joinWaiting,
                            enabled = joinEnabled,
                            onJoin = actions.onJoin,
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight(),
                        )
                        RestKey(
                            lit = restLit,
                            onPress = actions.onRestPressed,
                            onRelease = actions.onRestReleased,
                            onActivate = actions.onRestActivated,
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight(),
                        )
                        BreathKey(
                            onBreath = actions.onBreath,
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight(),
                        )
                        EraseKey(
                            enabled = eraseEnabled,
                            onErase = actions.onEraseLast,
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight(),
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
    SignKey(
        emoji = SLEEP_EMOJI,
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

/** The yellow ring around a pressed key, a little outside its outline. */
private fun DrawScope.drawKeyRing() {
    val gap = 2.dp.toPx() + 2.5.dp.toPx()
    drawRoundRect(
        color = Sun,
        topLeft = Offset(-gap, -gap + 3.dp.toPx()),
        size = Size(size.width + gap * 2, size.height + gap * 2),
        cornerRadius = CornerRadius(21.dp.toPx() + gap),
        style = Stroke(5.dp.toPx()),
    )
}

@Composable
private fun FluteKey(
    midi: Int,
    lit: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = noteLabel(midi)
    val colors = FluteTheme.colors
    // The gesture block is keyed on the note alone, so it reads the latest callbacks through state.
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
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
            .drawBehind { if (lit) drawKeyRing() }
            .chunky(KeyShape, noteColor(midi), colors.outline, KeyDepth, pressed = lit)
            .pointerInput(midi) {
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
        contentAlignment = Alignment.Center,
    ) {
        Text(noteText(midi), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = InkOnFill, maxLines = 1)
    }
}
