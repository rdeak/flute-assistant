// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// Every word the site shows, for one language. A new language is a new file that satisfies this
// type (TypeScript names any missing text) plus a line in ./index.ts.

export type FeatureId = 'flute' | 'metronome' | 'tuner' | 'notes';

export interface Feature {
  id: FeatureId;
  /** The tab's name in the app, in this language. */
  name: string;
  title: string;
  text: string;
  points: string[];
  shotAlt: string;
}

export interface Strings {
  meta: { title: string; description: string; ogImageAlt: string };
  skipLink: string;
  languageMenu: string;
  /** The small Play button in the header. */
  getApp: string;
  hero: {
    eyebrow: string;
    title: string;
    lead: string;
    chips: string[];
    platform: string;
    shotAlt: string;
  };
  badgeAlt: string;
  features: { title: string; lead: string; items: Feature[] };
  parents: {
    title: string;
    lead: string;
    items: { icon: string; title: string; text: string }[];
    privacyLink: string;
  };
  teachers: { title: string; text: string; share: string; shareText: string; copied: string };
  faq: { title: string; items: { q: string; a: string }[] };
  cta: { title: string; text: string; qr: string; qrAlt: string };
  footer: {
    madeBy: string;
    contact: string;
    privacy: string;
    home: string;
    playTrademark: string;
    robot: string;
  };
  privacy: { title: string; description: string; back: string };
  /** The banner a visitor whose browser asks for this language sees on another language's page. */
  suggest: { text: string; open: string; dismiss: string };
  notFound: { title: string; text: string; home: string };
  /** For search engines and AI tools: the structured data and llms.txt. */
  app: { description: string; features: string[] };
}
