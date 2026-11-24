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
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.ui.common.ChunkyKey
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.KeyDepth
import hr.bertlab.fluteassistant.ui.common.KeyGap
import hr.bertlab.fluteassistant.ui.common.KeyHeight
import hr.bertlab.fluteassistant.ui.common.KeyName
import hr.bertlab.fluteassistant.ui.common.SharpFlatKey
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.TapKey
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteLabelParts
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val KeyRowGap = SidewaysGeometry.KEY_ROW_GAPS.dp

/**
 * The octave pills over the letters (since 2026-11-18): a pill, its shadow, and the room under it that is still its touch
 * area. 40 dp tall with a name at 22 since 2026-11-19 (32 and 17 for a day: the user, "very hard to read", who gave them
 * the room from the staff's box).
 */
private val PillHeight = 40.dp
private val PillDepth = 3.dp
private val PillsHeight = PillHeight + PillDepth + 8.dp
private val PillGap = 8.dp
private val PillShape = RoundedCornerShape(20.dp)
private val PillNameSize = 22.dp

/** The least a pill's name shrinks to, in dp, on the narrowest keys' column at the largest text. */
private val PillNameFloor = 10.dp

/** The keys' height when the screen is too short to give them more: the pills and two rows at their minimum (three rows until 2026-11-18). */
val FluteKeysMinHeight: Dp = PillsHeight + (KeyHeight + KeyDepth) * 2 + KeyRowGap

/** Seven keys a row: the letters, and under them the tools in five of the seven columns. */
private const val KeysPerRow = 7

/** The breath, rest and erase emoji, in dp so a large font scale cannot push the breath emoji out of a 40 dp key. */
private val ToolEmojiSize = 24.dp

/** The ⌒ key's picture: the staff's own slur ([slurPath]), from (2, 3) to (28, 3) dp in a box this size. */
private val JoinPictureWidth = 30.dp
private val JoinPictureHeight = 16.dp
private val JoinPicture by lazy { slurPath(StaffGeometry.slurCurve(1f, 3f - StaffGeometry.SLUR_END_DROP, 29f, 3f - StaffGeometry.SLUR_END_DROP, emptyList())) }

private const val SPONGE_EMOJI = "\uD83E\uDDFD" // sponge

/** After this long held, the erase key starts repeating, then every [EraseRepeatMs]: a phone keyboard's backspace. */
private const val EraseRepeatDelayMs = 400L
private const val EraseRepeatMs = 120L

/**
 * The flute's keys (since 2026-11-18; spec `2026-11-18-flute-every-octave-design.md`, Revision 3): the four octaves as
 * pills ([OctavePills]: c¹–h¹, c²–h², c³–h³, c⁴), the chosen one ([octave]) yellow; under them the seven letter keys, C to B, each in its
 * letter's colour and named for the note it writes in that octave ([FluteNotes.inOctave]: c¹, d³; its black note while
 * [signWaiting]; faded where it writes none: D and A then, and all of octave 4 but c⁴); and under those the tools in
 * columns 1 2, 4 5 and 7: ♯♭ ([SharpFlatKey], [FluteActions.onSign]), ⌒ (down while [joinWaiting], faded unless
 * [joinEnabled]), 💤 (held like a note key, sunk while [restLit]), 😮‍💨, and 🧽 (repeating while held, faded unless
 * [eraseEnabled]). The key of [litLetter] sinks onto its shadow inside a yellow ring.
 *
 * A finger down on a letter calls [FluteActions.onKeyPressed] with the letter (0 = C to 6 = B) at once, and the finger
 * lifting, or sliding off, calls [FluteActions.onKeyReleased]; sliding onto another key does not press it, so every note
 * in the tune is a deliberate tap. A screen reader activates a key through [FluteActions.onKeyActivated]. A pill calls
 * [FluteActions.onOctave] as the finger goes down.
 *
 * Every key row is [keyHeight] tall, [KeyHeight] unless the phone is on its side or the tab is zoomed, and the pills and
 * the two rows sit centred in the height they are given. A key's width comes from the width given, less [keyGap]
 * between each two keys. When each key came and where it stood before is in `DESIGN.md`, "The keys" and "Octaves".
 */
@Composable
fun FluteKeys(
    octave: Int,
    signWaiting: Boolean,
    litLetter: Int?,
    restLit: Boolean,
    eraseEnabled: Boolean,
    joinWaiting: Boolean,
    joinEnabled: Boolean,
    actions: FluteActions,
    modifier: Modifier = Modifier,
    keyHeight: Dp = KeyHeight,
    keyGap: Dp = KeyGap,
) {
    val groupLabel = stringResource(R.string.flute_keys)
    BoxWithConstraints(modifier = modifier) {
        // One key's width, in whole pixels: seven keys that each rounded up ran 4 px over the row on a 420 dpi phone, so
        // the rows are measured in pixels and centred, and the tools stand exactly under the letters' columns.
        val keyWidth = with(LocalDensity.current) {
            ((constraints.maxWidth - keyGap.roundToPx() * (KeysPerRow - 1)) / KeysPerRow).toDp()
        }
        val key = Modifier.width(keyWidth).fillMaxHeight() // every key of both rows: one column wide, as tall as its row
        val row = Modifier
            .fillMaxWidth()
            .height(keyHeight + KeyDepth)
            .padding(bottom = KeyDepth)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = groupLabel },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OctavePills(octave, actions.onOctave, Modifier.width(keyWidth * KeysPerRow + keyGap * (KeysPerRow - 1)))
            Row(row, horizontalArrangement = Arrangement.spacedBy(keyGap, Alignment.CenterHorizontally)) {
                for (letter in 0 until KeysPerRow) {
                    FluteKey(
                        letter = letter,
                        note = FluteNotes.inOctave(letter, signWaiting, octave),
                        white = FluteNotes.whiteIn(letter, octave),
                        lit = letter == litLetter,
                        onPress = actions.onKeyPressed,
                        onRelease = actions.onKeyReleased,
                        onActivate = actions.onKeyActivated,
                        modifier = key,
                    )
                }
            }
            Spacer(Modifier.height(KeyRowGap))
            Row(row, horizontalArrangement = Arrangement.spacedBy(keyGap, Alignment.CenterHorizontally)) {
                SharpFlatKey(waiting = signWaiting, onSign = actions.onSign, modifier = key)
                JoinKey(waiting = joinWaiting, enabled = joinEnabled, onJoin = actions.onJoin, modifier = key)
                Spacer(key)
                RestKey(
                    lit = restLit,
                    onPress = actions.onRestPressed,
                    onRelease = actions.onRestReleased,
                    onActivate = actions.onRestActivated,
                    modifier = key,
                )
                BreathKey(onBreath = actions.onBreath, modifier = key)
                Spacer(key)
                EraseKey(enabled = eraseEnabled, onErase = actions.onEraseLast, modifier = key)
            }
        }
    }
}

/**
 * The four octaves as pills over the letter keys (since 2026-11-18): each names the notes its octave holds, c¹–h¹,
 * c²–h², c³–h³ and c⁴ alone (the user: "replace 1, 2, 3, 4 labels with c1-h1, c2-h2, c3-h3, and c4"; C4–B4 to C7 in
 * English), the chosen one ([octave]) yellow and down. A pill acts as the finger goes down ([onOctave]); its touch area
 * takes the room under it. A screen reader hears "Octave" and the octave's figure, so the pill of c⁴ and the key c⁴
 * are not one name.
 */
@Composable
private fun OctavePills(octave: Int, onOctave: (Int) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    Row(modifier = modifier.height(PillsHeight), horizontalArrangement = Arrangement.spacedBy(PillGap)) {
        for (each in FluteNotes.OCTAVES) {
            val low = FluteNotes.whiteIn(0, each)
            // The highest white note the flute has in that octave: h, or c itself in the top one.
            val high = (KeysPerRow - 1 downTo 0).firstNotNullOf { FluteNotes.inOctave(it, signed = false, octave = each) }
            val lowName = noteText(low)
            val highName = noteText(high)
            val name = if (high == low) lowName else buildAnnotatedString {
                append(lowName)
                append("–")
                append(highName)
            }
            val figure = noteLabelParts(resources, low).second
            OctavePill(name, figure, chosen = each == octave, onTap = { onOctave(each) }, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** One octave pill showing [name]: a button a screen reader hears as "Octave [figure]", selected while [chosen]. */
@Composable
private fun OctavePill(name: AnnotatedString, figure: String, chosen: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.flute_octave, figure)
    val colors = FluteTheme.colors
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (chosen) selected = true
                onClick {
                    currentOnTap()
                    true
                }
            }
            .pointerInput(Unit) { detectTapGestures(onPress = { currentOnTap() }) },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(PillHeight)
                .chunky(PillShape, if (chosen) Sun else colors.raised, colors.outline, PillDepth, pressed = chosen),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                color = if (chosen) InkOnFill else colors.text,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = PillNameFloor.asTextSize(), maxFontSize = PillNameSize.asTextSize()),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
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
    ToolKey(
        label = stringResource(R.string.flute_rest),
        pressed = lit,
        onActivate = onActivate,
        modifier = modifier.heldGesture(Unit, onDown = onPress, onUp = onRelease),
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
    var pressed by remember { mutableStateOf(false) }
    ToolKey(
        label = stringResource(R.string.flute_breath),
        pressed = pressed,
        onActivate = onBreath,
        modifier = modifier.heldGesture(
            Unit,
            onDown = {
                pressed = true
                onBreath()
            },
            onUp = { pressed = false },
        ),
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
 * A key held down: [onDown] as the finger goes down, and [onUp] when it lifts, when it slides off, and when the key
 * leaves the screen under it. That last one is why the release is in a `finally`: turning the phone takes the key off
 * the screen and cancels the gesture, with no up to come; the Activity lives on, so nothing else would end the note
 * (the view model ignores a key that is no longer the one held). The gesture is keyed on [key] alone and reads the
 * latest callbacks through state, so nothing that changes under a finger restarts it, which would let the key go.
 */
@Composable
private fun Modifier.heldGesture(key: Any?, onDown: () -> Unit, onUp: () -> Unit): Modifier {
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnUp by rememberUpdatedState(onUp)
    return pointerInput(key) {
        detectTapGestures(
            onPress = {
                currentOnDown()
                try {
                    tryAwaitRelease()
                } finally {
                    currentOnUp()
                }
            },
        )
    }
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
private fun ToolEmoji(emoji: String) = Emoji(emoji, size = ToolEmojiSize)

/**
 * A letter key (since 2026-11-18), 0 = C to 6 = B, in its letter's colour, named for the note it writes ([note]: c¹,
 * d³, or its black note while the ♯♭ key waits). Where it writes none (D and A while the sign waits, and all of octave
 * 4 but c⁴) it is faded, heard as disabled, and shows the name of its white note there ([white]: d⁴). It always reports
 * [letter], and its gesture is keyed on that alone: the name changes under a finger when a press spends the sign and
 * when a pill is tapped while the key is held, and neither may restart the gesture (whose `finally` would let the key
 * go). The view model ignores a press of a key that writes nothing.
 */
@Composable
private fun FluteKey(
    letter: Int,
    note: Int?,
    white: Int,
    lit: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChunkyKey(
        label = noteLabel(note ?: white),
        color = noteColor(white),
        down = lit,
        onActivate = { onActivate(letter) },
        enabled = note != null,
        modifier = modifier.heldGesture(letter, onDown = { onPress(letter) }, onUp = { onRelease(letter) }),
    ) {
        if (note != null && note != white) {
            // A signed name is longer ("gis¹", "C♯⁴"): it shrinks to fit on one line.
            KeyName(noteText(note), maxSize = 22.sp)
        } else {
            Text(noteText(note ?: white), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = InkOnFill, maxLines = 1)
        }
    }
}
