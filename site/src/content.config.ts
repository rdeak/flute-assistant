// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// The privacy policy, one Markdown file per language, kept with the release docs:
// docs/release/privacy-policy.md is English (the text the Play Console links to) and
// privacy-policy.<code>.md each other language. The entry id is the language code.
import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';

const privacy = defineCollection({
  loader: glob({
    pattern: 'privacy-policy*.md',
    base: '../docs/release',
    generateId: ({ entry }) => entry.match(/^privacy-policy\.([a-z-]+)\.md$/)?.[1] ?? 'en',
  }),
});

export const collections = { privacy };
