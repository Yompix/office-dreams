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
  const acts = [['tap'], ['tap'], ['tap'], ['tap'], ['hire', 'prog'], ['hire', 'art'], ['ups', 'prog'], ['upy'], ['buy', 'coffee'], ['flr', 'f1'], ['new', 'puzzle', 'mini', 'Block Dreams'], ['new', 'plat', 'mini', ''], ['auto', true], ['clm', 'tap10'], ['clm', 'rel1'], ['dly', 'tap'], ['ev', 0], ['ev', 1], ['bst', 'turbo'], ['idea'], ['idea'], ['idea']];
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

// 5. a fresh game is winnable: a simple bot reaches floor 2 within 2 game hours of real play
{
  const s = E.newState(42);
  E.syncDay(s, 20000);
  let sec = 0;
  while (sec < 7200 && !s.floors.f2) {
    if (!s.project) E.startProject(s, s.trend.hot, 'mini', '');
    for (let i = 0; i < 4; i++) E.doTap(s);
    for (const r of E.ROLES) if (s.staff[r].lvl === 0 && s.cash > E.ROLE[r].hire * 2) E.hire(s, r);
    const nf = E.nextFloor(s);
    if (nf && s.cash > E.FLOORS.find((f) => f.id === nf).price * 1.2) E.buyFloor(s, nf);
    if (s.pending) E.chooseEvent(s, 1);
    E.advance(s, 1);
    sec++;
  }
  ok(s.floors.f2 === 1, 'reach floor 2 in ' + Math.round(sec / 60) + ' min of play');
  console.log('  pacing: floor 2 after', Math.round(sec / 60), 'min, released', s.stats.released, 'games');
}
console.log(`engine: ${passes} passed, ${fails} failed`);
process.exit(fails ? 1 : 0);
