// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

import type { Strings } from './types';

// British English, like the app. The features describe the app as the screenshots show it (they come
// from the current app, tools/play-screenshots.sh): when a tab changes, both change together.
export const en: Strings = {
  meta: {
    title: 'Flute Assistant – flute tuner, metronome and notes for kids',
    description:
      'A free Android app for children learning the flute: a digital flute, a metronome with animals, a flute tuner, a note reading game and a flute fingering chart. No ads, works offline.',
    ogImageAlt:
      'Flute Assistant: the Android robot playing the flute, with the words Flute, metronome, tuner and notes.',
  },
  skipLink: 'Skip to content',
  languageMenu: 'Language',
  getApp: 'Get the app',
  hero: {
    eyebrow: 'Free for Android',
    title: 'The flute app for kids who are just starting',
    lead: 'Play a tune, keep the beat, tune your flute, learn to read notes and find the fingers for every note. Five big, friendly screens for primary-school children, with almost nothing to read.',
    chips: ['Free', 'No ads', 'Works offline', 'English and Croatian'],
    platform: 'For Android phones and tablets',
    shotAlt: 'Flute Assistant on a tablet and a phone: a tune on a staff beside colourful note keys, and the metronome’s ring of animals.',
  },
  badgeAlt: 'Get it on Google Play',
  features: {
    title: 'Five screens, one flute friend',
    lead: 'Everything a young flute player reaches for at a lesson or at home, in one app.',
    items: [
      {
        id: 'flute',
        name: 'Flute',
        title: 'Play it, see it written, hear it back',
        text: 'Fifteen colourful keys in three rows, and a ♯♭ key for the sharps and flats. Hold a key to hear its note: the longer you hold it, the longer the note. Join two notes with ⌒ for a slur. Your tune is written on a real staff, with the note names underneath.',
        points: [
          'Hear your tune played back',
          'Press “Listen to me”, play the tune on your own flute and get a star for every note you get right, or a sign that says what to change',
          'Six little books keep six tunes',
        ],
        shotAlt:
          'The Flute screen: a short tune on a staff with the note names under it, fifteen colourful note keys and the ♯♭ and ⌒ keys.',
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
        text: 'Play a note and watch it float above or below its home ring. One line tells you what to change, like rolling the flute in a little, using gentler air or pushing the head joint in. Bring the note home for “Perfect!”.',
        points: [
          'Or pick a note, tap it to hear it and see its fingering on a drawing of the flute',
          'Tap a tip about the flute for four pictures on how to tune it',
          'No numbers to read',
        ],
        shotAlt:
          'The Tuner screen: the note to tune to in its home ring between two arrows, and under it the flute with the note’s fingering.',
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
      {
        id: 'fingers',
        name: 'Fingers',
        title: 'Which fingers for this note?',
        text: 'Pick a note with the letter keys, or its sharp or flat with ♯♭, and see it in every octave the flute plays: the note on the staff, its name, and the flute as you see it when you play, with the keys to press filled in. Tap a flute to tune that note.',
        points: [
          'Every key of a concert flute, the trill keys too',
          'Every note from middle C up three octaves',
          'Left hand and right hand marked on every flute',
        ],
        shotAlt:
          'The Fingers screen: G in three octaves, each on a small staff beside a drawing of the flute with the keys to press filled in, and the letter keys below.',
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
    text: 'Tell your pupils about Flute Assistant. The metronome keeps going while sheet music is open in another app, the tuner shows a beginner which way to go without a single number, and the fingering chart shows every note on the whole concert flute.',
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
        a: 'The concert (transverse) flute. The tuner and the fingering chart cover every note from middle C up three octaves, and the digital flute plays the first two. The metronome and the note reading game help any young musician.',
      },
      {
        q: 'Does it show flute fingerings?',
        a: 'Yes. The Fingers screen shows every note from middle C up three octaves on a drawing of the concert flute, with the keys to press filled in, and the tuner shows the fingering of the note being tuned.',
      },
      {
        q: 'How do I tune a flute with it?',
        a: 'Open the Tuner, choose A in Tune and play one long, steady A. If it floats above its ring, the tuner tells you to pull the head joint out a little; tap that tip for four pictures on tuning the flute. On other notes it says what to change, like rolling the flute in a little or using gentler air. When the note comes home, it says “Perfect!”.',
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
      'A free Android app for primary-school children learning the flute, with a digital flute, a metronome, a flute tuner, a note reading game and a flute fingering chart.',
    features: [
      'Digital flute with fifteen note keys, sharps and flats and slurs, that writes the tune on a staff',
      'Listen to me: follows the tune played on a real flute, gives a star for every right note and says what to change',
      'Metronome with five animal speeds and two to six beats in a bar',
      'Flute tuner with tips, fingerings and a guide to tuning, without numbers',
      'Two note reading games on the treble staff: Find (place the named note) and Hear (write a short tune by ear)',
      'Flute fingering chart: every note from middle C up three octaves on a drawing of the concert flute, every key included',
      'No ads, no account, no data collected, works offline',
    ],
  },
};
