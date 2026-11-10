# The songbook

Since 2026-11-10 the metronome's tempo and beat have one owner, `MetronomeSettings`
(`domain/settings`), so the hand-over this file describes is done in one place: `openBook` reads the
owner for the book being left and then updates the owner with the opened book's speed and beat, and
`MetronomeViewModel` follows the owner (a running metronome jumps to the new tempo as before).
`FluteScreen` no longer holds the metronome's ViewModel, `openBook` returns nothing, and there is no
order for the screen to keep. Read "`FluteScreen` ... calls `MetronomeViewModel.setBpm`" below that way.

Moved out of this folder's CLAUDE.md on 2026-11-05 to keep that file short; read it before changing
books, the shelf, the book button or their storage.

The songbook (since 2026-11-04, spec `docs/superpowers/specs/2026-11-04-flute-songbook-design.md`;
the user: "child needs to practice more than two tunes and dont want to type notes over again when
tune changes"): the flute keeps six tunes at once, so a child types each school tune in once and
afterwards only switches between them. A `Book` (`domain/flute/Songbook.kt`) is a tune (the same
`List<TuneItem>`, the same `MAX_NOTES` cap, sanitised on load) with its own bpm and time signature,
a `Shelf` is the six books and the index of the open one, and the books sit in six fixed places,
each with a fixed picture that never changes, so a child who cannot read yet learns "my school tune
is the cookie": `Sweet` (`Shelf.kt`) is 🍭 🍪 🧁 🍩 🍦 🍬 in that order, six sweets that share no
shape or colour (the user asked for "dedicated number of slots, eg 6 with pictures", then "sweets
and cookies"; the six themselves were chosen in the 2026-11-04 brainstorm and approved by the user). There are no names, no typing, no choice of
picture, no built-in songs, no delete button, no more than six places and no save step: every change
to the tune goes through `save()`, which writes the open book (`FluteUiState.openBook`; `books` is
the shelf as last stored, `shelf` the same with the open book's tune as the child sees it), exactly
as the single tune was kept before. A screen reader hears the sweets as Lollipop, Cookie, Cupcake,
Donut, Ice cream and Candy (the spec's table says Doughnut and Sweet; the strings follow the US
spellings, as the English flag did the same day) and in Croatian Lizalica, Keks, Muffin, Krafna,
Sladoled and Bombon (`book_*`, used for the screen reader only).

While a book is open its speed and beat are the metronome's: the speed knob, its panel and the
metronome tab change them as before, and `save()` writes the metronome's current bpm and time
signature with the tune, so a change made on the metronome tab while the cookie is open is the
cookie tune's new speed (intended, "yes, each tune keeps its own speed and beat"). Opening another
book is `FluteViewModel.openBook(index)`, and its order matters because `FluteViewModel` and
`MetronomeViewModel` never reference each other: `openBook` first reads the metronome's config from
`SettingsStore` and saves it with the tune of the book being left (also when that book is empty,
where the speed is never used), then switches `tune`, `openBook`, `timeSignature` (the staff is
written in the new book's beat; an empty book keeps the current one), clears the marks of the last
Your turn and forgets the key let go of, and only then returns the `Pulse` the metronome must take;
`FluteScreen`, which holds both view models as it does for the speed panel, calls
`MetronomeViewModel.setBpm` and `setTimeSignature` with it afterwards (never `SettingsStore`
directly: those calls update the metronome state the knob and the metronome tab draw, save the
config and pass it to a running metronome, so a drum clicking in the background jumps to the new
tempo, which is acceptable). Were the setters to run first, the book left would record the new
book's speed. The returned bpm is the book's brought onto the ring's 40 to 200 (`TempoBand.MIN_BPM`
and `MAX_BPM`). It is null, and the metronome stays as it is, for an empty book (the child writes the
new tune at whatever speed and beat the metronome has, and the book records them when it is left),
for the open book picked again (logged as `flute.shelf_close`, nothing else changes) and for an index
off the shelf. Wiping a tune with the sponge until nothing is left empties its book, which stays
open, the staff blank and the button showing its sweet; there is no other way to empty one, so with
all six full the child wipes one to reuse it. After a restart the stored open book is opened and its
speed and beat are already the metronome's, so nothing needs applying.

Storage is `SettingsStore.loadShelf`, `saveBook(index, book)` and `saveOpenBook(index)`
(`SharedPreferencesSettingsStore`): each book is one string `"<bpm>;<time signature name>;<tune>"`
(`Songbook.encode` / `decode`, the tune in `Tune.encode`'s format, which has no `;`) under `book_0` to
`book_5`, and `open_book` is the open index. Reading is lenient, so a damaged book never loses the
other five: what cannot be read becomes the metronome's bpm and time signature, or an empty book, and
the tune is decoded with the book's own bpm as `legacyBpm`, so a tune from before lengths were
snapped keeps its rhythm. The migration from the single tune is decided by `open_book` alone: when
the key is missing the shelf was never stored (an update from before the songbook, or a fresh
install), the old `tune` key becomes book 0 (🍭) at the metronome's stored bpm and time signature,
book 0 is opened, and `book_0` and `open_book` are written and `tune` removed in one edit, so it
runs once. It is never decided by whether the books are empty: a child who wipes all six must not
get the old tune back on the next start, and once `open_book` exists a `tune` key is ignored
(`Songbook.restore`, `SongbookTest`; `SharedPreferencesSettingsStoreTest` in `androidTest` runs the
store over a throwaway preferences file, the first test of the store, and its "fresh store" gets the
process's cached preferences, so it checks the key layout and encoding, not the disk write).

The button (`BookButton`, `Shelf.kt`) is the open book itself, drawn as a little book like the
shelf's (the user, later on 2026-11-04: "sweet icon that shows what slot is currently active should
have same book layout with sweet same as load screen has", and from three true-size mockups,
`book-button-shape.html`, "A is fine"): the shelf's `LittleBook` at the `CornerBook` sizes (a 7 dp
spine, page edges 6 dp out and 4 / 2 dp in from the cover's top and bottom, corners 4 dp at the
spine and 12 at the edge), `StaffGeometry.CORNER_BUTTON` (54 dp) wide with its page edges (the
cover 48) and `BOOK_BUTTON_HEIGHT` (64 dp) tall, with the knob's 5 dp shadow, the open book's sweet
at 26 dp (in dp, as on the shelf, so a large font scale cannot push it off the cover) and no label
(the tune is on the staff beside it; five note bubbles would be 5 dp dots at this size). The cover is
`Sun`, as the open book's is on the shelf (in dark mode too: `Sun` never changes), so the child sees
on the staff the very book they picked, and it pairs with the yellow speed knob one corner up: the
knob says how fast, the book under it says which tune. It replaced a round white button the same
day (white then because it was "not chosen"; the user's "same book" overrode that). It stands at
the staff's bottom left, under the clef, beside the names row, at
`StaffGeometry.bookButtonTop`: centred on the names' pills (`NAMES_TOP + PILL_HEIGHT / 2`), in the
column left of `NOTES_CLIP` (57 dp) that is empty from under the clef (`CLEF_BOTTOM`, 167 dp, the
hook's lowest control point, a bound the stroke never passes) down to the bottom of the marks row
(`TOTAL_HEIGHT`, 257 dp), so nothing moves and nothing is covered (`StaffGeometryTest` checks it at
both scales). At `RunningStaffScale` the cell grows and the button does not; only its position
follows the scaled geometry. It shows on an empty tune too, and while the tune runs it stays, faded
and not pressable, still showing its sweet (the knob is inert then too; the sweet stays because,
unlike the animal, it does not walk the staff). The book fades as one layer: `LittleBook` fades
its page edges and cover together and passes `fade = false` to its `ChunkyButton`, since a cover
faded on its own would show the page edges behind it through its open edge. Only the cover is
pressable and carries the description; `FluteScreenTest` checks the cover's 48 x 64 and that the
page edges, 6 dp further right, end before the notes. It was first sketched at the staff's top right
(approved from the sketch, "A is fine"), and turned down on the real staff for two reasons: the tune
is shown scrolled to its end with `NOTES_END_PADDING` at 4 dp, and every note from c² up has its stem
top inside the button's 0 to 59 dp, so it covered the newest notes' stems; keeping the staff's end
clear instead (about 60 dp of end padding) made every long tune show about two beats less. True-size
mockups of the three places (`button-fit.html`) went back to the user, who chose bottom left, under
the clef, beside the names, on 2026-11-04 ("C is fine"). A screen reader hears "Tune: Cookie, tap to
change" (`flute_book_button`), never a number. A tap calls `FluteViewModel.openShelf()`, which ends a
playback (`flute.stop`, reason `book`), a Your turn (`flute.listen_stop`, reason `book`) and a held
key as `openSpeed()` does and leaves the tone player open, and then `FluteContent` sets
`choosingBook`.

The shelf (`TuneShelf`, `Shelf.kt`): `FluteContent` keeps `choosingBook` in `rememberSaveable`, as it
keeps `choosingSpeed`, so rotation and the language switch keep it open, and while it is true the
shelf takes the place of the stage, the keys and the Play row (leaving the tab drops it, and logs no
closing line, as for the speed panel). Six `BookCover`s stand in two columns and three rows (sideways in one row, see below), always
in the sweets' order (🍭 🍪 / 🧁 🍩 / 🍦 🍬), each a little book (`LittleBook` at the `ShelfBook` sizes,
the drawing the book button shares): a cover square at the spine and round
at the edge that opens (`BookLook.shape`), a dark spine down its left edge, page edges (thin lines on
paper) showing past its right edge, the sweet large in the middle and under it a white label with
the tune's first five notes (`Book.coverNotes`, rests and breaths skipped) as small `NoteBubble`s in
their letters' colours, the label always five bubbles wide so every label is the same size (the
bubbles shrink on a 360 dp phone, where five of 22 dp do not fit; sideways the label is as many bubbles as
fit). The open book's cover is `Sun`,
the others `colors.raised`, and dark mode follows `FluteColors` as every other card. An empty book
has its sweet faded and a blank label; there is no number and no word anywhere on the shelf. One tap
on a book is `onChoose(index)`, which closes the shelf and, in `FluteScreen`, calls `openBook`
(which gives the metronome the book's speed and beat itself); the open book's tap only closes it; there is no OK and the back button
is not handled, as in the speed panel and the notes picker, and a stray tap loses nothing, since
every tune is kept as it is. The sweet is sized from its cover, not set: `sweetSize` is 0.32 of the
cover's height (the mockup's 36 px sweet on a 112 px cover, a third of its height), at least 40 dp
(`SweetMinSize`, on the shortest cover) and at most half the room between the spine and the cover's
other edge, in dp rather than sp so a large font scale cannot push it out or onto the label; a third
leaves the label and the gap under the sweet their room at every height from `BookMinHeight`
(116 dp) up. The column is `fitOrScroll` at `ShelfMinHeight` (three `BookMinHeight` rows, two 16 dp
gaps and the column's 20 dp), so it fits a 360 x 640 dp phone with large text and scrolls only below
that. A screen reader hears each cover as one node, "Cookie, 3 notes" (`flute_book_notes`, the
note-count plural), "Cupcake, empty" or "Lollipop, open now", and the bubbles on it stay silent
(the wording was chosen in the same brainstorm and approved by the user).

Sideways (2026-11-05, spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "The shelf of tunes"):
the six books stand side by side in one row, like a real shelf, with no right column, as tapping a book is
the only action (and `FluteContent` keeps `choosingBook`, so a turn leaves the shelf open). Each
`BookCover` is `weight(1f)` and as tall as the room up to 300 dp, centred, 8 dp apart with 8 dp margins: about
128 x 300 on the 915 x 411 phone, 83 wide at 640. There is no `fitOrScroll` and no `ShelfMinHeight` here (that
is the upright column's); the six covers are in the room on the three phones, which `FluteSidewaysTest` checks
with their bounds. The little strip under the sweet shows as many of the tune's first notes as fit
(`coverNotesThatFit`, bubbles 22 dp with `CoverNoteGap` between and a 10 dp `LabelInset`): three at 915, two at
800, one at 640, where upright always shows five.

The sweet is bigger sideways (`sidewaysSweetSize`, used by `BookCover` when `LocalSideways`): the third of the
cover's height with the 40 dp floor as upright, but capped at 0.625 of the room between the spine and the cover's
other edge instead of a half, and where that falls under 40 dp (the 640 phone: a room of about 48 dp) the sweet
keeps 40 dp, or 0.85 of the room on a window narrower than that. That is 58 dp at 915 (the mockup's), about 47 at
800 and 40 at 640. Upright's half-of-the-room cap gave 46, 38 and 24 dp, and 24 is under the book button's 26;
the sweet is the only cue a child who cannot read yet has for "my school tune", so it is not left small. The
sweet's glyph is about 1.05 times its size wide, which the 6 dp `LabelMargin` on each side of a 40 dp sweet in
the 640 phone's room still clears. `SweetTest` pins the sizes and that the sweet is never under
`CornerSweetSize` from 31 dp of room up (below 30 dp the bound does not hold, and no sideways window is that
narrow).
