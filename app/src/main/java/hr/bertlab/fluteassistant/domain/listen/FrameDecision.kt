// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * What the tuner did with one microphone frame; logged per frame when verbose and counted in the summary.
 * [FAINT] is the flute's alone: a clear pitch under [NoteOnsetTracker.MIN_LEVEL], too quiet to be the flute at
 * the phone (the tuner never decides it, so its summary's `faint` is 0).
 */
enum class FrameDecision { IGNORED, SILENT, LOW_PROBABILITY, FAINT, UNSTEADY, SHOWN }
