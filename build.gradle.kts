// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 provides "built-in Kotlin" (no org.jetbrains.kotlin.android plugin) and defaults to
// Kotlin Gradle plugin 2.2.10. Per the AGP 9 release notes, placing a newer KGP on the build
// classpath upgrades the Kotlin version AGP uses. The Compose compiler plugin below shares the
// same catalog version, so the two always match.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
