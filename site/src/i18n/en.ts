// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

import type { Strings } from './types';

// British English, like the app. The features describe the app as the screenshots show it (they come
// from the current app, tools/play-screenshots.sh): when a tab changes, both change together.
export const en: Strings = {
  meta: {
    title: 'Flute Assistant – flute tuner, metronome and notes for kids',
    description:
      'A free Android app for children learning the flute: a digital flute, a metronome with animals, a flute tuner and a note reading game. No ads, works offline.',
    ogImageAlt:
      'Flute Assistant: the Android robot playing the flute, with the words Flute, metronome, tuner and notes.',
  },
  skipLink: 'Skip to content',
  languageMenu: 'Language',
  getApp: 'Get the app',
  hero: {
    eyebrow: 'Free for Android',
    title: 'The flute app for kids who are just starting',
    lead: 'Play a tune, keep the beat, tune your flute and learn to read notes. Four big, friendly screens for primary-school children, with almost nothing to read.',
    chips: ['Free', 'No ads', 'Works offline', 'English and Croatian'],
    platform: 'For Android phones and tablets',
    shotAlt: 'Flute Assistant on a tablet and a phone: a tune on a staff beside colourful note keys, and the metronome’s ring of animals.',
  },
  badgeAlt: 'Get it on Google Play',
  features: {
    title: 'Four screens, one flute friend',
    lead: 'Everything a young flute player reaches for at a lesson or at home, in one app.',
    items: [
      {
        id: 'flute',
        name: 'Flute',
        title: 'Play it, see it written, hear it back',
        text: 'Fifteen colourful keys in three rows. Hold a key to hear its note: the longer you hold it, the longer the note. Your tune is written on a real staff, with the note names underneath.',
        points: [
          'Hear your tune played back',
          'Press “Listen to me”, play the tune on your own flute and get a star for every note you get right',
          'Six little books keep six tunes',
        ],
        shotAlt:
          'The Flute screen: a short tune on a staff with the note names under it, and fifteen colourful note keys.',
      },
      {
        id: 'metronome',
        name: 'Metronome',
        title: 'From snail to cheetah',
        text: 'A tempo ring with five animals. Drag the animal round, or tap one to jump to its speed. Pick two to six beats in a bar and press play.',
        points: [
          'The animal hops on every beat you hear',
          'Keeps clicking while sheet music is open in another app',
          'Stop it from the notification',
        ],
        shotAlt:
          'The Metronome screen: a tempo ring with a snail, a turtle, a duck, a rabbit and a cheetah, and the beats of a bar as bubbles.',
      },
      {
        id: 'tuner',
        name: 'Tuner',
        title: 'Bring the note home',
        text: 'Play a note and watch it float above or below its home ring. The tuner says “A little higher” or “A little lower” and gives a tip, like rolling the flute in or using gentler air. Bring the note home for “Perfect!”.',
        points: ['Or pick a note, hear it and see its fingering', 'No numbers to read'],
        shotAlt: 'The Tuner screen: the note to tune to in its home ring, a Hear button and the note’s fingering.',
      },
      {
        id: 'notes',
        name: 'Notes',
        title: 'Find the note, write the tune',
        text: 'Two games on a real staff. In Find, a note name appears: slide your finger to its place and let go. In Hear, the phone plays a short tune and you write it down by ear, note by note.',
        points: [
          'Ten notes a round, flames for hits in a row and a trophy at the end',
          'Choose which notes to practise',
          'Fifteen notes across the flute’s first two octaves',
        ],
        shotAlt: 'The Notes screen: the Find and Hear games, a note found on the staff, and stars for the round so far.',
      },
    ],
  },
  parents: {
    title: 'For parents: safe, simple and free',
    lead: 'Flute Assistant is made for children, so we left out the things parents worry about.',
    items: [
      { icon: '🎁', title: 'Free, really', text: 'No price, no in-app purchases and no subscription.' },
      { icon: '🚫', title: 'No ads', text: 'Nothing to tap by mistake and no links out of the app.' },
      { icon: '🙂', title: 'No account', text: 'No sign-up and no password. Open it and play.' },
      {
        icon: '🔒',
        title: 'Nothing leaves the phone',
        text: 'The microphone listens only on the tuner and on “Listen to me”. Nothing is recorded or sent.',
      },
      { icon: '📴', title: 'Works offline', text: 'No internet needed: at home, at music school or on the way there.' },
      { icon: '🧒', title: 'Made for young children', text: 'Rated PEGI 3, with big buttons and hardly any text.' },
    ],
    privacyLink: 'Read the privacy policy',
  },
  teachers: {
    title: 'Do you teach the flute?',
    text: 'Tell your pupils about Flute Assistant. The metronome keeps going while sheet music is open in another app, and the tuner shows a beginner which way to go without a single number.',
    share: 'Share this page',
    shareText: 'Flute Assistant: a free flute app for kids who are just starting',
    copied: 'Link copied',
  },
  faq: {
    title: 'Questions parents ask',
    items: [
      {
        q: 'Is Flute Assistant free?',
        a: 'Yes. It is free to download and to use, with no ads, no in-app purchases and no subscription.',
      },
      {
        q: 'What age is it for?',
        a: 'It is made for primary-school children, roughly 6 to 12, who are starting the flute. Older beginners use it too, and so do parents who play along.',
      },
      {
        q: 'Which flute does it work with?',
        a: 'The concert (transverse) flute: the tuner, the fingerings and the fifteen notes follow the flute’s first two octaves. The metronome and the note reading game help any young musician.',
      },
      {
        q: 'How do I tune a flute with it?',
        a: 'Open the Tuner and play one long, steady note, such as A. If the note floats above its ring, the app says “A little lower” and gives a tip, like rolling the flute in a little or using gentler air. When the note comes home, it says “Perfect!”.',
      },
      {
        q: 'Does it need the internet?',
        a: 'No. Everything works offline, and the app never connects to the internet.',
      },
      {
        q: 'Why does it ask for the microphone?',
        a: 'The tuner and “Listen to me” need to hear your flute. The sound is analysed in the phone’s memory while you play and is never recorded, saved or sent anywhere.',
      },
      {
        q: 'Is there an iPhone or iPad version?',
        a: 'Not yet. Flute Assistant is for Android phones and tablets (Android 7.0 or newer), from Google Play.',
      },
      {
        q: 'Which languages does it speak?',
        a: 'English and Croatian. It follows the phone’s language, and the flag at the top of the screen switches it.',
      },
    ],
  },
  cta: {
    title: 'Ready to play?',
    text: 'Get Flute Assistant for free and play your first tune today.',
    qr: 'On a computer? Scan this code with your Android phone to open Google Play.',
    qrAlt: 'QR code that opens Flute Assistant on Google Play',
  },
  footer: {
    madeBy: 'Made in Croatia by Bertlab d.o.o.',
    contact: 'Contact',
    privacy: 'Privacy policy',
    home: 'Home',
    playTrademark: 'Google Play and the Google Play logo are trademarks of Google LLC.',
    robot:
      'The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.',
  },
  privacy: {
    title: 'Privacy policy – Flute Assistant',
    description:
      'How Flute Assistant and this website handle data: the app collects nothing, and nothing leaves your device.',
    back: 'Back to Flute Assistant',
  },
  suggest: { text: 'This page is also in English.', open: 'Read it in English', dismiss: 'No, thanks' },
  notFound: {
    title: 'Page not found',
    text: 'This page has flown away like a note that was too high. Let’s go back.',
    home: 'Go to the home page',
  },
  app: {
    description:
      'A free Android app for primary-school children learning the flute, with a digital flute, a metronome, a flute tuner and a note reading game.',
    features: [
      'Digital flute with fifteen note keys that writes the tune on a staff',
      'Listen to me: follows the tune played on a real flute and gives a star for every right note',
      'Metronome with five animal speeds and two to six beats in a bar',
      'Flute tuner with tips and fingerings, without numbers',
      'Two note reading games on the treble staff: Find (place the named note) and Hear (write a short tune by ear)',
      'No ads, no account, no data collected, works offline',
    ],
  },
};
