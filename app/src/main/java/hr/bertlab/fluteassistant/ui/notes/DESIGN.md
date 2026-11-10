# Note reading game: design and history

The long text: how the notes tab works, why, and who decided what. It was this folder's CLAUDE.md
until 2026-11-10 (and part of the root one until 2026-11-02); `CLAUDE.md` beside it now holds only
the rules that must not be broken and says which part of this file to read before changing what.
It is not loaded by itself: open it when you change the notes tab.

Read with these names in mind (the refactor of 2026-11-10 moved the round's rules into
`domain/notes`; the behaviour described below did not change): the tab's state and the round's
rules are one class, `NotesRound` (it was `NotesUiState`): `NotesRound.open` starts a round,
`tapped` judges a tap and `next` puts up the next note or phrase, and `NotesViewModel.onNoteTapped`
and the rest call them and add the sound, the waits, the storage and the log. `NotesContent` takes
the round and one `NotesActions` object, and `NoteStaff` takes the round.

This first part is Find, the original game. Since 2026-11-07 the tab has a second game, Hear, and a sticker book of
progress per note ("The two games" onwards); both use the same state, staff and touch.

`StaffNotes` (`domain/notes/`) is the pool: the naturals c1 to c3 (MIDI 60 to 84), fifteen
notes whose index in `ALL` is their place on the treble staff (0 = the ledger line below,
`LINE_POSITIONS` 2, 4, 6, 8, 10 are the lines, 14 = the second ledger line above);
`ledgerLinesFor` and `sanitize` (unknown or empty stored sets fall back to `DEFAULT_PRACTICE`,
c1 to g1) live there too. `NotesViewModel` (Activity-scoped; spec:
`docs/superpowers/specs/2026-10-22-note-reading-game-design.md`) runs one round of
`NotesRound.ROUND_LENGTH` (10) notes: `onNoteTapped(midi)` judges an answer (ignored while
`solved`, `roundOver` or Hear's `phrasePlaying`), a right tap sets `solved`, appends to `results` (true = first tap),
plays the note through the tuner's `ReferenceTonePlayer` at `TONE_VOLUME` and after
`FEEDBACK_MS` (1200) draws the next note (never the same twice in a row unless only one is
chosen) or ends the round; a wrong tap joins `wrongTaps`, becomes `lastWrongTap` (the cross that
gets the arrow) and zeroes the streak. `taps` counts
every tap so `accuracyPercent` (right taps over all taps) differs from `score`; `roundMillis`
is the time from the round's first tap to the tenth note found (`MonotonicClock`, so tests use
`FakeClock`), shown in the summary only as one of the metronome's animals (`NotesRound.speed`,
the time per note: cheetah up to 2 s, rabbit 3.5 s, duck 6 s, turtle 10 s, then the snail), as the
accuracy is only one to three stars (`aimStars`: 90 % and 70 %). The chosen
notes persist through `SettingsStore.savePracticeNotes` (`practice_notes`, a string set), the last one cannot be switched off, and any change to them starts a fresh round; the sound of
a found note can be switched off (`toggleSound`, `SettingsStore.saveNoteSound`, a switch row
under the picker's rows); `resetRound()` (also what Again on the summary calls) throws the round away and is
called by the tab bar when the tab is left, and `stopSound()` when the screen is disposed (it means "the screen went away" now, see Hear), so
the tuner's microphone does not hear the tone. A `Random` is passed through an internal
secondary constructor for deterministic tests. `NoteStaff` draws the staff on a Canvas
(it takes all the height left in the stage and sizes the places from it; it shows the staff
and its two neighbouring spaces, d1 to g2, plus the ledger-line places c1, a2, h2, c3 only as
far as a chosen note needs them, `StaffNotes.visiblePositions`, their ledger lines dashed; every
place a full-width band, the spaces tinted, the lines thick; a small, faint hand-built clef whose
curl sits on the G line; one note column in the middle of the width, clear of a right-handed
finger sliding on the right, after the user rejected a two-column split; Hear's phrase columns
are another thing, answered one after the other). The child answers by touch and slide: a finger down shows a see-through note
on the nearest place and turns its band yellow (`placing`, hoisted to `NotesContent` for the
"Slide, let go" line and for Hear's speaker bubble, which waits while a finger is down), it follows the finger with a haptic tick per place, lifting it answers,
sliding off the staff cancels. A cancelled touch answers nothing (the system taking the touch back, or the staff going
away under the finger, as when a second finger opens Pick): Compose ends it with a lift already consumed, which the
gesture tells from a child letting go (`change.isConsumed`), and the place goes with it (the gesture's `finally`, which
also covers the staff locking under a finger). Until the fix wave of 2026-11-07 such a cancel answered the note under
the finger. Pinned by `a_cancelled_touch_answers_nothing`, `a_plain_down_and_up_answers_the_place` and
`a_finger_left_down_is_forgotten_when_the_question_goes`. A miss is a cross inside its own place (`CROSS_RADIUS` 0.45 of the
pitch, so it never hides the place next to it), the latest with a ⬆️/⬇️ arrow towards the right
place; the found note is a head in its letter's colour with sparkles. Over the canvas sits a
column of fifteen full-width places that take no touches but are buttons for accessibility,
named "Staff position N" counted from the bottom, never the note, so a screen reader does not
give the answer away; their semantic click answers at once.

## The tab on screen

Moved here from the root CLAUDE.md on 2026-11-05; the games and the sticker book came on 2026-11-07.

The notes tab has two games on one staff, chosen by two pills on top ("The two games"), then one hero row (the
streak as up to five flames, sized in dp as pictures, since five at a larger text size would not fit their pill, the
note's bubble or, in Hear, the speaker bubble, and a "Pick" button with a dot per chosen letter) over a stage with the
staff and one feedback line, and ten round places at the bottom (a star for first time, a faint star for a retry,
yellow for the current note); "Pick" swaps the staff for the picker in place (no dialog): the fifteen notes in octave
rows, highest on top, each chosen key a sticker that fills as the note is learned ("The sticker book"), the "Sound"
switch row and a big OK, the way back (and the phone's Back, `ui/DESIGN.md`). The end-of-round summary has no numbers on screen either: a trophy, ten
stars, tiles for the best streak (flames), the aim (one to three stars) and the speed (an animal), and the keys of the
notes asked.

The hero row is 76 dp (it was 96 until the pills took their height) and the word "Find" that stood over the bubble is
gone: the pill names the game. `NotesMinHeight` is 623 dp (it was 560: the pills' 64 dp and the 5 under them and the
column gap of 14, less the 20 the hero row gave back), so the smallest place at the minimum is what it was.

At large text the upright Pick button holds together like the sideways one (2026-11-07, the user's call, on
phones and tablets alike): the backpack is in dp, the word keeps 6 dp from the outline, and it shrinks to fit
the width and `PickWordHeight` (`autoSize`, floor 8 dp). Before, "Odaberi" broke inside the word from 1.5 x text
and the dots were cut. At normal text nothing moved. A test of the word's size must go by its width:
`layoutInput.style.fontSize` keeps the size the text was given, whatever `autoSize` chose. The picker's title in the
76 dp row does the same: it shrinks from 30 sp to 14 beside a backpack in dp (32), so the row holds it at large text.
The summary's headline beside Pick shrinks from 28 sp to 11 (`HeadlineFloor`; at twice the text "💪 Keep practising!"
was cut at 18 on a 411 dp phone and at 16 on a 360 dp one, where it has 204 dp and needs a font of at most about 22.7
dp; 11 sp is 22 dp there). All four headlines and the title are pinned whole at twice the text in both languages, on
the 411 dp emulator and in a 360 dp window (`the_picker_title_and_the_headline_fit_the_hero_row_at_twice_the_text`,
`..._fit_a_360_dp_phone_at_twice_the_text`, each with `_in_croatian`).

## The two games

Since 2026-11-07; spec `docs/superpowers/specs/2026-11-07-notes-hear-and-sticker-book-design.md` (the calls and who
made them are listed at its end). The user said the tab "has room for improvement", asked for melodic dictation as a
new mode and for the note game to be gamified more, and from three drawn places for the switch chose two pills on top
of the tab, the tuner's own pattern. The mockups are `.superpowers/brainstorm/11082-1794036686/content/notes-modes-v2.html`
(local only, drawn at true size from `NoteStaff`'s formula).

- `NotesGame` (`domain/notes/`) is FIND or HEAR, stored by name (`notes_game`; never rename an entry; an unknown name
  reads as Find, the default). The pills are `ui/common/ModeChoice`, lifted from the tuner's `TunerModeChoice` so both
  tabs share one: 64 dp tall, 12 apart, 5 under, the chosen one yellow (`Sun`), a "Game" group for a screen reader. The
  emoji is inside the word (`find_note`, `hear_game`), so `emojiOf` gives "" and the pill draws the word alone. The word
  shrinks to fit at large text (`autoSize`, floor 12 dp) rather than end in an ellipsis: "👂 Slušaj" was cut short at
  twice the text on a 360 dp phone (fixed in the fix wave of 2026-11-07, for the tuner's pills as well);
  `the_game_pills_keep_their_words_whole_at_twice_the_text` (and `_in_croatian`) pin it at 360 and 411 dp wide.
- **The pills stay on all three screens** (asking, choosing notes, the summary), as the tuner's do, so the frame never
  rearranges and a child can pick the other game straight from the summary. That is why the picker's title and the
  summary's headline sit in the 76 dp hero row. Do not move them off the other two screens to win back height.
- Choosing the other game starts a fresh round at once (`setGame`), as choosing other notes does: a score belongs to
  one game and one set of notes. Tapping the chosen pill does nothing (ruled 2026-11-07: a re-tap must not throw the
  round away; Again starts one, and so does a note switched on or off in the picker, while opening Pick starts
  nothing). Leaving the tab resets the round and keeps the game. The remembered game and
  the two resets are Claude's calls, for the user to check.
- In Hear the bubble slot holds the speaker bubble, `HearAgainBubble` (68 dp, the speaker 34 dp, "Hear it again"),
  which plays the phrase again. The note's bubble would give the answer away. It is disabled while a finger is on the
  staff, since a touch there is an answer; `placing` is hoisted into `NotesContent` for it. Sideways the same bubble
  fills the panel's 108 dp. It is the only addition to the playing screen besides the pills.

## Hear

A second way to play the same staff: the phone plays a short phrase, the staff shows its first note, and the child
writes the rest by ear with the same slide-and-let-go. Judging is per note exactly as in Find: a first-try placement is
a star in the places row and one more flame; a miss shows the cross and the arrow, "Try again", zeroes the flames, and
the note counts as not first try. The child tries until the note lands. The round is still ten answered notes; the
given notes do not count.

- **Phrases** (`Phrases`, `domain/notes/`): 2 to 4 notes (`MIN_LENGTH`, `MAX_LENGTH`) from the chosen notes, so Pick
  governs both games. `draw`: the first note at random, not the first note of the previous phrase when there is a
  choice; each next note within a third (`REACH` 2 staff places up or down), never the same note; when no chosen note
  is that close, any other chosen note; with one note chosen, that note again (a repetition game, harmless). Candidates
  are sorted before the `Random` picks, so a seed gives the same phrase. Steps and thirds because a beginner hears
  steps long before leaps.
- **The staircase** (`nextLength`, `lengthFor`): a round opens with two notes. After a phrase with no miss the next has
  one more, up to four; after a phrase with a miss, one fewer, down to two (`phraseClean`). The given note comes free,
  so the last phrase is one longer than the notes left (`lengthFor`) and may be shorter than the staircase says. Every
  round starts at two again; nothing about the length is stored. The user chose "start at two and grow"; the down-step
  and the fresh start are Claude's calls.
- **On the staff**: the first note is given, a head in its letter's colour with no sparkles, and `currentMidi` is the
  note to place, `phrase[placed]`. The phrase stands left to right in columns `0.5 + (i - (n - 1) / 2) * 0.16` of the
  width (`phraseColumns` in `NoteStaff.kt`: two notes at 0.42 and 0.58, four at 0.26 to 0.74), clear of the clef and
  of the sliding finger. The column being answered carries what Find's single column carries: the see-through note, the
  dashed stubs of the far places, the crosses, the arrow and the last note's sparkles. Placed notes stay as heads in
  their colours, so the phrase builds up. Find is a phrase of one with nothing given, so one drawing path serves both.
  The user rejected a two-column split of Find's staff; that is a different thing from these columns, which the child
  answers one after the other.
- **The slots** (the first child test, 2026-11-09; spec: "Revision 2026-11-09" in the Hear spec above). The user watched
  a child play Hear and said: "note is too short in Hear mode, it should last longer, it has confused player what needs
  to be done, why first one is marked and second is not", and asked to "reduce cognitive load and update UI so it is
  more intuitive what needs to be done". The staff showed only the given note, nothing stood for the note to place, and
  the line under it was empty. Now every note still to find has a slot in its column (`slot` in `NoteStaff.kt`): a tall
  dashed capsule from the top edge of the top place to the bottom edge of the bottom one, round-ended, with a ❓
  (U+2753) in the middle, 1.4 pitches tall. Its half width is a pitch, or 0.42 of a column step where that is less, so
  neighbours keep a gap on a narrow staff. The note to place is pale yellow (`Sun` at 0.28) with a dashed outline
  (3 dp, 6 on and 4 off) in `colors.text`, ink by day and white by night, as the staff's lines are: `colors.outline` is
  almost the card's own navy at night and the dashes were lost (2026-11-09, from the first screenshots). The later ones
  have no fill, the light `colors.border` outline and a ❓ at 0.35, so the child sees how many are left. The slots are
  drawn after the bands, lines and clef and before every note, so a slot never hides one; none is drawn
  for the note just found (its head stands there). Find has the same slot in its one column until its note is found
  (`slotIndices`, since 2026-11-10: "Find says what to do" below). The ❓ steps aside for what stands in its place: while a finger is
  down the current slot loses its ❓ (the see-through note takes its place and slides inside the capsule, and the yellow
  band crosses it, so column and place make a crosshair), and while a wrong tap stands the current slot and the later
  ones all lose theirs, because the cross and the arrow are drawn over the middle of the column and landed on a ❓
  (the capsules stay, so the child still sees what is left). The ❓ comes back with the next note. The dashed ledger
  stub of a far place (c1) in the current column is cut to the slot's half width and clipped to the capsule (a
  `clipPath` of its rounded rect) while that slot is drawn, so it stays inside the capsule and does not poke out: cut
  to the half width alone it still poked about 2 dp out at the capsule's rounded end, where the capsule is narrower
  than that; the stubs of a head's own ledger lines are as before. The faded ❓ is drawn into a layer
  with alpha (one `Paint`, made once), as `drawText`'s alpha fades only the colour, and an emoji brings its own. Pinned
  by `the_note_to_find_has_a_slot_and_later_notes_fainter_ones`,
  `the_question_mark_gives_way_to_the_finger`, `a_miss_takes_the_question_marks_off_the_slots_and_keeps_the_capsules`,
  `the_slot_of_the_note_to_find_has_a_bright_outline_at_night` and `the_far_places_stub_stays_inside_the_slot`.
- **Find says what to do** (2026-11-10, from the review of that day, which the user answered with "start"). Hear
  got its slot and its line after the first child test; Find, the game the tab opens on, still showed a name, an
  empty staff and an empty line until a finger was down: the same nothing. Now Find's column has the current slot
  (the pale yellow capsule with the ❓, which steps aside for the finger and for a miss's cross exactly as in Hear)
  until its note is found, and the feedback line says "👆 Slide, let go" at rest as well as while a finger is down,
  so no new words were needed. Whether "Povuci i pusti" at rest reads as "drag the bubble down" is for a child to
  show; "❓ Nađi notu", Hear's own shape, is the one-string swap if it does. Pinned by `NoteStaffColumnsTest`,
  `find_says_how_to_answer_before_the_first_touch` and, on the emulator, `find_has_a_slot_for_the_note_to_find`.
- **The sound lights its note.** `NotesRound.sounding` is the index in `phrase` of the note sounding during a
  playback (null in the breath before it, in the gaps and when nothing plays). `NoteStaff` lights that index: a head
  gets a halo behind it (`Sun` at 0.7, 1 pitch round it, or less where the next capsule's edge is nearer, a column step
  less the slot's half width: 1.15 touched the next capsule at true size, and a pitch still does on a tall narrow
  staff, as in the tests' whole-screen window), a slot is filled `Sun` at 0.75 and its ❓ grows to 1.6 pitches at full
  alpha. First sound, first note; second sound, the ❓. The playback after the last note lights the child's own notes
  the same way. Nothing animates: the state flips per note,
  which is the pulse. Pinned by `the_note_sounding_lights_up`, `the_playback_after_the_last_note_lights_the_found_note`,
  `the_halo_stops_at_the_next_capsule` and `the_halo_is_a_whole_pitch_where_there_is_room`.
- **The prompt.** In Hear, while the phrase is not playing and no finger is down, the line under the staff says
  "❓ Find the next note" / "❓ Nađi sljedeću notu" (`hear_prompt`): the verb of the game the child already knows, and
  the slot's own symbol, so the words and the picture say the same. `FeedbackLine`'s order is now solved, playing,
  placing, wrong taps, hearing, then empty (Find's wait stays empty). **The floors:** the line is the longest of the
  feedback lines, and at twice the text it was cut (on a 360 dp phone) or broke inside a word (in the sideways panel).
  So its autoSize floors are low: upright `FeedbackFloor` is 11 sp, its own number (the headline's `HeadlineFloor` is the same today but they are not
  tied; it was 18) and the sideways panel's `PanelFeedbackFloor` is 9 sp (it was 12), for every line the feedback shows. A floor only matters where the text
  would not fit, so the shorter lines never reach it, and a word cut short is worse than a small one. Measured at
  twice the text on the emulator (density 2.625): upright in 288 dp, "❓ Find the next note" fits from a floor of 16 sp
  down (763 px at 17, 739 at 16, in a 756 px line) and the Croatian too; sideways in the 124 dp panel's two lines, the
  English fits from 11 sp down and the Croatian from 10 sp down, so 9 keeps a margin for another phone's emoji font.
  Pinned by `hear_asks_for_the_next_note_while_waiting` (and `_in_croatian`), `the_hear_prompt_holds_at_twice_the_text`
  (and `_in_croatian`, upright at 360 and 411 dp wide) and `the_hear_prompt_is_whole_in_the_panel` (the sideways
  panel's words at 915 x 411 and 640 x 360, 1x, 1.5x and 2x text, both languages).
- **Turned down on 2026-11-09, do not add:** naming the given note with a letter bubble on the staff (a second
  vocabulary on the staff, and Hear is about the ear), a "1, 2, 3" under the slots (numbers), dropping the given note
  (a dictation needs a reference pitch) and a tutorial overlay (words, and seen once).
- **No pause between the notes.** Find locks the staff for 1.2 s after a right tap. Hear does not: a placed note
  settles as a head, sounds `NOTE_MS` (900 ms) under the Sound switch and the next column is live at once, so the
  phrase stays in the child's head (a lock per note would be over a second of waiting at every one).
- **The last note's feedback is the whole phrase played back**, then the next phrase appears and plays. It always
  sounds, Sound on or off: it is the question, and a silent reward is nothing. Tones are `NOTE_MS` 900 with `GAP_MS`
  200 of silence between, at `TONE_VOLUME`, through the tuner's `ReferenceTonePlayer`. While a phrase plays
  `phrasePlaying` locks the staff (`NoteStaff`'s `locked`) and the feedback line says "👂 Listen", over "Slide, let
  go" and "Try again", as the staff takes no answer then. Only "That's it!" goes before it (`solved` is checked
  first): the last note's playback is its feedback, so the line says "🎉 That's it!" with the sparkles while the
  phrase plays back, as the spec has it, and a fresh phrase's playback (`solved` false) says Listen. Pinned by
  `the_last_note_of_a_phrase_says_thats_it_while_the_phrase_plays_back` and
  `the_feedback_line_says_listen_while_the_phrase_plays`. (Until the fix wave of 2026-11-07 Listen went first, and
  "That's it!" never showed in Hear.)
- **Who starts a phrase.** A phrase plays when it comes on screen, never on another tab and never under the picker.
  `NotesContent` calls `onPhraseShown()` from `LaunchedEffect(phraseId, game, choosingNotes)`, only while the picker is
  closed, and the ViewModel plays each `phraseId` once (`heardId`). The ViewModel cannot decide this itself: the tab
  bar resets the round when the tab is left, which draws a new phrase, and that must not sound on another tab; and in
  Hear every note switched in the picker (and the Hear pill tapped there) draws a phrase the child cannot see. So the
  screen asks for the phrase only while the staff is on screen: a phrase drawn under the picker plays when OK closes
  it, and a screen that comes back to a phrase already heard (the picker closed on an unchanged phrase, a turn of the
  phone) plays nothing. The effect sits above the sideways branch, so a turn neither asks again nor loses the ask.
  `phraseId` counts on across rounds and games, so a fresh round's phrase is always new. Pinned by
  `a_phrase_is_asked_for_only_while_the_staff_is_on_screen` (`NotesScreenTest`) and
  `a_turn_does_not_ask_for_the_phrase_again` (`NotesSidewaysTest`).
- **`stopSound()` now means "the screen went away"** (ruled 2026-11-07). It cancels the playback and releases the player.
  If a playback was cut, it then either advances a phrase just answered (the playback was its feedback, so otherwise a
  disposed screen would leave `solved` stuck for good) or, for a phrase only being heard, forgets `heardId` so it
  plays again on return. Every other site calls the private `releasePlayer()`. Guarded by the playback being active,
  so Find never advances twice.
  `toggleSound` does not cut a playing phrase, as the phrase sounds with Sound off. `hearAgain()` plays the phrase on
  screen from the start; a tap on the bubble mid-playback restarts it.
- The summary is Find's, with the same tiles. The speed animal counts the listening time too, so it runs slower in
  Hear; honest, needs no words, left alone.
- **The pace** is 900 ms a note and 200 ms between (`NOTE_MS`, `GAP_MS`), about a note a second, a teacher's dictation
  pace. It was 450 and 100 until the first child test (2026-11-09: "note is too short"). A phrase that comes on screen
  waits `LEAD_MS`, 600 ms, before its first note ("👂 Listen" is already up), so two phrases never run into each other;
  "Hear it again" and the playback after the last note start at once. To watch at the next test: a four-note phrase is
  4.4 s, its playback after the last note another 4.4 s and then the next phrase, and 900 may be too slow for a child
  who has the idea. The pace and the staircase's feel are first guesses. Only a child can settle them: read the
  `notes.phrase` and `notes.round` lines of a run ("Logging").
- For a screen reader the staff in Hear is "Staff, press where the next note goes and let go"
  (`staff_description_hear`); the places stay "Staff position N", never the note.

## The sticker book

The user's call (2026-11-07): "the notes themselves are the collection". It reverses the 2026-10-22 "no history"
call, which was Claude's, now revisited with the user. Dropped in a line each and not to re-propose: a sticker album
screen, a path across rounds, auto-unlocking notes, daily streaks, mixing Find and Hear in one round.

- `NoteProgress` (`domain/notes/`) is a count from 0 to `KNOWN_AT` (8) per note of `StaffNotes.ALL`; zero is never
  kept, so two progresses that mean the same are equal. `found` adds one, `missed` takes one and never goes below 0,
  and a note at 8 is known for good: **a miss on a known note costs nothing and the star stays.** The ViewModel adds one
  for a first-try find in either game, and takes one from the note asked on its first wrong tap, once per answered note
  however many crosses it takes (so a guessing child cannot hold a note at zero while its neighbours fill). Nothing
  resets the book and there are no profiles; a sibling shares it. 8 because a note comes about twice a round with five
  chosen, so four clean rounds fill it (Claude's call, for a child to check).
- Stored as `note_progress`, a string set of `midi:count` (`SettingsStore.loadNoteProgress`, `saveNoteProgress`), saved
  at once on each change, and read as leniently as `practice_notes`: an entry that is not `midi:count` or whose midi is
  not on the staff is dropped, a count is clamped to 0..8.
- **The picker's chosen keys** are `NoteKey` (44 x 50): the tint is the ground (`noteTint` in `ui/common/Labels.kt`,
  the letter colour at 0.3 over the card colour, so it follows the theme) and the letter colour fills the key from the
  bottom to count / 8 of its height. At 8 the key is solid and wears a ⭐ (14 dp, sized in dp as a picture, so large
  text does not grow it past the room kept over a key) over its top-right corner, described
  "C4, learned" (`note_known`). The star hangs outside the outline, so whatever holds a `NoteKey` must not clip it. An
  unchosen key stays dashed and shows nothing of its count; it keeps it and shows it again when chosen. No number is on
  a key and a screen reader hears no "3 of 8" either: a count in the description was considered and not built.
- **The key's label colour** is ink (`InkOnFill`) on a bright ground and the theme's text colour on the dark tint of
  night (ground luminance under 0.2, `labelOn`). The dark tint is a surface like the card, not a bright fill, and ink
  on it is 1.7 to 2.3:1. The name sits across the fill's edge while a note is under way, so where the two grounds want
  different colours (night, 1 to 7 of 8) the name is drawn twice (`ClippedToGround`, two key-sized boxes): the text
  colour clipped to the tint above the edge, ink clipped to the fill below it, so half a letter is never lost (before
  the fix wave of 2026-11-07 one colour served the whole name, and at 3 to 5 of 8 half of it was unreadable). The
  second copy has no semantics in either tree (`clearAndSetSemantics` on the text itself), so the key keeps one name
  for a screen reader and a test. Where both grounds want ink (all of light mode) the name is drawn once, as before.
  Pinned by `a_half_learned_key_keeps_its_whole_name_in_dark_mode` and `a_half_learned_key_is_ink_all_over_in_light_mode`
  (pixels of the name above and below the edge).
- **Pick's dots** (`letterDotColors`, `LetterDots`): one per letter among the chosen notes, solid in the letter's colour
  when every chosen note of that letter is known and the pale tint otherwise, so the row is the book's spine.
- **The summary's keys** (`SummaryKeys`, `RoundSummary.kt`) are the notes asked this round (`askedThisRound`), plain
  `NoteKey`s in staff order, up to seven to a row (`summaryRows`: seven and three for ten asked), 44 x 50 and 6 apart,
  rows 8 apart. This is a revision made while planning: the first draft showed every chosen note, and with fifteen
  chosen three rows do not fit under the pills at any trophy size; the notes asked are what the round earned anyway. A
  key that grew this round (`grewThisRound`) hops, the trophy's own hop, with ✨ (14 dp) over its top-left corner; one that
  became known gets its star the same way. The keys of a row share the width equally (`weight(1f, fill = false)`), so
  a full row of seven shrinks all alike on a 411 dp phone (its stage is 339 dp inside, seven keys take 344). The block
  is one accessible node: "Your notes", then the names of the notes that grew. It keeps `KEY_OVERHANG` (10 dp) of room
  above the first row for the star and sparkle.
- **The trophy gives way** (`fitSummary`, `RoundSummary.kt`), to the room the stage has. At most 90 sp with one row of
  keys and 50 sp with two (the spec said about 72 dp; at 60 sp the design phone's stage was still about 5 dp short with
  two rows, and 90 rather than 100 leaves a phone with another emoji font a margin: the user's Galaxy A55 draws emoji
  with Samsung's metrics). On a shorter stage the trophy takes the largest size that fits, down to 36 sp; when even
  that is not enough the tiles drop from 104 to the sideways 92 dp (the upright look holds there: at twice the text
  their two lines take about 77) and the trophy grows back into the 12 dp that frees. Most phones are shorter than the
  design phone (360 x 800, 384 x 854, 393 x 873, 411 x 891), so before the fix wave of 2026-11-07, when the trophy had
  only its two sizes, the summary scrolled on most of them with only a sliver of Again in view at the 623 dp minimum.
- **The summary fits or scrolls.** Its column is `fitOrScroll` at a minimum worked out from its parts at the sizes
  chosen (`summaryMinHeight`): the trophy and the stars measured with the phone's own emoji font (a line is 1.364 times
  the size here, not the 1.2 `Emoji` asks for), the tiles, the key rows, Again (`BigActionHeight`, internal for this)
  and 8 dp under it, with gaps of at least 2 dp. Upright on a 411 dp phone: the 915 dp phone (a tab of 713 dp, the
  stage 442 inside) keeps 90 and 50 sp and nothing scrolls; a tab of 650 (the stage 379) takes a trophy of about 55 sp
  with five notes asked and nothing scrolls; at the tab's 623 dp minimum (the stage 352) five asked take, by the sums,
  the 92 dp tiles and a trophy of about 44 sp with a few dp to spare that another emoji font may eat, and ten asked
  still scroll, at 36 sp and 92 dp, rather than squeeze Again, since a child must always reach it. Pinned by `RoundSummaryFitTest` (the rule, with the emulator's emoji line),
  `the_summary_fits_the_stage_with_ten_notes_asked_and_two_rows` (713), `the_summary_fits_a_shorter_tab_with_five_notes_asked`
  (650) and `the_summary_keeps_again_whole_at_the_tab_minimum` (623, and 650 with ten asked).

## Celebrations

Drawn, not played: the app has one tone synth, and a fanfare would be new audio, for another day.

- **`StreakBurst`** (`Celebrations.kt`) at every multiple of five flames (5, 10, 15, and nothing for other streaks):
  six pieces, ✨ and 🎉, rise 120 dp from the middle of the staff and fade over 1 s (`BURST_MS`). It is drawn over the
  staff in the same box and handles no touch, so the slide goes through; it is sized in dp, hidden from accessibility
  (the tag is only for tests), and upright and sideways alike. It replays when the picker is closed at an unchanged
  streak of five (accepted: harmless, and fixing it means hoisting "shown" state above `Question`).
- **A perfect round** (ten first try): the summary's trophy hops 16 dp instead of 10, upright and sideways, with ✨
  either side (28 dp, in dp as pictures) under the "Amazing!" it already gets. The sparkles are upright only; sideways
  has no room planned for them in the card's row, and that was left for the user to ask for.

## Sideways

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "Notes game"; what every tab
shares sideways is in `../DESIGN.md`), and arranged for the two games on 2026-11-07 (the spec above, "Sideways"; the
true-size drawings and the emulator shots of the built screens are in
`.superpowers/sdd/2026-11-07-notes-hear-and-sticker-book/task-11-drawings/`, local only). `NotesContent` keeps
`choosingNotes` above the branch, so the picker stays open when the phone turns, and then, when `LocalSideways`, hands
over to `NotesSideways` (`NotesSideways.kt`). The phone is in the hand here, not on a stand. One layout, three screens,
and **the two game pills head the right column on all three** (`GamePillsSideways`: two `SidewaysPlainButton`s with
no emoji, the word alone, a "Game" group for a screen reader):

- **Playing.** From the left: the rail; a 124 dp panel on the page with the bubble (108 dp: the note in Find, the
  speaker in Hear, which waits while a finger is `placing`), the streak and the feedback line (each in a fixed slot, so
  nothing moves when the first flame or a line comes; the word "Find" is gone); the staff's card (padding 10, not 16:
  the staff needs the height); and the right column: the pills, the ten round places as two columns of five, and Pick
  low. The staff gets the phrase and the lock as upright does, and `StreakBurst` over it. The note stays in the middle of
  the staff, leaving the right side for the sliding finger, and the card stays clear of the 30 dp swipe-back zones at
  both screen edges.
- **`NoteStaff` is the one upright draws**, `END_MARGIN_PLACES` included, and so is the touch, slide and accessibility
  behaviour: it takes the card's full height and a place is the staff's height over the visible places plus
  two. That gives these places (the mockup in `hand.html` draws no margin places and leaves out c1, so its
  bands are taller; these are what is built, and `NotesSidewaysTest` pins them):

  | Window | Default notes | All fifteen |
  |---|---|---|
  | Sideways, 411 tall | 26 dp | 22 dp |
  | Sideways, 360 tall | 23 dp | 19 dp |
  | Upright, the 915 dp phone (framed) | 27.6 dp | 22.9 dp |
  | Upright at its 623 dp minimum | 21 dp | 18 dp |

  The upright 915 dp row is measured in the app on the emulator (2026-11-07, a 411 x 914 dp window with the bars: the
  staff 387 dp tall, over 14 or 17 slots), the pills and the shorter hero row included; it was 31 and 26 before the
  pills (the spec expected 28 for the usual notes). The minimum row is what the
  old 560 gave, as the minimum grew by exactly what the pills cost. The tab never scrolls sideways at 360 dp tall or
  more, because scrolling would fight the slide. Whether a finger can slide and let go at the 19 to 22 dp places is the
  one thing only a real phone can say; it is the worst case the spec names.
- **The pills** are 44 dp tall and 8 apart, or **30 and 6 on a short phone** (under 400 dp tall, `isShort`). At 360 tall
  the column is 330 dp inside (the window less the page's 8 + 8 and the column's 6 + 8). The spec drew 38 dp pills, and
  they, the ten places (5 x 28 + 4 x 7 = 168) and Pick (84) came to 334: 4 dp short, so Pick was squeezed to 78 and
  Hear's 5 dp shadow lay on the first stars. At 30 there are 6 dp between the groups and 1 dp under Hear's shadow
  (`task-11-drawings/emulator-question-hear-placed-640x360.png` is tight but reads fine). The spec's "328 of 344" left
  out the column's 14 dp of padding. A 30 dp pill is under the 48 dp tap guideline: ruled 2026-11-07, for the user to
  judge on a phone. At 411 tall the 44 dp pills leave 33 dp. The pill words are pinned whole at 1x and 1.5x text in
  both languages by `NotesSidewaysTest`, and at 2x by `TabletFitTest`. `SidewaysPlainButton` (in `ui/common`, every
  tab's sideways buttons) floors its word at 12 dp since the fix wave of 2026-11-07. Its old 12 sp floor doubled at
  twice the text and cut "👂 Slušaj" (135 wide in a 131 box on the 10-inch tablet sideways in Croatian, which failed
  `TabletFitTest`'s three notes cases).
- **Which notes?** The pills, Sound, OK low in the column; the title and the same rows as upright in the card, with
  bigger keys (78 x 62, shrinking to fit: about 72 at 800 wide, 48 at 640), the same `NoteKey`s with their fill and
  star. The ten places hide here. The column uses 266 of 330 dp at 360 tall and 296 of 381 at 411.
- **End of the round.** The trophy hopping on the left of the card beside the headline, one row of ten stars, the three
  tiles (92 tall) and the keys of the notes asked under them (36 x 40, 4 apart, rows 8 apart, with `SummaryKeys`' 10
  dp of room on top); the pills, Pick and Again low in the column (254 of 330 at 360, 284 of 381 at 411). Still no
  numbers. The star and trophy sizes are measured from the phone's own emoji font (`emojiWidthShare`), not guessed, so
  the row fills the room the trophy leaves without leaving the card.
- **Ten notes asked do not fit the 360 tall card at the usual gaps.** The card is 312 dp inside at 360 (363 at 411).
  Two rows of keys (106 with the star's room and the gap) with 16 dp between the parts came to 322 at 800 x 360, and
  the headline's second line at 640 x 360 made it worse. Built: the gaps are 10 dp when the keys take two rows (16
  otherwise), and the headline is the column's one weighted child, measured last, so its `autoSize` takes the height
  the rest leaves and the keys keep theirs. The tiles stay 92 (the spec offered 80). With five asked (the usual round)
  nothing differs: 270 of 312 at 800 x 360.
- Pick has its letter dots on both the question and the summary, so it is one button on both; the summary
  mockup drew it without. The dots are 11 dp; seven of them (all fifteen notes) shrink to 9 on the 100 dp
  column, so that the row keeps 9 dp from the button's outline (`SidewaysGeometry.pickDotSize`).
- For an adb or `uiautomator` driven check: the bounds of the "Staff position N" nodes are not the drawn
  bands (the lowest and the top one carry the staff's margins and are taller, upright too), so a tap at a node's centre can land in the
  next place, and at 360 dp tall the e1 place sat on the line between two nodes and was never hit. Tap the
  drawn bands instead: the nodes' union, cut into the visible places plus two (the margins), one tap per band.

## Tablets

Simply the phone layouts, zoomed (`../DESIGN.md`, "Tablets"), with nothing of their own.

## Logging (`FA.Notes`)

Tag `LogTags.NOTES`, through `Logger` like the others (root CLAUDE.md, "Logging and observability"). Names are stable.

- `notes.phrase` (`length`, `notes`: the midi numbers, space-separated, `phraseId`: the id it goes on screen with)
  at `d`, every time a Hear phrase is drawn, a round's first one included. Every reset (the tab left, Again) and
  every note switched in the picker draws one, so not every line is a phrase the child heard: a line whose
  `phraseId` is followed at once by the next one, with no tap between, was replaced unseen. Nothing logs a phrase's
  playing, so the id tells phrases apart but does not by itself say which ones sounded.
- `notes.round` (`game`, `notes`: how many notes are chosen, `score`, `bestStreak`, `millis`) at `d`, when a round ends.
- `notes.known` (`midi`) at `i`, the one time a note reaches 8.

To read a child's Hear run: `adb logcat -v time -s FA.Notes`, then put each `notes.phrase` beside what the child did.
That is how the pace and the staircase get settled.

## Strings

New in both languages, Croatian in the informal singular: `hear_game` (👂 Hear / 👂 Slušaj), `notes_game` (Game /
Igra, the pills' group), `hear_again` (Hear it again / Poslušaj opet, the speaker bubble), `notes_listening` (👂 Listen /
👂 Slušaj, the line while a phrase sounds), `staff_description_hear`, `note_known` (%1$s, learned / %1$s, naučena) and
`your_notes` (Your notes / Tvoje note, the summary's keys) and, since 2026-11-09, `hear_prompt` (❓ Find the next note /
❓ Nađi sljedeću notu, the line while a phrase waits for the child). `find_note` ("🔎 Find" / "🔎 Nađi") is now the pill's word.
"Slušaj" is the tuner's own word for Hear, so the Croatian stays one vocabulary; a screen reader therefore hears it
twice while a phrase plays, the pill and the line (accepted).

## Where the build differs from the spec

Ruled on 2026-11-07, so the code is right where the two disagree:

- The short phone's sideways pills are 30 dp, not 38 (the column did not fit, above).
- The sideways summary with ten notes asked takes 10 dp gaps and a shrinking headline, not 80 dp tiles (above).
- The summary's trophy is at most 90 and 50 sp, not 120 and about 72 dp, and on a shorter stage it shrinks to fit, down
  to 36 sp, and then the tiles drop to 92 dp; only below that does its column scroll (`fitOrScroll`). The spec had
  the trophy give way and forbade scrolling on the 915 dp phone only.
- Tapping the chosen pill does nothing; the spec only said choosing the other game resets.
- `stopSound()` advances an answered phrase or forgets a heard one, as above; the spec only said "cancel".
- The key's label is the theme's text colour on the dark tint, ink on the fill, drawn in two clipped halves where they
  meet (spec: ink).
- The upright headline shrinks to 11 sp, not 18, so that "💪 Keep practising!" stays whole beside Pick at twice the
  text on a 360 dp phone.

As built and not ruled, for the user to decide (do not "fix" it without asking):

- No sparkles beside the trophy on a perfect round sideways; the spec drew them upright only, and the user may ask
  for them. The big hop is there sideways too since the fix wave of 2026-11-07.

## Known limitations

Recorded so nobody "fixes" them blindly; each was seen and left.

- A placed head's solid ledger line and the next column's dashed stub can overlap by a few dp on the C4 line (about 4 dp
  at 31 dp places on a 360 dp staff). Since 2026-11-09 the stub is cut to the slot's half width, at most 0.42 of a
  column step (the cap this entry once proposed was 0.45), and clipped to the capsule, so it is smaller now; it may
  still touch on a narrow staff.
- A known key's star meets the next key's sparkle in the 6 dp gap between summary keys (both hang 9 dp over the corner).
- The burst replays when the picker is closed at an unchanged streak of five.
- The picker's seven 44 dp keys (332 dp) overflow a 360 dp phone's 288 dp stage. That predates this work (the
  2026-10-24 redraw) and is not fixed here.
- The 30 dp sideways pills on a short phone and the 19 to 22 dp sideways places need a real hand to judge.

## Tests

JVM: `PhrasesTest`, `NoteProgressTest`, `NoteStaffColumnsTest`, `LetterDotsTest`, `RoundSummaryFitTest` (the trophy
and tiles giving way), and the Hear and progress cases in `NotesViewModelTest` (staircase, last phrase cut, game
switch, playback gate, progress and its saving, the phrase log's id). Instrumented: `NotesScreenTest` (pills and their
words at twice the text, speaker bubble, when a phrase is asked for, the feedback line's order, the place let go when
the question goes or the staff locks, a cancelled touch answering nothing, picker keys and their name in both themes,
summary keys and its fit at 713, 650 and 623 dp, the hero row's words at twice the text in both languages at 411 and
360 dp wide, the burst, Hear's slots, the lighting of the note sounding and the prompt, and Find's bare staff). The
slot, miss, outline, stub, halo, lighting, finger-and-❓ and Find tests read the staff's pixels by its own formula
(`StaffPixels`), in the light theme except the night-outline test, which is dark:
`the_note_to_find_has_a_slot_and_later_notes_fainter_ones`,
`a_miss_takes_the_question_marks_off_the_slots_and_keeps_the_capsules`,
`the_slot_of_the_note_to_find_has_a_bright_outline_at_night`, `the_far_places_stub_stays_inside_the_slot` (it looks at
the stub's start, where a dashed line always has a dash, since the other end can fall in a gap),
`the_note_sounding_lights_up`, `the_playback_after_the_last_note_lights_the_found_note`,
`the_halo_stops_at_the_next_capsule`, `the_halo_is_a_whole_pitch_where_there_is_room`,
`the_question_mark_gives_way_to_the_finger` and `find_draws_its_slot_until_its_note_is_found` (it was `find_draws_no_slot_no_question_mark_and_no_halo` until Find got its slot on 2026-11-10). The prompt tests read
the text, not the pixels. Also instrumented: `NotesSidewaysTest` (the column at 360 and 411 tall, the pills on all three screens, the keys in the card, a turn not asking for the phrase again, the Hear prompt whole in the panel),
`LabelsTest` (the pills' words in both languages) and `SharedPreferencesSettingsStoreTest` (the game and the book, read
leniently). A test of a fit compares `getUnclippedBoundsInRoot()` with the room: `assertIsDisplayed` does not prove
one (`../DESIGN.md`, "Tests").
