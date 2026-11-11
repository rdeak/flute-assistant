// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import hr.bertlab.fluteassistant.domain.tuner.FluteKey.C_SHARP
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.E_FLAT
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.G_SHARP
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L1
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L2
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L3
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.LOW_C
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R1
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R2
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R3
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.THUMB

/** The keys of a Boehm flute a fingering diagram shows, in playing order from the head joint down. */
enum class FluteKey {
    /** Left thumb (B key). */
    THUMB,
    L1, L2, L3,
    /** Left little finger: the G sharp / A flat lever. */
    G_SHARP,
    R1, R2, R3,
    /** Right little finger: the D sharp / E flat key. */
    E_FLAT,
    /** Right little finger on the foot joint: the C sharp roller. */
    C_SHARP,
    /** Right little finger on the foot joint: the low C roller. */
    LOW_C,
}

/**
 * The basic fingering of every note a C-foot flute plays, c1 to c4 (MIDI 60 to 96), after the
 * Woodwind Fingering Guide's basic chart. B flat uses the "one and one" fingering. a3 sharp (94)
 * and h3 (95) need trill or sliver keys the diagram does not draw, so they have no entry.
 */
object FluteFingerings {
    private val chart: Map<Int, Set<FluteKey>> = mapOf(
        60 to setOf(THUMB, L1, L2, L3, R1, R2, R3, LOW_C),            // c1
        61 to setOf(THUMB, L1, L2, L3, R1, R2, R3, C_SHARP),          // cis1
        62 to setOf(THUMB, L1, L2, L3, R1, R2, R3),                   // d1
        63 to setOf(THUMB, L1, L2, L3, R1, R2, R3, E_FLAT),           // dis1
        64 to setOf(THUMB, L1, L2, L3, R1, R2, E_FLAT),               // e1
        65 to setOf(THUMB, L1, L2, L3, R1, E_FLAT),                   // f1
        66 to setOf(THUMB, L1, L2, L3, R3, E_FLAT),                   // fis1
        67 to setOf(THUMB, L1, L2, L3, E_FLAT),                       // g1
        68 to setOf(THUMB, L1, L2, L3, G_SHARP, E_FLAT),              // gis1
        69 to setOf(THUMB, L1, L2, E_FLAT),                           // a1
        70 to setOf(THUMB, L1, R1, E_FLAT),                           // b1
        71 to setOf(THUMB, L1, E_FLAT),                               // h1
        72 to setOf(L1, E_FLAT),                                      // c2
        73 to setOf(E_FLAT),                                          // cis2
        74 to setOf(THUMB, L2, L3, R1, R2, R3),                       // d2
        75 to setOf(THUMB, L2, L3, R1, R2, R3, E_FLAT),               // dis2
        76 to setOf(THUMB, L1, L2, L3, R1, R2, E_FLAT),               // e2
        77 to setOf(THUMB, L1, L2, L3, R1, E_FLAT),                   // f2
        78 to setOf(THUMB, L1, L2, L3, R3, E_FLAT),                   // fis2
        79 to setOf(THUMB, L1, L2, L3, E_FLAT),                       // g2
        80 to setOf(THUMB, L1, L2, L3, G_SHARP, E_FLAT),              // gis2
        81 to setOf(THUMB, L1, L2, E_FLAT),                           // a2
        82 to setOf(THUMB, L1, R1, E_FLAT),                           // b2
        83 to setOf(THUMB, L1, E_FLAT),                               // h2
        84 to setOf(L1, E_FLAT),                                      // c3
        85 to setOf(E_FLAT),                                          // cis3
        86 to setOf(THUMB, L2, L3, E_FLAT),                           // d3
        87 to setOf(THUMB, L1, L2, L3, G_SHARP, R1, R2, R3, E_FLAT),  // dis3
        88 to setOf(THUMB, L1, L2, R1, R2, E_FLAT),                   // e3
        89 to setOf(THUMB, L1, L3, R1, E_FLAT),                       // f3
        90 to setOf(THUMB, L1, L3, R3, E_FLAT),                       // fis3
        91 to setOf(L1, L2, L3, E_FLAT),                              // g3
        92 to setOf(L2, L3, G_SHARP, E_FLAT),                         // gis3
        93 to setOf(THUMB, L2, R1, E_FLAT),                           // a3
        96 to setOf(L1, L2, L3, G_SHARP, R1),                         // c4 (C foot)
    )

    /** The keys to press for [midi], or null when the chart has no plain fingering for it. */
    fun keysFor(midi: Int): Set<FluteKey>? = chart[midi]
}
