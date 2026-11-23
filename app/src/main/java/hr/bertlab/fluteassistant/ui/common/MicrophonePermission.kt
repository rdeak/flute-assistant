// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The microphone permission as a screen uses it: whether it [isGranted] right now, the phone's own question
 * ([request]), and the app's page in the phone's settings ([openSettings]), the only way left once the phone will not
 * ask again. Made by [rememberMicrophonePermission]; the tuner and the flute's "Listen to me" share it, as they share
 * the [MicrophonePermissionCard].
 */
@Stable
class MicrophonePermission internal constructor(
    val isGranted: () -> Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * The screen's [MicrophonePermission]. [onAnswer] gets the phone's answer to [MicrophonePermission.request]: whether
 * it was a yes, and after a no whether the phone would show its question again (when it would not, only its settings
 * are left).
 */
@Composable
fun rememberMicrophonePermission(onAnswer: (granted: Boolean, canAskAgain: Boolean) -> Unit): MicrophonePermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } ?: true
        onAnswer(granted, canAskAgain)
    }
    return remember(launcher, context) {
        MicrophonePermission(
            isGranted = {
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            },
            request = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}
