// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hr.bertlab.fluteassistant.audio.tuner.AudioTap
import hr.bertlab.fluteassistant.core.log.AndroidLogger
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.core.log.RecordingLogger
import hr.bertlab.fluteassistant.debug.session.FileSessionRecorder
import hr.bertlab.fluteassistant.domain.flute.ListenRecorder
import javax.inject.Singleton

/**
 * The debug build's logging and session wiring: one [FileSessionRecorder] is the engine's
 * [AudioTap], the flute's [ListenRecorder] and the sink of the [RecordingLogger] that wraps the
 * [AndroidLogger], so every "Your turn" run is kept under the app's external files directory
 * (`ui/flute/CLAUDE.md`, "Sessions"). The release build's module of the same name binds the
 * plain logger and no-ops, and compiles none of this.
 */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {

    @Provides
    @Singleton
    fun provideRecorder(@ApplicationContext context: Context): FileSessionRecorder {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val facts = FileSessionRecorder.Facts(
            app = info.versionName ?: "?",
            code = PackageInfoCompat.getLongVersionCode(info),
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            sdk = Build.VERSION.SDK_INT,
        )
        // The recorder logs through the plain logger: the recording one would hand its lines back to it.
        return FileSessionRecorder(root, facts, androidLogger(context))
    }

    @Provides
    @Singleton
    fun provideLogger(@ApplicationContext context: Context, recorder: FileSessionRecorder): Logger =
        RecordingLogger(androidLogger(context), recorder)

    @Provides
    fun provideAudioTap(recorder: FileSessionRecorder): AudioTap = recorder

    @Provides
    fun provideListenRecorder(recorder: FileSessionRecorder): ListenRecorder = recorder

    private fun androidLogger(context: Context) =
        AndroidLogger(debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
}
