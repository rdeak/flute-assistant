// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// /llms.txt (llmstxt.org): the site in plain Markdown for AI assistants, built from the same words
// as the pages so the two never disagree. Its Play link is tagged `llms`, so the Play Console shows
// visits that came through an assistant.
import type { APIRoute } from 'astro';
import { languages, language, pagePath, playUrl, defaultLang, APP_NAME, PUBLISHER, CONTACT_EMAIL } from '../i18n';

export const GET: APIRoute = ({ site }) => {
  const t = language(defaultLang).strings;
  const abs = (path: string) => new URL(path, site).href;
  const lines = [
    `# ${APP_NAME}`,
    '',
    `> ${t.app.description}`,
    '',
    `${t.hero.chips.join('. ')}. ${t.hero.platform} (Android 7.0 or later). Made in Croatia by ${PUBLISHER}`,
    '',
    '## Features',
    '',
    ...t.features.items.map((f) => `- **${f.name}**: ${f.text} ${f.points.join('. ')}.`),
    '',
    '## For parents',
    '',
    ...t.parents.items.map((p) => `- **${p.title}**: ${p.text}`),
    '',
    '## Questions and answers',
    '',
    ...t.faq.items.flatMap((item) => [`### ${item.q}`, '', item.a, '']),
    '## Links',
    '',
    `- [${APP_NAME} on Google Play](${playUrl(defaultLang, 'llms')})`,
    ...languages.map((l) => `- [${APP_NAME} (${l.name})](${abs(pagePath(l.code, 'home'))})`),
    `- [Privacy policy](${abs(pagePath(defaultLang, 'privacy'))})`,
    `- Contact: ${CONTACT_EMAIL}`,
    '',
  ];
  return new Response(lines.join('\n'), { headers: { 'Content-Type': 'text/markdown; charset=utf-8' } });
};
