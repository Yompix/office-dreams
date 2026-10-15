'use strict';
/* Engine invariants: deterministic replay, offline rules, save/load, prestige, goals. */
const E = require('../src/engine.js');
let fails = 0, passes = 0;
const ok = (c, m) => { if (c) passes++; else { fails++; console.log('FAIL', m); } };
E.hooks.emit = () => {};

// 1. a random action log replays to the identical state
function randomLog(seed, n) {
  let r = seed >>> 0;
  const rnd = () => ((r = (Math.imul(r, 1664525) + 1013904223) >>> 0) / 4294967296);
  const acts = [['tap'], ['tap'], ['tap'], ['tap'], ['hire', 'prog'], ['hire', 'art'], ['ups', 'prog'], ['upy'], ['buy', 'coffee'], ['flr', 'f1'], ['new', 'puzzle', 'mini', 'Block Dreams'], ['new', 'plat', 'mini', ''], ['auto', true], ['clm', 'tap10'], ['clm', 'rel1'], ['dly', 'tap'], ['ev', 0], ['ev', 1], ['bst', 'turbo'], ['idea'], ['idea'], ['idea'], ['bug'], ['bug']];
  const log = [];
  for (let i = 0; i < n; i++) log.push([Math.floor(rnd() * 12)].concat(acts[Math.floor(rnd() * acts.length)]));
  return log;
}
for (let seed = 1; seed <= 20; seed++) {
  const log = randomLog(seed, 400);
  const a = E.newState(seed * 977), b = E.newState(seed * 977);
  E.syncDay(a, 20000); E.syncDay(b, 20000);
  // a: step by step like the client; b: one replay like the server
  for (const e of log) { E.advanceTicks(a, e[0]); E.applyAction(a, e.slice(1)); }
  E.advanceTicks(a, 37);
  E.replay(b, log, 37);
  ok(E.stateHash(a) === E.stateHash(b) && JSON.stringify(a) === JSON.stringify(b), 'replay determinism seed ' + seed);
}

// 2. offline: income yes, releases/events no, project capped below completion
{
  const s = E.newState(5);
  s.staff.prog.lvl = 5; s.staff.art.lvl = 5; s.staff.des.lvl = 5;
  s.games.push({ id: 1, name: 'X', genre: 'rpg', tier: 'mini', score: 8, total: 5000, rate: 900, earned: 0, active: true, day: 1 });
  E.startProject(s, 'puzzle', 'mini', 'Test');
  const cash0 = s.cash, rel0 = s.stats.released;
  E.advanceOffline(s, 3 * 3600);
  ok(s.stats.released === rel0, 'no releases offline');
  ok(s.project && s.project.pts < s.project.need, 'project waits for the player');
  ok(!s.pending, 'no events offline');
  ok(s.cash > cash0, 'passive income offline');
  const t = E.advanceOffline(s, 50 * 3600);
  ok(t === E.OFFLINE_CAP, 'offline capped at ' + E.OFFLINE_CAP);
}

// 3. save/load round trip
{
  const s = E.newState(9);
  E.replay(s, randomLog(3, 200), 10);
  const t = E.deserialize(E.serialize(s));
  ok(E.stateHash(t) === E.stateHash(s), 'serialize round trip');
  ok(E.decodeSave(E.encodeSave(s)).rng === s.rng, 'save code round trip');
}

// 4. prestige keeps permanent progress
{
  const s = E.newState(11);
  for (const f of E.FLOOR_ORDER) s.floors[f] = 1;
  s.stats.released = 40; s.goals.f5 = 2; s.gems = 7;
  const r = E.prestige(s);
  ok(r.ok && s.legacy === 1 && s.floors.f1 === 0 && s.stats.released === 40 && s.gems === 10 && s.goals.f5 === 2, 'prestige');
  E.checkGoals(s);
  ok(s.goals.ipo1 === 1, 'ipo goal unlocked');
}

// 5. a fresh game is winnable but coins stay scarce: a simple bot opens floor 1 within 2 h and floor 2 within 8 h of non-stop play
{
  const s = E.newState(42);
  E.syncDay(s, 20000);
  let sec = 0, f1At = 0;
  while (sec < 8 * 3600 && !s.floors.f2) {
    if (s.floors.f1 && !f1At) f1At = sec;
    if (!s.project) E.startProject(s, s.trend.hot, 'mini', '');
    for (let i = 0; i < 4; i++) E.doTap(s);
    for (const r of E.ROLES) if (s.staff[r].lvl === 0 && s.cash > E.ROLE[r].hire * 2) E.hire(s, r);
    const nf = E.nextFloor(s);
    if (nf && s.cash > E.FLOORS.find((f) => f.id === nf).price * 1.2) E.buyFloor(s, nf);
    if (s.pending) E.chooseEvent(s, 1);
    E.advance(s, 1);
    sec++;
  }
  ok(f1At > 0 && f1At <= 7200, 'floor 1 in ' + Math.round(f1At / 60) + ' min of play');
  ok(s.floors.f2 === 1, 'reach floor 2 in ' + Math.round(sec / 60) + ' min of play');
  ok(sec > 7200, 'floor 2 is not trivial: ' + Math.round(sec / 60) + ' min');
  console.log('  pacing: floor 1 after', Math.round(f1At / 60), 'min; floor 2 after', Math.round(sec / 60), 'min, released', s.stats.released, 'games');
}
// floor renovation: ★1..★5 per open floor, costs grow, bonuses apply, saved with the game
{
  const s = E.newState(9);
  s.cash = 1e9;
  ok(!E.renovate(s, 'f3').ok, 'a closed floor cannot be renovated');
  const t0 = E.tapPower(s).cash;
  ok(E.renovate(s, 'b').ok && s.flv.b === 1, 'basement ★1');
  ok(E.tapPower(s).cash > t0 * 1.05, 'basement stars add cash per tap');
  for (const id of ['f1', 'f2', 'f3', 'f4', 'f5']) s.floors[id] = 1;
  const sal0 = E.salaryOf(s, 'prog', 3), sales0 = E.salesMult(s), wp0 = E.wpTotal(s);
  E.renovate(s, 'f1'); E.renovate(s, 'f3'); E.renovate(s, 'f2');
  ok(E.salaryOf(s, 'prog', 3) < sal0 && E.salesMult(s) > sales0 && E.wpTotal(s) > wp0, 'salaries / sales / speed bonuses');
  const c1 = E.renoCost(s, 'f5');
  E.renovate(s, 'f5');
  ok(E.renoCost(s, 'f5') === c1 * 2, 'each star costs twice the previous one');
  for (const id of E.FLOOR_ORDER) while (E.renoLvl(s, id) < E.RENO_MAX) ok(E.renovate(s, id).ok, 'renovate ' + id);
  ok(E.renovate(s, 'f5').err === 'max' && E.renoStars(s) === 30, 'all floors at ★5');
  E.checkGoals(s);
  ok(['reno1', 'reno15', 'renomax'].every((g) => s.goals[g]), 'renovation goals reached');
  const back = E.deserialize(E.serialize(s));
  ok(back.flv.f5 === 5, 'stars survive save/load');
  const old = JSON.parse(E.serialize(E.newState(3))); delete old.flv;
  ok(E.deserialize(old).flv.b === 0, 'old saves get empty renovation');
  ok(E.applyAction(E.newState(4), ['reno', 'b']).err === 'cash', 'reno is a synced action');
}
// the city: coffee break, academy course, contract board, store promo — all on game-time timers
{
  const s = E.newState(21);
  s.cash = 1e6; s.energy = 3;
  const c0 = s.cash;
  ok(E.applyAction(s, ['cof']).ok && Math.abs(s.energy - (3 + E.maxEnergy(s) * 0.6)) < 1e-9 && s.cash === c0 - E.CITY.cofCost(s), 'coffee: +60% energy for a small bill');
  ok(E.applyAction(s, ['cof']).err === 'cooldown', 'coffee has a cooldown');
  E.advance(s, E.CITY.cofCd + 1);
  ok(E.applyAction(s, ['cof']).ok, 'coffee again after the cooldown');
  ok(E.applyAction(s, ['trn', 'prog']).err === 'hire_first', 'only hired staff can study');
  E.hire(s, 'prog');
  const cash0 = s.cash;
  ok(E.applyAction(s, ['trn', 'prog']).ok && s.cash === cash0 - E.CITY.trnCost('prog', 1), 'course paid');
  ok(E.applyAction(s, ['trn', 'prog']).err === 'trn_busy', 'one course at a time');
  E.advance(s, E.CITY.trnSec(1) + 2);
  ok(s.staff.prog.lvl === 2 && !s.city.trn, 'course finished by itself: level up');
  E.applyAction(s, ['trn', 'prog']);
  s.gems = 5;
  ok(E.applyAction(s, ['trd']).ok && s.staff.prog.lvl === 3 && s.gems === 3, 'finish a course for gems');
  ok(!s.city.offer, 'no contract before the first game');
  ok(E.applyAction(s, ['prm']).err === 'no_game', 'promo needs a game');
  E.startProject(s, 'puzzle', 'mini', 'A');
  while (s.project) E.advance(s, 5);
  E.advance(s, 2);
  ok(!!s.city.offer, 'a contract is offered after the first release');
  ok(E.applyAction(s, ['ctr']).ok && s.city.ctr && !s.city.offer, 'contract taken');
  const k = s.city.ctr, gems0 = s.gems, evs0 = s.stats.evs;
  E.startProject(s, k.genre, k.tier, 'B');
  s.staff.prog.lvl = 10; s.staff.art = { lvl: 10, mood: 100 }; s.staff.des = { lvl: 10, mood: 100 }; s.staff.qa = { lvl: 10, mood: 100 };
  while (s.project) E.advance(s, 5);
  ok(s.games[0].score >= k.min ? (s.gems >= gems0 + k.gems && s.stats.evs === evs0 + 1 && !s.city.ctr) : !!s.city.ctr, 'contract paid on a matching release');
  const r0 = s.games[0].rate;
  ok(E.applyAction(s, ['prm']).ok && s.games[0].rate > r0 * 1.3, 'promo boosts sales');
  ok(E.applyAction(s, ['prm']).err === 'cooldown', 'promo cooldown');
  E.advance(s, 400);
  if (s.city.offer) { E.applyAction(s, ['ctr']); E.advance(s, E.CITY.ctrSec + 2); ok(!s.city.ctr, 'an unfinished contract expires'); }
  const old = JSON.parse(E.serialize(E.newState(3))); delete old.city;
  ok(E.deserialize(old).city.cof === 0, 'old saves get a city');
  // client and server replay give the same city
  const a = E.newState(77), b = E.newState(77);
  a.cash = b.cash = 1e5;
  const acts = [[0, 'hire', 'prog'], [4, 'trn', 'prog'], [8, 'cof'], [3, 'new', 'plat', 'mini', 'Z']];
  E.replay(a, acts, 9000); E.replay(b, acts, 9000);
  ok(JSON.stringify(a.city) === JSON.stringify(b.city) && E.stateHash(a) === E.stateHash(b), 'city is deterministic');
}
console.log(`engine: ${passes} passed, ${fails} failed`);
process.exit(fails ? 1 : 0);
