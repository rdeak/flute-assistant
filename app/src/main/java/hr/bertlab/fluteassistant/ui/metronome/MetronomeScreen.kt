// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionStop

/** Stateful entry point: wires the ViewModel to the stateless [MetronomeContent]. */
@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    viewModel: MetronomeViewModel,
    modifier: Modifier = Modifier,
) {
    // Android 13+ only shows the background-playback notification with permission. Ask once,
    // on the first start, when the notification becomes relevant; playback works either way.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying && !notificationPermissionAsked && needsNotificationPermission(context)) {
            notificationPermissionAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MetronomeContent(
        state = state,
        onTogglePlayback = viewModel::togglePlayback,
        onBpmChange = viewModel::setBpm,
        onTimeSignatureChange = viewModel::setTimeSignature,
        modifier = modifier,
    )
}

/**
 * The whole metronome: the [MetronomeStage] (beat bubbles and tempo ring), then the big play
 * button. Nothing else: a child should never have to read. Loudness is left to the phone's
 * volume buttons. The column closes the tempo wheel on a touch anywhere, Play included. On a phone
 * on its side it is [MetronomeSideways] instead, with the same wheel and the same callbacks.
 */
@Composable
fun MetronomeContent(
    state: MetronomeUiState,
    onTogglePlayback: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wheel = rememberTempoWheelState()

    if (LocalSideways.current) {
        MetronomeSideways(
            state = state,
            wheel = wheel,
            onTogglePlayback = onTogglePlayback,
            onBpmChange = onBpmChange,
            onTimeSignatureChange = onTimeSignatureChange,
            modifier = modifier.closesTempoWheel(wheel),
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fitOrScroll(fits = maxHeight >= MetronomeMinHeight, minHeight = MetronomeMinHeight)
                .closesTempoWheel(wheel)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetronomeStage(
                state = state,
                wheel = wheel,
                onBpmChange = onBpmChange,
                onTimeSignatureChange = onTimeSignatureChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            BigActionButton(
                icon = if (state.isPlaying) AppIcons.Stop else AppIcons.Play,
                text = stringResource(if (state.isPlaying) R.string.stop else R.string.play),
                color = if (state.isPlaying) ActionStop else ActionGo,
                onClick = onTogglePlayback,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
