// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

/**
 * The two games on the notes tab: [FIND] puts a note on the staff and asks where it sits,
 * [HEAR] plays a short phrase and asks for it back, note by note. The choice is stored by
 * name, so never rename an entry.
 */
enum class NotesGame { FIND, HEAR }
