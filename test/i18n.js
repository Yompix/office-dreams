'use strict';
/* i18n checks: every language has the same keys and placeholders, and every key the game can ask for exists. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const E = require('../src/engine.js');

const root = path.join(__dirname, '..');
const ctx = { LANGS: {} };
vm.createContext(ctx);
for (const l of ['en', 'ru', 'uk']) vm.runInContext(fs.readFileSync(path.join(root, 'src/lang_' + l + '.js'), 'utf8'), ctx);
const { LANGS } = ctx;
let fails = 0;
const bad = (m) => { fails++; console.log('FAIL', m); };

const en = Object.keys(LANGS.en);
for (const l of ['ru', 'uk']) {
  for (const k of en) if (!(k in LANGS[l])) bad(`${l} missing ${k}`);
  for (const k of Object.keys(LANGS[l])) if (!(k in LANGS.en)) bad(`${l} has extra ${k}`);
  for (const k of en) {
    if (!(k in LANGS[l])) continue;
    const ph = (s) => (s.match(/\{\w+\}/g) || []).sort().join(',');
    if (ph(LANGS.en[k]) !== ph(LANGS[l][k])) bad(`${l} placeholders differ for ${k}: ${ph(LANGS.en[k])} vs ${ph(LANGS[l][k])}`);
    if (/'/.test(LANGS[l][k]) && l !== 'en') bad(`${l} straight apostrophe in ${k}`);
  }
}

// keys the engine can produce
const need = new Set();
for (const r of E.ROLES) { need.add(`role.${r}.name`); need.add(`role.${r}.perk`); }
for (const g of E.GENRE_ORDER) need.add('genre.' + g);
for (const t of E.TIER_ORDER) need.add('tier.' + t);
for (const f of E.FLOORS) for (const s of ['name', 'sub', 'perk']) need.add(`floor.${f.id}.${s}`);
for (const i of E.ITEMS) for (const s of ['name', 'perk']) need.add(`item.${i.id}.${s}`);
for (const b of E.BOOSTS) for (const s of ['name', 'perk']) need.add(`boost.${b.id}.${s}`);
for (const r of ['owner', 'director', 'lead', 'indie', 'freelancer']) need.add('rank.' + r);
for (const g of E.GOALS) need.add('goal.' + g.id);
for (const d of E.DAILY) need.add('daily.' + d.id);
for (const t of ['cash', 'rep', 'burnout', 'opportunity']) need.add('tag.' + t);
for (const k of ['play', 'checkin', 'task', 'ref1', 'ref2', 'ref_act']) need.add('src.' + k);
for (const k of ['channel', 'chat', 'x', 'wallet', 'play3', 'play14', 'streak3', 'streak7', 'invite1', 'invite3', 'invite10']) need.add('task.' + k);
for (const k of ['office', 'studio', 'quests', 'empire', 'drop']) need.add('tab.' + k);
for (const k of ['team', 'games', 'home', 'friends', 'tasks', 'top', 'shop']) need.add('sub.' + k);
for (const k of ['en', 'ru', 'uk']) need.add('lang.' + k);
for (const k of ['cash', 'gems', 'energy', 'owned', 'max', 'hire_first', 'prev_floor', 'locked', 'busy', 'unavailable', 'claimed', 'notyet']) need.add('err.' + k);
for (const id of E.EVENT_IDS) {
  const def = E.EVENTS[id];
  for (const s of ['who', 'title', 'text']) need.add(`ev.${id}.${s}`);
  def.choices.forEach((c, i) => {
    need.add(`ev.${id}.c${i}`);
    const src = c.run.toString();
    if (/_v: 'a'/.test(src)) { need.add(`ev.${id}.r${i}a`); need.add(`ev.${id}.r${i}b`); } else need.add(`ev.${id}.r${i}`);
  });
}
// log keys emitted by the engine
const engSrc = fs.readFileSync(path.join(root, 'src/engine.js'), 'utf8');
for (const m of engSrc.matchAll(/'((?:log|toast)\.[a-z_]+)'/g)) need.add(m[1]);
// literal keys used by the UI
const KEY_NS = /^(ui|u|res|net|tab|sub|boot|intro|next|office|buff|fin|daily|role|team|genre|tier|games|quests|goals|goal|log|tag|floor|item|boost|rank|empire|ipo|end|rel|off|hub|src|ci|risk|wallet|fr|task|top|shop|chal|set|demo|toast|err|lang|side)\.[a-z0-9_.]+$/;
for (const f of ['ui_core.js', 'ui_tabs.js', 'ui_main.js', 'net.js']) {
  const src = fs.readFileSync(path.join(root, 'src', f), 'utf8');
  for (const m of src.matchAll(/'([a-z0-9_]+\.[a-z0-9_.]+)'/g)) {
    const k = m[1];
    if (!KEY_NS.test(k) || k.endsWith('.') || k.endsWith('_')) continue;
    if (/\.(js|json|png|com|me|org|xyz)$/.test(k)) continue;
    need.add(k);
  }
}
for (const k of need) if (!(k in LANGS.en)) bad('en missing ' + k);
console.log(`i18n: ${en.length} keys × 3 languages, ${need.size} required keys checked, ${fails} problems`);
process.exit(fails ? 1 : 0);
