// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing. Credentials never live in the repo: they come from the environment
// (FLUTE_ASSISTANT_STORE_PASSWORD and friends, e.g. injected at build time from AWS Secrets
// Manager) or from keystore.properties in the project root (gitignored, see docs/release).
// Without them the release build is left unsigned so any machine can still compile it.
val keystoreProperties = Properties().apply {
    val file = rootDir.resolve("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(property: String, envVar: String): String? =
    System.getenv(envVar)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(property)
val releaseStoreFile = signingValue("storeFile", "FLUTE_ASSISTANT_STORE_FILE")?.let { rootDir.resolve(it) }
val releaseStorePassword = signingValue("storePassword", "FLUTE_ASSISTANT_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "FLUTE_ASSISTANT_KEY_ALIAS") ?: "upload"
val releaseKeyPassword = signingValue("keyPassword", "FLUTE_ASSISTANT_KEY_PASSWORD") ?: releaseStorePassword
val canSignRelease = releaseStoreFile?.exists() == true && releaseStorePassword != null

android {
    namespace = "hr.bertlab.fluteassistant"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hr.bertlab.fluteassistant"
        minSdk = 24
        targetSdk = 37
        versionCode = (property("fluteAssistant.versionCode") as String).toInt()
        versionName = property("fluteAssistant.versionName") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Whole-app R8 (shrink, optimise, obfuscate). Keep rules come from the libraries'
            // consumer rules. Upload app/build/outputs/mapping/release/mapping.txt to Play so
            // crash reports are readable. (A package-scoped optimisation was tried and crashed
            // at startup with an IllegalAccessError in androidx.startup.)
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Shipped languages: English (default resources) and Croatian. Other library locales are
        // stripped, and Play lists exactly these two.
        localeFilters += listOf("en", "hr")
    }
    testOptions {
        unitTests.all { test ->
            // A recorded "Your turn" session to replay on the JVM (SessionReplayTest; see
            // ui/flute/CLAUDE.md, "Sessions"): -Psession=<folder>, relative to the repository
            // root, and -Psweep=<rule>:<v1>,<v2>,... The task is never up to date while one is
            // named, so a second replay of the same folder runs again.
            val session = providers.gradleProperty("session").map { rootDir.resolve(it).absolutePath }.getOrElse("")
            test.systemProperty("fa.session", session)
            test.systemProperty("fa.sweep", providers.gradleProperty("sweep").getOrElse(""))
            if (session.isNotEmpty()) test.outputs.upToDateWhen { false }
        }
    }
}

// Room exports each schema version to app/schemas so future migrations can be written and tested.
room {
    schemaDirectory("$projectDir/schemas")
}

// With AGP's built-in Kotlin, jvmTarget defaults to compileOptions.targetCompatibility.
kotlin {
    compilerOptions {
        // Opt in to APIs we use deliberately (ModalBottomSheet, SegmentedButton, etc.).
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    // AndroidX core + Activity/Lifecycle
    implementation(libs.androidx.core.ktx)
    // In-app language switch: AppCompatDelegate.setApplicationLocales works back to minSdk.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Compose + Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
