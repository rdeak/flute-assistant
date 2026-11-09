# Play Console answers for Flute Assistant

Facts the answers rest on: the app is made for primary-school children learning the flute and
runs entirely on the device. It has four tabs: a digital flute, a metronome, a tuner and a note
reading game. The microphone is used only while the Tuner tab is open and while the flute tab
listens after its "Listen to me" button is pressed; audio is analysed in memory for pitch and
never stored, logged or transmitted. The metronome and the note reading game only play sound.
There is no account, no network access, no analytics, no ads, no SDK that collects data.
Settings and the child's tunes are stored locally on the device.

## Store listing

| Field | Value |
|---|---|
| App name | Flute Assistant |
| Short description | A digital flute, metronome, tuner and note game for young flute players. |
| Full description | See below |
| App icon | `play-listing/icon-512.png` (512x512) |
| Feature graphic | `play-listing/feature-graphic-1024x500.png` (subtitle "Flute, metronome, tuner and notes", text in `tools/IconGen.java`) |
| Screenshots | `play-listing/screenshots/en/` and `hr/`: one per tab, 1080x1920 JPEG (9:16, the ratio Play asks for), made by `tools/play-screenshots.sh` on the emulator |
| Tablet screenshots | `play-listing/screenshots-tablet/7-inch/<lang>/` (1296x2304) for the 7-inch tablet slot and `10-inch/<lang>/` (2880x1620) for the 10-inch slot: one per tab, made by `tools/play-screenshots.sh tablet`, uploaded by hand |
| Category | Music & Audio |
| Languages | English (default) and Croatian; the UI follows the system language and Android 13+ per-app language settings. Add a Croatian listing translation (draft below) with the `hr/` screenshots so Croatian users see the listing in their language. |
| Contact email | Required; shown publicly |
| Privacy policy URL | Required; host `privacy-policy.md` (for example on GitHub Pages) |

Full description, English:

> Made for children in primary school who are learning the flute. Four big, friendly screens
> and nothing to read.
>
> FLUTE. Fifteen colourful note keys in three octave rows. Hold a key to hear the note: the longer
> you hold it, the longer the note, and your tune is written on a real staff with the note names
> under it. Hear it played back, or press "Listen to me" and play it on your own flute for a star
> under every note you get right. Six little books keep six tunes.
>
> METRONOME. A tempo ring with five animals, from a snail to a cheetah: drag the animal round or
> tap one to jump to its speed. Two to six beats in a bar, shown as bubbles with the animal
> hopping on the beat you hear, and one play button. The click keeps going while you read sheet
> music in another app, with a notification to stop it.
>
> TUNER. Play a note and see it float above or below its home ring, with "Too high" or "Too low"
> and a tip on what to change; bring it home for "Perfect!". Or pick the note you want to play:
> hear it, see its fingering and tune to it.
>
> NOTES. A note reading game. A note name appears; slide your finger to its place on the staff
> and let go. Ten notes a round, flames for hits in a row and stars at the end. Choose which
> notes to practise.
>
> In English and Croatian. No account, no ads, no data leaves your device.
>
> The Android robot is reproduced or modified from work created and shared by Google and used
> according to terms described in the Creative Commons 3.0 Attribution License.

Full description, Croatian (short description: "Digitalna flauta, metronom, štimer i igra
čitanja nota za male flautiste."):

> Napravljeno za djecu u osnovnoj školi koja uče svirati flautu. Četiri velika, vesela ekrana i
> ništa za čitanje.
>
> FLAUTA. Petnaest šarenih tipki u tri reda. Drži tipku da čuješ ton: što je dulje držiš, to je
> nota dulja, a tvoja se pjesmica zapisuje na pravo crtovlje s imenima nota ispod. Poslušaj je ili
> pritisni „Poslušaj me“ i odsviraj je na svojoj flauti, za zvjezdicu ispod svake pogođene note.
> Šest knjižica čuva šest pjesmica.
>
> METRONOM. Krug za tempo s pet životinja, od puža do geparda: povuci životinju po krugu ili
> dodirni jednu da skočiš na njezinu brzinu. Od dvije do šest doba u taktu, kao mjehurići na
> kojima skakuće životinja dok kuca, i jedna tipka za sviranje. Metronom kuca i dok u drugoj
> aplikaciji gledaš note, a obavijest ga zaustavlja.
>
> ŠTIMER. Odsviraj ton i vidi kako lebdi iznad ili ispod svoje kućice, uz „Previsoko“ ili
> „Prenisko“ i savjet što promijeniti; vrati ga kući za „Savršeno!“. Ili odaberi ton koji želiš
> svirati: poslušaj ga, pogledaj prstomet i ugodi flautu prema njemu.
>
> NOTE. Igra čitanja nota. Pojavi se ime note, a ti povuci prst do njezina mjesta na crtovlju i
> pusti. Deset nota po krugu, plamičci za pogotke zaredom i zvjezdice na kraju. Sam biraš koje
> note vježbaš.
>
> Na hrvatskom i engleskom. Bez računa, bez oglasa, nikakvi podaci ne napuštaju uređaj.
>
> Android robot preuzet je ili izmijenjen iz djela koje je stvorio i podijelio Google te se
> koristi u skladu s uvjetima licencije Creative Commons 3.0 Attribution.

## App content

### Privacy policy
Required. Link to the hosted copy of `privacy-policy.md`.

### Ads
Does the app contain ads? **No.**

### App access
No login or restricted parts. **All functionality is available without special access.**

### Content rating (IARC questionnaire)
Category: **Utility, Productivity, Communication, or Other**. Answer No to every content
question (violence, sexuality, language, controlled substances, gambling, user interaction,
location sharing, purchases). Expected rating: Everyone / PEGI 3.

### Target audience and content
Target age groups: **6-8 and 9-12** (add 13-15 and 16-17 if you also want to reach older
students; adding 18+ makes it a "mixed audience" app). The app is designed for children, so
the **Families policy applies**: it already has no ads, no data collection, no social features
and no links out of the app, which is what the policy checks. Complete the Families
self-certification in the Target audience section and keep the privacy policy linked. The
optional "Teacher Approved" badge can be applied for after launch.

### News app
**No.**

### Data safety

- Does your app collect or share any of the required user data types? **No.**
  Play defines collection as transmitting data off the device. Microphone audio is processed
  on the device in memory and discarded; nothing is stored or sent, so no data type is
  collected or shared. Settings, the chosen practice notes and the child's tunes stay in the
  app's private storage on the device.
- Encryption in transit and deletion requests are not asked when nothing is collected.
  Uninstalling removes everything.

Resulting public label: "No data collected" and "No data shared with third parties".

### Government apps
**No.**

### Financial features
**None** (no financial features).

### Health
**No health features.**

### Advertising ID
Does your app use advertising ID? **No.** The manifest does not declare
`com.google.android.gms.permission.AD_ID` and no ads SDK is included.

### Foreground service permissions (declaration for Android 14+)

The app declares `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

- Foreground service type: **Media playback**.
- Description to enter: "The metronome keeps clicking while the screen is off or another app
  (for example sheet music) is in front. A notification shows the current tempo and offers a
  Stop action. The service runs only while the metronome is playing and stops itself when
  playback ends."
- A link to a short video demonstrating the feature is required: record starting the
  metronome, pressing Home, showing the notification, and stopping from it.

### Photo and video permissions
Not requested. **No.**

## Permissions declared in the manifest

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Chromatic tuner and the flute's "Listen to me" (runtime permission, requested when the Tuner tab opens or when "Listen to me" is first pressed) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Metronome keeps playing in the background |
| `POST_NOTIFICATIONS` | Playback notification with Stop (runtime permission on Android 13+) |

None of these needs a separate sensitive-permission declaration form, apart from the
foreground service type above. The Notes tab needs no permission: it only plays tones through
the speaker. The Flute tab plays tones too and needs `RECORD_AUDIO` only for "Listen to me".

## Testing requirement for newer personal developer accounts

Personal accounts created after November 2023 must run a closed test with at least 12 testers
for 14 days before applying for production access. Plan the closed track first.
