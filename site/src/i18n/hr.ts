// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

import type { Strings } from './types';

// Croatian. Like the app, what the child does is said to the child (ti: "Drži tipku"); what a
// parent or teacher decides is said to them (vi: "Preuzmite"). Quotation marks „…“.
export const hr: Strings = {
  meta: {
    title: 'Flute Assistant – štimer, metronom i note za male flautiste',
    description:
      'Besplatna Android aplikacija za djecu koja uče flautu: digitalna flauta, metronom sa životinjama, štimer i igra čitanja nota. Bez oglasa, radi bez interneta.',
    ogImageAlt: 'Flute Assistant: Android robot svira flautu.',
  },
  skipLink: 'Preskoči na sadržaj',
  languageMenu: 'Jezik',
  getApp: 'Preuzmite',
  hero: {
    eyebrow: 'Besplatno za Android',
    title: 'Aplikacija za flautu za djecu koja tek počinju',
    lead: 'Odsviraj pjesmicu, drži ritam, ugodi flautu i nauči čitati note. Četiri velika, vesela ekrana za djecu u osnovnoj školi, gotovo bez čitanja.',
    chips: ['Besplatno', 'Bez oglasa', 'Radi bez interneta', 'Hrvatski i engleski'],
    platform: 'Za Android mobitele i tablete',
    shotAlt: 'Flute Assistant na tabletu i mobitelu: pjesmica na crtovlju uz šarene tipke s notama i metronomov krug sa životinjama.',
  },
  badgeAlt: 'Preuzmite na Google Playu',
  features: {
    title: 'Četiri ekrana, jedan prijatelj za flautu',
    lead: 'Sve što mali flautist treba na satu ili kod kuće, u jednoj aplikaciji.',
    items: [
      {
        id: 'flute',
        name: 'Flauta',
        title: 'Odsviraj, vidi zapisano, poslušaj',
        text: 'Petnaest šarenih tipki u tri reda. Drži tipku da čuješ ton: što je dulje držiš, to je nota dulja. Tvoja se pjesmica zapisuje na pravo crtovlje, s imenima nota ispod.',
        points: [
          'Poslušaj svoju pjesmicu',
          'Pritisni „Poslušaj me“, odsviraj pjesmicu na svojoj flauti i dobij zvjezdicu za svaku pogođenu notu',
          'Šest knjižica čuva šest pjesmica',
        ],
        shotAlt: 'Ekran Flauta: kratka pjesmica na crtovlju s imenima nota ispod i petnaest šarenih tipki.',
      },
      {
        id: 'metronome',
        name: 'Metronom',
        title: 'Od puža do geparda',
        text: 'Krug za tempo s pet životinja. Povuci životinju po krugu ili dodirni jednu da skočiš na njezinu brzinu. Odaberi od dvije do šest doba u taktu i pokreni.',
        points: [
          'Životinja skakuće na svaku dobu koju čuješ',
          'Kuca i dok su note otvorene u drugoj aplikaciji',
          'Zaustavljaš ga iz obavijesti',
        ],
        shotAlt:
          'Ekran Metronom: krug za tempo s pužem, kornjačom, patkom, zecom i gepardom, i dobe takta kao mjehurići.',
      },
      {
        id: 'tuner',
        name: 'Štimer',
        title: 'Vrati notu kući',
        text: 'Odsviraj ton i gledaj kako lebdi iznad ili ispod svoje kućice. Štimer kaže „Malo više“ ili „Malo niže“ i da savjet, na primjer da malo zakreneš flautu ili puhneš nježnije. Vrati notu kući za „Savršeno!“.',
        points: ['Ili odaberi notu, poslušaj je i pogledaj prstomet', 'Bez brojeva za čitanje'],
        shotAlt: 'Ekran Štimer: nota za ugađanje u svojoj kućici, tipka Slušaj i prstomet te note.',
      },
      {
        id: 'notes',
        name: 'Note',
        title: 'Nađi notu, zapiši melodiju',
        text: 'Dvije igre na pravom crtovlju. U igri Nađi pojavi se ime note: povuci prst do njezina mjesta i pusti. U igri Slušaj mobitel odsvira kratku melodiju, a ti je zapisuješ po sluhu, notu po notu.',
        points: [
          'Deset nota po krugu, plamičci za pogotke zaredom i pehar na kraju',
          'Sam biraš koje note vježbaš',
          'Petnaest nota kroz prve dvije oktave flaute',
        ],
        shotAlt: 'Ekran Note: igre Nađi i Slušaj, pronađena nota na crtovlju i zvjezdice za ovaj krug.',
      },
    ],
  },
  parents: {
    title: 'Za roditelje: sigurno, jednostavno i besplatno',
    lead: 'Flute Assistant je napravljen za djecu, pa smo izostavili sve zbog čega bi se roditelji brinuli.',
    items: [
      { icon: '🎁', title: 'Stvarno besplatno', text: 'Bez cijene, bez kupnje unutar aplikacije i bez pretplate.' },
      { icon: '🚫', title: 'Bez oglasa', text: 'Ništa za slučajno dodirnuti i nema poveznica izvan aplikacije.' },
      { icon: '🙂', title: 'Bez računa', text: 'Nema registracije ni lozinke. Dijete otvori aplikaciju i odmah svira.' },
      {
        icon: '🔒',
        title: 'Ništa ne napušta mobitel',
        text: 'Mikrofon sluša samo u štimeru i kod „Poslušaj me“. Ništa se ne snima i ništa se ne šalje.',
      },
      { icon: '📴', title: 'Radi bez interneta', text: 'Internet nije potreban: kod kuće, u glazbenoj školi ili na putu do nje.' },
      { icon: '🧒', title: 'Za malu djecu', text: 'Oznaka PEGI 3, velike tipke i gotovo bez teksta.' },
    ],
    privacyLink: 'Pročitajte pravila privatnosti',
  },
  teachers: {
    title: 'Predajete flautu?',
    text: 'Preporučite Flute Assistant svojim učenicima. Metronom kuca i dok su note otvorene u drugoj aplikaciji, a štimer početniku pokazuje kamo treba ići, bez ijednog broja.',
    share: 'Podijelite ovu stranicu',
    shareText: 'Flute Assistant: besplatna aplikacija za djecu koja tek uče flautu',
    copied: 'Poveznica je kopirana',
  },
  faq: {
    title: 'Česta pitanja roditelja',
    items: [
      {
        q: 'Je li Flute Assistant besplatan?',
        a: 'Da. Preuzimanje i korištenje su besplatni, bez oglasa, bez kupnje unutar aplikacije i bez pretplate.',
      },
      {
        q: 'Za koju je dob?',
        a: 'Napravljen je za djecu u osnovnoj školi, otprilike od 6 do 12 godina, koja tek počinju svirati flautu. Koriste ga i stariji početnici te roditelji koji sviraju s djecom.',
      },
      {
        q: 'S kojom flautom radi?',
        a: 'S koncertnom (poprečnom) flautom: štimer, prstomet i petnaest nota prate prve dvije oktave flaute. Metronom i igra čitanja nota pomoći će svakom malom glazbeniku.',
      },
      {
        q: 'Kako se njime ugađa flauta?',
        a: 'Otvorite Štimer i odsvirajte jedan dug, miran ton, na primjer A. Ako ton lebdi iznad svoje kućice, aplikacija kaže „Malo niže“ i da savjet, na primjer da malo zakrenete flautu ili puhnete nježnije. Kad se ton vrati kući, piše „Savršeno!“.',
      },
      {
        q: 'Treba li aplikaciji internet?',
        a: 'Ne. Sve radi bez interneta i aplikacija se nikad ne spaja na internet.',
      },
      {
        q: 'Zašto traži mikrofon?',
        a: 'Štimer i „Poslušaj me“ moraju čuti flautu. Zvuk se analizira u memoriji mobitela dok svirate i nikad se ne snima, ne sprema i ne šalje.',
      },
      {
        q: 'Postoji li verzija za iPhone ili iPad?',
        a: 'Zasad ne. Flute Assistant je za Android mobitele i tablete (Android 7.0 ili noviji), s Google Playa.',
      },
      {
        q: 'Na kojim je jezicima?',
        a: 'Na hrvatskom i engleskom. Prati jezik mobitela, a mijenja se i zastavicom na vrhu ekrana.',
      },
    ],
  },
  cta: {
    title: 'Spremni za sviranje?',
    text: 'Preuzmite Flute Assistant besplatno i odsvirajte prvu pjesmicu još danas.',
    qr: 'Na računalu ste? Skenirajte kod Android mobitelom i otvorit će se Google Play.',
    qrAlt: 'QR kod koji otvara Flute Assistant na Google Playu',
  },
  footer: {
    madeBy: 'Napravljeno u Hrvatskoj, Bertlab d.o.o.',
    contact: 'Kontakt',
    privacy: 'Pravila privatnosti',
    home: 'Početna',
    playTrademark: 'Google Play i logotip Google Play zaštitni su znakovi tvrtke Google LLC.',
    robot:
      'Android robot preuzet je ili izmijenjen iz djela koje je stvorio i podijelio Google te se koristi u skladu s uvjetima licencije Creative Commons 3.0 Attribution.',
  },
  privacy: {
    title: 'Pravila privatnosti – Flute Assistant',
    description:
      'Kako Flute Assistant i ova stranica postupaju s podacima: aplikacija ništa ne prikuplja i ništa ne napušta vaš uređaj.',
    back: 'Natrag na Flute Assistant',
  },
  suggest: { text: 'Ova stranica postoji i na hrvatskom.', open: 'Otvori na hrvatskom', dismiss: 'Ne, hvala' },
  notFound: {
    title: 'Stranica nije pronađena',
    text: 'Ova je stranica odletjela kao previsoka nota. Vratimo se.',
    home: 'Na početnu stranicu',
  },
  app: {
    description:
      'Besplatna Android aplikacija za djecu u osnovnoj školi koja uče flautu: digitalna flauta, metronom, štimer za flautu i igra čitanja nota.',
    features: [
      'Digitalna flauta s petnaest tipki koja pjesmicu zapisuje na crtovlje',
      'Poslušaj me: prati pjesmicu odsviranu na pravoj flauti i daje zvjezdicu za svaku pogođenu notu',
      'Metronom s pet životinja za brzinu i od dvije do šest doba u taktu',
      'Štimer za flautu sa savjetima i prstometom, bez brojeva',
      'Dvije igre čitanja nota na crtovlju s violinskim ključem: Nađi (smjesti notu po imenu) i Slušaj (zapiši kratku melodiju po sluhu)',
      'Bez oglasa, bez računa, bez prikupljanja podataka, radi bez interneta',
    ],
  },
};
