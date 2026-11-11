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
| Short description | `play-listing/text/<lang>/short-description.txt` |
| Full description | `play-listing/text/<lang>/full-description.txt` |
| App icon | `play-listing/icon-512.png` (512x512) |
| Feature graphic | `play-listing/feature-graphic-1024x500.png` (subtitle "Flute, metronome, tuner and notes", text in `tools/IconGen.java`) |
| Screenshots | `play-listing/screenshots/en/` and `hr/`: one per tab, 1080x1920 JPEG (9:16, the ratio Play asks for), made by `tools/play-screenshots.sh` on the emulator |
| Tablet screenshots | `play-listing/screenshots-tablet/7-inch/<lang>/` (1296x2304) for the 7-inch tablet slot and `10-inch/<lang>/` (2880x1620) for the 10-inch slot: one per tab, made by `tools/play-screenshots.sh tablet`, uploaded by hand |
| Category | App, **Education** (Store settings; this table said Music & Audio until 2026-11-11, the console never did). Tags: Education, Music & audio, Music instrument, Primary education ("Audio recorder" removed on 2026-11-11: a release build records nothing) |
| Languages | English (default) and Croatian; the UI follows the system language and Android 13+ per-app language settings. The Croatian listing translation takes the `text/hr/` words and the `hr/` screenshots so Croatian users see the listing in their language. |
| Contact email | Required; shown publicly |
| Website | `https://flute-assistant.bertlab.hr` (Store settings > Store listing contact details; set 2026-11-11, published at once, no review) |
| Privacy policy URL | Required. `https://flute-assistant.bertlab.hr/privacy/` once the website is live (`site/README.md`); until then the old `https://rdeak.github.io/flute-assistant/release/privacy-policy.html`, which keeps working |

The listing's words live in `play-listing/text/<lang>/` (`en`, `hr`), one file per Play field:
`short-description.txt` (80 characters at most), `full-description.txt` (4000) and
`whats-new-<version>.txt`, the release notes (500). Paste them from there; the files are the record of
what the store shows. Since 2.0.0 (2026-11-11) the copy is written for parents and children choosing
an app: playful, short sentences, one emoji per section, the app's own words ("Listen to me", "Hear",
the animals and the sweets). Keep it true to the build (every feature named must be in it), keep the
Android robot attribution as the description's last paragraph, and avoid emoji newer than 2021
(🪈 shows as an empty box on older phones).

## App content

### Privacy policy
Required. Link to the hosted copy of `privacy-policy.md`: the website's `/privacy/` page.

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
self-certification in the Target audience section and keep the privacy policy linked.

The Kids tab on Google Play shows only **Teacher Approved** apps. There is no form and no category
for it: teachers and children's media specialists rate apps that follow the Families policy and
target children (design, appeal, enrichment, age-appropriateness, ads and purchases), and Google
picks which apps they rate and when. The status is under Policy and programs > Programs > Teacher
Approved (`.../app/<id>/teacher-approved`): on 2026-11-11 it read "Awaiting review", with 6-8 and
9-12 declared. A listing that shows the app well (playful text, current screenshots) is what helps
with "appeal"; the category does not decide it. Store listing graphics with young characters on an
app not declared for under-13s are a rejection reason, so keep the audience and the art matched.

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
