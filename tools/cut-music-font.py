#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.
"""Cut the staves' music font from Bravura: app/src/main/res/font/flute_music.otf.

Bravura is Steinberg's SMuFL engraving font (https://github.com/steinbergmedia/bravura, redist/otf/Bravura.otf),
under the SIL Open Font License 1.1 with the Reserved Font Name "Bravura". The app's staves draw fourteen of its
symbols (the treble clef, the heads, the flag, the rests, the signs, the dot and the breath comma; specs
2026-11-24-fingers-music-symbols-and-book-chart-design.md and 2026-11-24-flute-staff-music-symbols-design.md), so
this keeps those and nothing else. A cut is a Modified Version under the OFL, and a Modified Version may
not use a Reserved Font Name: the cut is renamed "Flute Assistant Music". Steinberg's copyright and the licence stay
in the font's name table (name ID 0), and docs/licenses/Bravura-OFL.txt carries the licence beside the app.

Needs fontTools (pip install fonttools), in a throwaway virtual environment:

    python3 -m venv /tmp/venv && /tmp/venv/bin/pip install fonttools
    /tmp/venv/bin/python -I tools/cut-music-font.py Bravura.otf app/src/main/res/font/flute_music.otf
"""
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

# SMuFL code points, what `ui/common/MusicSymbols.ALL` draws: gClef; noteheadWhole, noteheadHalf, noteheadBlack; flag8thUp;
# restWhole, restHalf, restQuarter, rest8th; accidentalFlat, accidentalNatural, accidentalSharp; augmentationDot;
# breathMarkComma.
SYMBOLS = [0xE050, 0xE0A2, 0xE0A3, 0xE0A4, 0xE240, 0xE4E3, 0xE4E4, 0xE4E5, 0xE4E6, 0xE260, 0xE261, 0xE262, 0xE1E7, 0xE4CE]
# The Bravura the app's sizes were measured from (`ui/common/MusicSymbols`' CLEF_WIDTH and the rest; MusicSymbolsTest checks them).
# Another version may draw the symbols at other sizes: measure it again before cutting it.
VERSION = "Version 1.482"
FAMILY = "Flute Assistant Music"
POSTSCRIPT = "FluteAssistantMusic-Regular"


def cut(source: str, target: str) -> str:
    # recalcTimestamp=False keeps the head table's date, so the same source cuts to the same bytes.
    font = TTFont(source, recalcTimestamp=False)
    version = font["name"].getDebugName(5)
    if version != VERSION:
        sys.exit(f"{source} is Bravura {version}; the app's sizes are {VERSION}'s: measure the new one first")

    options = subset.Options()
    options.name_IDs = ["*"]  # keep the copyright, the licence and its URL
    options.name_languages = ["*"]
    options.layout_features = []
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=SYMBOLS)
    subsetter.subset(font)

    for record in font["name"].names:
        if record.nameID in (1, 4, 16, 21):
            record.string = FAMILY
        elif record.nameID == 6:
            record.string = POSTSCRIPT
        elif record.nameID == 3:
            record.string = f"{FAMILY}; cut from Bravura {version}"
    cff = font["CFF "].cff
    cff.fontNames = [POSTSCRIPT]
    top = cff.topDictIndex[0]
    top.FullName = FAMILY
    top.FamilyName = FAMILY

    font.save(target)
    return version


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: cut-music-font.py <Bravura.otf> <out.otf>")
    print(cut(sys.argv[1], sys.argv[2]))
