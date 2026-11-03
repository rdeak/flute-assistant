package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.common.hopOnBeat
import hr.bertlab.fluteassistant.ui.metronome.tempoBand
import hr.bertlab.fluteassistant.ui.theme.Sun

/** Width and height of [SpeedKnob]; the staff's tune starts further in, so it never covers a note. */
val SpeedKnobSize: Dp = 54.dp

/**
 * The metronome's yellow knob at the flute staff's top left, where printed music writes the tempo:
 * the animal of the speed at [bpm], facing right and hopping on the beat as [flash] rises while the
 * tune plays. A tap is [onClick]. A screen reader hears the animal's name ("Speed: Rabbit, tap to
 * change"), never a number.
 */
@Composable
fun SpeedKnob(
    bpm: Int,
    flash: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val band = tempoBand(bpm)
    val description = stringResource(R.string.flute_speed_knob, stringResource(band.labelRes))
    ChunkyButton(
        onClick = onClick,
        shape = CircleShape,
        color = Sun,
        depth = 5.dp,
        modifier = modifier
            .size(SpeedKnobSize)
            .semantics { contentDescription = description },
    ) {
        Emoji(band.emoji, fontSize = 26, modifier = Modifier.facingRight().hopOnBeat(flash))
    }
}
