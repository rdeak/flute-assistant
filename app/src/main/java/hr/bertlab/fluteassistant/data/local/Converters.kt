// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.data.local

import androidx.room.TypeConverter
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import hr.bertlab.fluteassistant.domain.model.TimeSignature

/**
 * Stores enums by name. Unknown names (e.g. after a future rename) fall back to a sensible
 * default instead of crashing when reading old rows.
 */
class Converters {
    @TypeConverter fun fromTimeSignature(value: TimeSignature): String = value.name
    @TypeConverter fun toTimeSignature(value: String): TimeSignature =
        TimeSignature.entries.firstOrNull { it.name == value } ?: TimeSignature.FOUR_FOUR

    @TypeConverter fun fromSubdivision(value: Subdivision): String = value.name
    @TypeConverter fun toSubdivision(value: String): Subdivision =
        Subdivision.entries.firstOrNull { it.name == value } ?: Subdivision.NONE

    @TypeConverter fun fromSoundProfile(value: SoundProfile): String = value.name
    @TypeConverter fun toSoundProfile(value: String): SoundProfile =
        SoundProfile.entries.firstOrNull { it.name == value } ?: SoundProfile.WOODBLOCK
}
