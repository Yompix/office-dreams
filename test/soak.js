'use strict';
/* Online soak: a real browser plays for N seconds like a person (irregular taps, upgrades, quests),
 * then we check that client and server agree, nothing was dropped and the player was not flagged as a bot.
 *   node test/soak.js [sec=150]
 */
const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');
const { build } = require('../server/config.js');
const { createApp } = require('../server/app.js');
const U = require('../server/users.js');
const E = require('../src/engine.js');
const { startMock } = require('./mock-telegram.js');

const SEC = +(process.argv.find((a) => /^sec=/.test(a)) || 'sec=150').slice(4);
const DEBUG_HTML = fs.readFileSync(path.join(__dirname, '../dist/debug.html'), 'utf8');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const mock = await startMock({});
  const cfg = build({ BOT_TOKEN: '1:T', BOT_USERNAME: 'od_test_bot', DB_PATH: ':memory:', TG_API_BASE: mock.url, BOT_MODE: 'off', DEV_AUTH: '1', SESSION_SECRET: 's' });
  const { ctx, handler } = createApp(cfg, { log: () => {} });
  const server = http.createServer(handler);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 400, height: 860 }, hasTouch: true });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.route('**/*', (route) => {
    const u = route.request().url();
    if (u.startsWith(base + '/?') || u === base + '/') return route.fulfill({ status: 200, contentType: 'text/html', body: DEBUG_HTML });
    if (u.startsWith(base)) return route.continue();
    return route.fulfill({ status: 200, body: '' });
  });
  await page.goto(base + '/?dev=777&name=Soak&lang=ru');
  await page.waitForSelector('.welcome');
  const ev = (c) => page.evaluate((x) => window.__ev(x), c);
  await page.click('[data-act="start_game"]');
  let seed = 12345;
  const rnd = () => ((seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0) / 4294967296);
  const t0 = Date.now();
  let taps = 0, acts = 0;
  while (Date.now() - t0 < SEC * 1000) {
    // housekeeping like a player: close modals, answer quests, keep a project running, spend money
    await ev(`(function(){
      if (UI.modal && UI.modal.id === 'event' && S.pending) { const v = eventView(S); const c = v.choices.find(x => !x.disabled); if (c) ACT.ev(c.i); }
      if (UI.modal && !UI.modal.sticky) closeModal();
      if (UI.modal && UI.modal.id && UI.modal.id.startsWith('chal:') && UI.chal) { /* a person would answer: */ }
      if (!S.project) netAct('new', S.trend.hot, tierUnlocked(S,'indie') ? 'indie' : 'mini', '');
      for (const g of GOALS) if (S.goals[g.id] === 1) netAct('clm', g.id);
      for (const d of DAILY) if (!S.dq.done[d.id] && dailyProgress(S, d) >= d.need) netAct('dly', d.id);
      for (const r of ROLES) { if (S.staff[r].lvl === 0 && S.cash > ROLE[r].hire * 1.5) netAct('hire', r); else if (S.staff[r].lvl > 0 && S.staff[r].lvl < 6 && S.cash > upCost(r, S.staff[r].lvl) * 2) netAct('ups', r); }
      const nf = nextFloor(S); if (nf) { const f = FLOORS.find(x => x.id === nf); if (S.cash > f.price * 1.3) netAct('flr', nf); }
      for (const it of ITEMS) if (!S.items[it.id] && S.cash > it.price * 2) netAct('buy', it.id);
      if (UI.tab !== 'office') go('office');
      render();
    })()`);
    acts++;
    // a burst of human taps
    const n = 3 + Math.floor(rnd() * 8);
    const b = await page.$('#workbtn');
    if (b) {
      const box = await b.boundingBox();
      for (let i = 0; i < n; i++) {
        await page.mouse.click(box.x + 30 + rnd() * (box.width - 60), box.y + 20 + rnd() * (box.height - 40));
        taps++;
        await sleep(90 + rnd() * 260 + (rnd() < 0.1 ? 600 : 0));
      }
    }
    await sleep(300 + rnd() * 1500);
  }
  await ev('netSync()');
  await sleep(2500);
  await ev('netSync()');
  await sleep(2500);
  const cl = await ev('({hash: stateHash(S), tk: S.tk, taps: S.stats.taps, rel: S.stats.released, cash: Math.round(S.cash), stats: NET.sc.stats, q: NET.sc.queue.length, pts: NET.eco.pts})');
  const s = U.loadState(ctx, 777);
  const u = U.getUser(ctx, 777);
  const srvHash = E.stateHash(s);
  console.log('client', JSON.stringify(cl));
  console.log('server', JSON.stringify({ hash: srvHash, tk: s.tk, taps: s.stats.taps, rel: s.stats.released, cash: Math.round(s.cash), pts: u.pts, risk: u.risk_state, riskScore: u.risk, note: u.risk_note }));
  let fails = 0;
  const ok = (c, m) => { if (!c) { fails++; console.log('FAIL', m); } };
  ok(cl.taps === s.stats.taps, 'taps agree');
  ok(cl.rel === s.stats.released, 'releases agree');
  ok(cl.stats.stale === 0, 'no stale drops');
  ok(cl.stats.replayFails === 0, 'no replay failures');
  ok(u.risk_state === 'ok', 'a human-like player is not flagged (risk ' + u.risk + ')');
  ok(errors.length === 0, 'no page errors: ' + errors.join('; '));
  ok(u.pts > 0 && u.pts <= cfg.eco.dailyD + 200, 'points within the daily curve: ' + u.pts);
  console.log(`soak ${SEC}s: ${taps} taps, ${acts} rounds, ${fails ? fails + ' FAILED' : 'all good'}`);
  await browser.close();
  server.close(); ctx.db.close(); await mock.close();
  process.exit(fails ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
