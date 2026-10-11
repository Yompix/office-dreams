'use strict';
/* Every story event × every choice × several game states: the choice runs, the texts resolve in all languages,
 * money never goes negative and the outcome is deterministic (same seed → same result). */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const E = require('../src/engine.js');

const ctx = { LANGS: {}, module: undefined };
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/i18n.js'), 'utf8'), ctx);
for (const l of ['en', 'ru', 'uk']) vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/lang_' + l + '.js'), 'utf8'), ctx);
const { L, setLang } = ctx;

function stateFor(kind, seed) {
  const s = E.newState(seed);
  s.day = 8;
  if (kind !== 'poor') {
    s.cash = kind === 'rich' ? 500000 : 3000;
    s.earned = s.cash * 2;
    s.staff.prog.lvl = 3; s.staff.art.lvl = 2; s.staff.des.lvl = 2;
    s.games.unshift({ id: 1, name: 'Neon Run', genre: 'plat', tier: 'mini', score: 9.1, total: 3000, rate: 600, earned: 100, active: true, day: 5 });
    s.stats.released = 1; s.stats.best = 9.1;
    s.income3 = kind === 'rich' ? 6000 : 400;
  } else {
    s.cash = 0; s.energy = 0;
  }
  return s;
}
let problems = 0, runs = 0;
const bad = (m) => { problems++; if (problems < 25) console.log('FAIL', m); };
const check = (txt, what) => {
  if (!txt || /\{\w+\}/.test(txt) || /^ev\./.test(txt) || /undefined|NaN/.test(txt)) bad(what + ' → ' + JSON.stringify(txt));
};
for (const id of E.EVENT_IDS) {
  for (const kind of ['poor', 'mid', 'rich']) {
    for (let seed = 1; seed <= 6; seed++) {
      const base = stateFor(kind, seed * 7919);
      if (!E.EVENTS[id].when(base) && kind !== 'poor') continue;
      if (kind === 'poor' && !E.EVENTS[id].when(base)) continue;
      E.spawnEvent(base, id);
      const view = E.eventView(base);
      for (const lang of ['en', 'ru', 'uk']) {
        setLang(lang);
        check(L(view.k + '.who'), `${id} who ${lang}`);
        check(L(view.k + '.title'), `${id} title ${lang}`);
        check(L(view.k + '.text', view.p), `${id} text ${lang}`);
        view.choices.forEach((c) => check(L(view.k + '.c' + c.i, c.lp), `${id} c${c.i} ${lang}`));
      }
      view.choices.forEach((c) => {
        const s = JSON.parse(JSON.stringify(base));
        const s2 = JSON.parse(JSON.stringify(base));
        const r = E.chooseEvent(s, c.i);
        const r2 = E.chooseEvent(s2, c.i);
        runs++;
        if (c.disabled) { if (r.ok) bad(`${id} c${c.i} should be disabled (${kind})`); return; }
        if (!r.ok) { bad(`${id} c${c.i} failed in ${kind}`); return; }
        if (E.stateHash(s) !== E.stateHash(s2)) bad(`${id} c${c.i} not deterministic`);
        if (s.cash < 0) bad(`${id} c${c.i} negative cash ${s.cash}`);
        if (s.gems < 0) bad(`${id} c${c.i} negative gems`);
        for (const lang of ['en', 'ru', 'uk']) { setLang(lang); check(L(r.outcome.rk, r.outcome.p), `${id} r${c.i} ${lang}`); }
        if (s.pending) bad(`${id} still pending`);
        if (s.stats.evs !== base.stats.evs + 1) bad(`${id} evs not counted`);
      });
    }
  }
}
console.log(`events: ${E.EVENT_IDS.length} events, ${runs} choice runs, ${problems} problems`);
process.exit(problems ? 1 : 0);
