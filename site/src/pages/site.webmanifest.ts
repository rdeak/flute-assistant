// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

// The web manifest: the app's icon and colours when the site is pinned to a home screen.
import type { APIRoute } from 'astro';
import { getImage } from 'astro:assets';
import { APP_NAME } from '../i18n';
import { icon } from '../i18n/media';

export const GET: APIRoute = async () => {
  const icons = await Promise.all(
    [192, 512].map(async (size) => {
      const image = await getImage({ src: icon, width: size, height: size, format: 'png' });
      return { src: image.src, sizes: `${size}x${size}`, type: 'image/png' };
    }),
  );
  const manifest = {
    name: APP_NAME,
    short_name: APP_NAME,
    start_url: '/',
    display: 'browser',
    background_color: '#EAF4FF',
    theme_color: '#EAF4FF',
    icons,
  };
  return new Response(JSON.stringify(manifest, null, 2), {
    headers: { 'Content-Type': 'application/manifest+json' },
  });
};
