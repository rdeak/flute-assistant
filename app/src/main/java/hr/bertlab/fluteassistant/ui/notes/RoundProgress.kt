package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.dashedBorder
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/** A star for a note that took more than one try: still earned, but faint. */
const val RETRY_STAR_ALPHA = 0.35f

/**
 * One place per note of the round: a star when found on the first try, a faint star when it
 * took more, a yellow dot for the one being asked and a dashed circle for the rest. The
 * score and the accuracy at a glance, without a number.
 */
@Composable
fun RoundProgress(results: List<Boolean>, roundOver: Boolean, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val description = pluralStringResource(
        R.plurals.round_progress,
        NotesUiState.ROUND_LENGTH,
        results.size,
        NotesUiState.ROUND_LENGTH,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(NotesUiState.ROUND_LENGTH) { index ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                when {
                    index < results.size -> Emoji(
                        STAR_EMOJI,
                        fontSize = 22,
                        modifier = Modifier.alpha(if (results[index]) 1f else RETRY_STAR_ALPHA),
                    )
                    index == results.size && !roundOver -> Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Sun)
                            .border(OutlineWidth, colors.outline, CircleShape),
                    )
                    else -> Box(
                        Modifier
                            .size(28.dp)
                            .dashedBorder(OutlineWidth / 2, colors.border, CircleShape),
                    )
                }
            }
        }
    }
}

const val STAR_EMOJI = "⭐" // star
