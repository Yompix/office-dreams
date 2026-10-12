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
console.log(`engine: ${passes} passed, ${fails} failed`);
process.exit(fails ? 1 : 0);
