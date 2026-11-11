// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// The Play listing's pictures (docs/release/play-listing), which the site shows as they are:
// a release that renews the screenshots renews the site.
import type { ImageMetadata } from 'astro';
import type { FeatureId, Lang } from './index';
import { defaultLang } from './index';
import icon from '../../../docs/release/play-listing/icon-512.png';
import featureGraphic from '../../../docs/release/play-listing/feature-graphic-1024x500.png';

export { icon, featureGraphic };

const shots = import.meta.glob<ImageMetadata>(
  '../../../docs/release/play-listing/screenshots/*/*.jpg',
  { eager: true, import: 'default' },
);

const tabletShots = import.meta.glob<ImageMetadata>(
  '../../../docs/release/play-listing/screenshots-tablet/10-inch/*/*.jpg',
  { eager: true, import: 'default' },
);

const order: Record<FeatureId, string> = {
  flute: '1-flute',
  metronome: '2-metronome',
  tuner: '3-tuner',
  notes: '4-notes',
};

/** The phone screenshot of a tab in a language, or in English when that language has none. */
export function screenshot(lang: Lang, id: FeatureId): ImageMetadata {
  const key = (l: string) => `../../../docs/release/play-listing/screenshots/${l}/${order[id]}.jpg`;
  const shot = shots[key(lang)] ?? shots[key(defaultLang)];
  if (!shot) throw new Error(`No screenshot for ${id} in ${lang} or ${defaultLang}`);
  return shot;
}

/** The 10-inch tablet screenshot (sideways) of a tab, or the English one. */
export function tabletScreenshot(lang: Lang, id: FeatureId): ImageMetadata {
  const key = (l: string) =>
    `../../../docs/release/play-listing/screenshots-tablet/10-inch/${l}/${order[id]}.jpg`;
  const shot = tabletShots[key(lang)] ?? tabletShots[key(defaultLang)];
  if (!shot) throw new Error(`No tablet screenshot for ${id} in ${lang} or ${defaultLang}`);
  return shot;
}
