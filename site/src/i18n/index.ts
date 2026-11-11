// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// The languages the site is in, and the links every page builds from them.
//
// To add a language (say German): write ./de.ts satisfying Strings, add `de` below, add it to the
// sitemap's locales in astro.config.mjs, write docs/release/privacy-policy.de.md and, if Google has
// a German badge, add src/assets/badges/google-play-de.png. The pages need no change.
import type { ImageMetadata } from 'astro';
import { en } from './en';
import { hr } from './hr';
import type { Strings } from './types';
import badgeEn from '../assets/badges/google-play-en.png';
import badgeHr from '../assets/badges/google-play-hr.png';

export type { Strings, Feature, FeatureId } from './types';

export interface Language {
  code: string;
  /** The language's own name, as the language menu shows it. */
  name: string;
  /** Open Graph locale. */
  ogLocale: string;
  strings: Strings;
  /** Google's official "Get it on Google Play" badge in this language. */
  badge: ImageMetadata;
  /** Whether its text needs the latin-ext font file (č ć đ š ž), which its pages then preload. */
  extendedLatin: boolean;
}

/** In menu order. The first is the default: its pages have no prefix and it is x-default. */
export const languages = [
  { code: 'en', name: 'English', ogLocale: 'en_GB', strings: en, badge: badgeEn, extendedLatin: false },
  { code: 'hr', name: 'Hrvatski', ogLocale: 'hr_HR', strings: hr, badge: badgeHr, extendedLatin: true },
] as const satisfies readonly Language[];

export type Lang = (typeof languages)[number]['code'];

export const defaultLang: Lang = 'en';

export function language(code: Lang): Language {
  return languages.find((l) => l.code === code)!;
}

/** The pages each language has. */
export type Page = 'home' | 'privacy';

/** Site-relative path of a page in a language: '/', '/hr/', '/privacy/', '/hr/privacy/'. */
export function pagePath(lang: Lang, page: Page): string {
  const prefix = lang === defaultLang ? '/' : `/${lang}/`;
  return page === 'home' ? prefix : `${prefix}privacy/`;
}

/** getStaticPaths for a page that exists in every language: the default language at the root. */
export function langPaths() {
  return languages.map((l) => ({
    params: { lang: l.code === defaultLang ? undefined : l.code },
    props: { lang: l.code as Lang },
  }));
}

export const PACKAGE_ID = 'hr.bertlab.fluteassistant';

/**
 * Where a Play link sits, so the Play Console can tell which one brought a visit; `llms` is the
 * link in /llms.txt, the one an AI assistant quotes.
 */
export type Placement = 'header' | 'hero' | 'cta' | 'qr' | 'llms';

/**
 * The Play listing, tagged with where the visit came from (utm_source=website,
 * utm_campaign=<lang>-<placement>). The tags go twice: as top-level parameters, which the Play
 * Console's acquisition reports read from links to a listing, and encoded in `referrer`, which Play
 * hands to the installed app's Install Referrer. Play ignores what it does not use.
 */
export function playUrl(lang: Lang, placement: Placement): string {
  const url = new URL('https://play.google.com/store/apps/details');
  const tags = { utm_source: 'website', utm_campaign: `${lang}-${placement}` };
  url.searchParams.set('id', PACKAGE_ID);
  url.searchParams.set('hl', lang);
  for (const [key, value] of Object.entries(tags)) url.searchParams.set(key, value);
  url.searchParams.set('referrer', new URLSearchParams(tags).toString());
  return url.toString();
}

export const CONTACT_EMAIL = 'robert.deak@bertlab.hr';
export const PUBLISHER = 'Bertlab d.o.o.';
export const APP_NAME = 'Flute Assistant';
/** Spellings people search for that are not the name; structured data only, never on the page. */
export const APP_ALTERNATE_NAMES = ['Flute Assistent', 'FluteAssistant'];
