// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// The website, flute-assistant.bertlab.hr: a static site that GitHub Pages serves
// (.github/workflows/site.yml). How it is built and how to add a language: README.md.
import { defineConfig, fontProviders } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// The one place the address is written; robots.txt, llms.txt, canonical and hreflang links,
// Open Graph and the sitemap all take it from here.
const site = 'https://flute-assistant.bertlab.hr';

/**
 * In `astro dev` an image's address is its path, size and wanted width, not its content, and Astro
 * serves it to be cached for a year. tools/play-screenshots.sh rewrites the screenshots under the
 * same names and sizes, so the browser went on showing the old ones (2026-11-11). Dev only: the build
 * names every image by its content.
 * @returns {import('vite').Plugin}
 */
function devImagesNotCached() {
  return {
    name: 'dev-images-not-cached',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url?.startsWith('/_image')) {
          const setHeader = res.setHeader.bind(res);
          res.setHeader = (name, value) =>
            setHeader(name, name.toLowerCase() === 'cache-control' ? 'no-store' : value);
        }
        next();
      });
    },
  };
}

export default defineConfig({
  site,
  // "preserve" builds src/pages/release/privacy-policy.astro as release/privacy-policy.html: the
  // address the app and the Play Console link to, which must keep working.
  build: { format: 'preserve', inlineStylesheets: 'always' },
  trailingSlash: 'always',
  fonts: [
    {
      // The app's font (OFL, docs/licenses/Nunito-OFL.txt), served from this site: no request to
      // Google. Variable, so one file covers every weight; latin-ext (č ć đ š ž) loads only on a
      // page that has those letters. The files come from @fontsource-variable/nunito.
      provider: fontProviders.local(),
      name: 'Nunito',
      cssVariable: '--font-nunito',
      fallbacks: ['ui-rounded', 'system-ui', 'sans-serif'],
      options: {
        variants: [
          {
            weight: '200 1000',
            style: 'normal',
            src: ['@fontsource-variable/nunito/files/nunito-latin-wght-normal.woff2'],
            unicodeRange: [
              'U+0000-00FF', 'U+0131', 'U+0152-0153', 'U+02BB-02BC', 'U+02C6', 'U+02DA', 'U+02DC',
              'U+0304', 'U+0308', 'U+0329', 'U+2000-206F', 'U+20AC', 'U+2122', 'U+2191', 'U+2193',
              'U+2212', 'U+2215', 'U+FEFF', 'U+FFFD',
            ],
          },
          {
            weight: '200 1000',
            style: 'normal',
            src: ['@fontsource-variable/nunito/files/nunito-latin-ext-wght-normal.woff2'],
            unicodeRange: [
              'U+0100-02BA', 'U+02BD-02C5', 'U+02C7-02CC', 'U+02CE-02D7', 'U+02DD-02FF', 'U+0304',
              'U+0308', 'U+0329', 'U+1D00-1DBF', 'U+1E00-1E9F', 'U+1EF2-1EFF', 'U+2020',
              'U+20A0-20AB', 'U+20AD-20C0', 'U+2113', 'U+2C60-2C7F', 'U+A720-A7FF',
            ],
          },
        ],
      },
    },
  ],
  integrations: [
    sitemap({
      // The legacy privacy address is a copy whose canonical is /privacy/, and 404 is no page.
      filter: (page) => !page.includes('/release/') && !page.includes('/404'),
      i18n: {
        defaultLocale: 'en',
        locales: { en: 'en', hr: 'hr' },
      },
    }),
  ],
  // The privacy policy and the Play listing's pictures are read from ../docs (src/i18n/media.ts,
  // src/content.config.ts): the dev server may serve files from the repository above site/.
  vite: {
    server: { fs: { allow: ['..'] } },
    plugins: [devImagesNotCached()],
  },
});
