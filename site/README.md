# Flute Assistant website

The app's website, **https://flute-assistant.bertlab.hr**: a landing page and the privacy policy, in
English (`/`, `/privacy/`) and Croatian (`/hr/`, `/hr/privacy/`). Its one job is to get a family who
searched for a metronome, a tuner or the flute for beginners to install the app from Google Play.

Static [Astro](https://astro.build) site, built by `.github/workflows/site.yml` and served by GitHub
Pages. No analytics, no cookies, nothing loaded from another company (the Nunito font is served from
the site).

## Commands

```sh
cd site
npm ci              # once
npm run dev         # http://localhost:4321, reloads on save
npm run build       # astro check (types) + the site in dist/
npm run preview     # serve dist/ as Pages will
```

Node 22.12 or newer (the workflow uses 24). The dev server shows the old privacy address as
`/release/privacy-policy/`; the build writes it as `release/privacy-policy.html`, as Pages serves it.

## Where things are

| What | Where |
|---|---|
| Every word on the site, per language | `src/i18n/en.ts`, `src/i18n/hr.ts` (shape: `src/i18n/types.ts`) |
| The languages, page paths, Play links | `src/i18n/index.ts` |
| The address (canonical, sitemap, robots, llms.txt) | `site` in `astro.config.mjs`, nowhere else |
| The privacy policy | `../docs/release/privacy-policy.md` (English, the text the Play Console links to) and `privacy-policy.<lang>.md` |
| Screenshots, icon, feature graphic | `../docs/release/play-listing/`, read as they are: a release that renews the store screenshots renews the site |
| Google Play badges | `src/assets/badges/` (Google's official artwork, unchanged) |
| The font's licence, published beside it | `public/Nunito-OFL.txt` (a copy of `../docs/licenses/Nunito-OFL.txt`) |
| Pages | `src/pages/[...lang]/index.astro`, `src/pages/[...lang]/privacy/index.astro` |
| The look | `src/styles/global.css` (the app's colours from `ui/theme/Color.kt`, day and night) |

## Adding a language

1. Copy `src/i18n/en.ts` to `src/i18n/<code>.ts` and translate it. TypeScript names anything missing.
2. Add the language to `languages` in `src/i18n/index.ts`, with Google's badge for it in
   `src/assets/badges/google-play-<code>.png`
   (`https://play.google.com/intl/en_us/badges/static/images/badges/<code>_badge_web_generic.png`).
3. Add it to the sitemap's `locales` in `astro.config.mjs`.
4. Translate the policy to `../docs/release/privacy-policy.<code>.md` (until then the page shows the
   English one). If the Play listing has screenshots in that language, they are picked up from
   `play-listing/screenshots/<code>/`; otherwise the English ones show.

The pages need no change.

## What not to break

- **`/release/privacy-policy.html` must keep working.** The shipped app (`credits_privacy_url`) and
  the Play Console link to `rdeak.github.io/flute-assistant/release/privacy-policy.html`, which
  GitHub redirects to the custom domain. `src/pages/release/privacy-policy.astro` renders the whole
  English policy there (not a redirect), with its canonical on `/privacy/`. Remove it only after the
  app and the Play Console both point at `/privacy/` and the old app versions are gone.
- **Only true facts in the structured data and FAQ.** No rating, review or download count until
  there are real ones. The words describe the app as the screenshots show it: after
  `tools/play-screenshots.sh` (and its `tablet` run) renews them, read the four features in
  `src/i18n/*.ts` against the new shots.
- **The Play links carry their tags.** `playUrl()` adds `utm_source=website` and
  `utm_campaign=<lang>-<placement>` (header, hero, cta, qr, and llms for `/llms.txt`), both as
  parameters and inside `referrer`. The Play Console shows them under Grow users > Store performance
  (filter by UTM source and campaign). Google does not document which of the two forms its reports
  read, so both are sent.
- **No tracking on the site itself.** The privacy policy's "This website" section says so.

## Going live (once)

Today GitHub Pages publishes the repository's `docs/` folder from `main` (the old Jekyll build),
and the privacy address lives there. In this order, the address never goes dark:

1. **DNS.** At the DNS host of `bertlab.hr`, add a CNAME record: `flute-assistant` pointing to
   `rdeak.github.io`.
2. **Verify the domain** in the GitHub account (profile Settings > Pages > Add a domain:
   `bertlab.hr`, then the TXT record it shows), so no other GitHub account can claim the name.
3. **Custom domain, still on the old source.** Repository Settings > Pages > Custom domain:
   `flute-assistant.bertlab.hr`, Save, and wait for the DNS check. The old pages now answer on the
   new domain, and `rdeak.github.io/flute-assistant/...` redirects there.
4. **Merge to `main`** (this pushes the site and the workflow). Mind `release.yml`: every push to
   `main` also runs it, and while `fluteAssistant.versionName` has no tag yet that push uploads the
   app to the Play internal track, tags it and publishes a GitHub Release. To launch the site
   first, put only `site/`, `.github/workflows/site.yml`, `.gitignore` and the `docs/` and
   README changes on `main`. The Site run this push starts will likely fail at its deploy step,
   since Pages is still on the old source: expected, step 6 runs it again.
5. **Switch the source.** Settings > Pages > Build and deployment > Source: **GitHub Actions**.
6. **Run the workflow.** Actions > Site > Run workflow (on `main`). Wait for the deploy job. From
   now on only the site is served: the other pages GitHub used to build from `docs/` (the release
   notes as HTML) are gone, and the files stay readable in the repository.
7. **Enforce HTTPS** in Settings > Pages once the certificate is issued.
8. **Check:**
   ```sh
   curl -sI https://rdeak.github.io/flute-assistant/release/privacy-policy.html | grep -i location
   curl -s https://flute-assistant.bertlab.hr/release/privacy-policy.html | grep -c "Privacy Policy"
   curl -s https://flute-assistant.bertlab.hr/hr/ | grep -o '<title>[^<]*'
   ```
9. **Search engines.** Add the domain to Google Search Console (a DNS TXT record) and submit
   `https://flute-assistant.bertlab.hr/sitemap-index.xml`; do the same in Bing Webmaster Tools,
   which also feeds Copilot and ChatGPT search.
10. **Later, with a release:** set the Play Console's privacy policy URL and the app's
    `credits_privacy_url` to `https://flute-assistant.bertlab.hr/privacy/`, and add the website to
    the Play listing's contact details.
