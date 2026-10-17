'use strict';
/* Studio Battle end-to-end: two real browsers against the real server find each other, fight, see the result.
 *   node test/e2e_pvp.js
 * Screenshots go to test/out/pvp_*.png
 */
// Playwright: a local install, or the one preinstalled in the cloud session image
const { chromium } = (() => { for (const p of ['playwright', '/opt/node-tools/node_modules/playwright', '/opt/npm-tools/node_modules/playwright']) { try { return require(p); } catch (e) { /* next */ } } throw new Error('playwright not found'); })();
const http = require('http');
const fs = require('fs');
const path = require('path');
const { build } = require('../server/config.js');
const { createApp } = require('../server/app.js');
const U = require('../server/users.js');
const { startMock } = require('./mock-telegram.js');

const OUT = path.join(__dirname, 'out');
fs.mkdirSync(OUT, { recursive: true });
const DEBUG_HTML = fs.readFileSync(path.join(__dirname, '../dist/debug.html'), 'utf8');
const FONT_CSS = "@font-face{font-family:'Press Start 2P';src:local('DejaVu Sans Mono');size-adjust:150%;}";
let fails = 0, passes = 0;
const ok = (c, m) => { if (c) passes++; else { fails++; console.log('  FAIL', m); } };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const mock = await startMock({});
  const cfg = build({
    BOT_TOKEN: '123456:TEST', BOT_USERNAME: 'od_test_bot', APP_SHORTNAME: 'play', DB_PATH: ':memory:', TG_API_BASE: mock.url, BOT_MODE: 'off',
    DEV_AUTH: '1', SESSION_SECRET: 'e2e', CHANNEL: '@od_news', PUBLIC_URL: '',
  });
  const { ctx, handler } = createApp(cfg, { log: () => {} });
  const server = http.createServer(handler);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' }).catch(() => chromium.launch());
  const errors = [];
  async function newPage(tag, w = 390, h = 844) {
    const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 2, hasTouch: true });
    page.on('pageerror', (e) => errors.push(tag + ': ' + e.message));
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(tag + ' console: ' + m.text()); });
    await page.route('**/*', (route) => {
      const u = route.request().url();
      if (u.startsWith(base + '/?') || u === base + '/') return route.fulfill({ status: 200, contentType: 'text/html', body: DEBUG_HTML });
      if (u.startsWith(base)) return route.continue();
      if (u.includes('fonts.googleapis.com')) return route.fulfill({ status: 200, contentType: 'text/css', body: FONT_CSS });
      return route.fulfill({ status: 200, contentType: 'application/javascript', body: '' });
    });
    return page;
  }
  const shot = (page, name) => page.screenshot({ path: path.join(OUT, 'pvp_' + name + '.png') });
  const ev = (page, code) => page.evaluate((c) => window.__ev(c), code);
  const ready = async (page) => {
    await page.waitForSelector('#tabs button', { timeout: 15000 });
    await sleep(600);
    await ev(page, "closeModal(true); UI.queue = []; lsSet('od2_intro','1'); tutStop(); TIPS.forEach((t) => tipDone(t.id));");
  };

  const A = await newPage('A');
  await A.goto(base + '/?dev=5001&name=Olena&lang=uk');
  await ready(A);
  const B = await newPage('B');
  await B.goto(base + '/?dev=5002&name=Max&lang=uk');
  await ready(B);
  ok(!!(await A.$('[data-act="pvp"]')), 'battle button on the office side bar');
  await A.click('[data-act="pvp"]');
  await sleep(900);
  await shot(A, '01_lobby');
  ok(!!(await A.$('.pv-find')), 'lobby open');
  await A.click('.pv-find');
  await sleep(700);
  await shot(A, '02_search');
  ok(await ev(A, 'PV.st') === 'search', 'A searching');
  await B.click('[data-act="pvp"]');
  await sleep(700);
  await B.click('.pv-find');
  await sleep(1200);
  ok(await ev(A, 'PV.st') === 'fight' && await ev(B, 'PV.st') === 'fight', 'both in the arena');
  ok(await ev(A, 'PV.v.op.kind') === 'live', 'live opponent');
  await shot(A, '03_countdown');
  await sleep(3200);
  // A taps fast, B slower
  const tapA = await A.$('.pv-tap'), tapB = await B.$('.pv-tap');
  const ba = await tapA.boundingBox(), bb = await tapB.boundingBox();
  const t0 = Date.now();
  let i = 0;
  while (Date.now() - t0 < 9000) {
    await A.mouse.click(ba.x + ba.width / 2 + (i % 5) * 6, ba.y + ba.height / 2);
    if (i % 2 === 0) await B.mouse.click(bb.x + bb.width / 2, bb.y + bb.height / 2);
    if (i === 20) await B.click('[data-pv="emo:1"]');
    i++;
    await sleep(70);
  }
  await sleep(500);
  await shot(A, '04_fight');
  ok((await ev(A, 'PV.n')) > 40, 'taps counted: ' + await ev(A, 'PV.n'));
  ok((await ev(A, 'PV.v.op.n')) > 10, 'sees the opponent progress live: ' + await ev(A, 'PV.v.op.n'));
  // fast-forward the rest of the fight on the server clock is not possible with the real clock: wait it out
  await sleep(23000);
  await sleep(1500);
  ok(await ev(A, 'PV.st') === 'result', 'A sees the result');
  ok(await ev(A, 'PV.v.res.win') === 1, 'A won');
  ok(await ev(B, 'PV.v.res.win') === -1, 'B lost');
  await sleep(900);
  await shot(A, '05_result_win');
  await shot(B, '06_result_lose');
  ok((await ev(A, 'S.cash')) >= 150, 'coins arrived in the game: ' + await ev(A, 'S.cash'));
  await A.click('[data-pv="lobby"]');
  await sleep(1000);
  await shot(A, '07_lobby_after');
  ok((await A.$$('.pv-topl .board li')).length >= 1, 'top shows the winner');
  await A.click('[data-pv="close"]');
  await sleep(500);
  ok(await ev(A, 'UI.pvp') === false && !(await A.$('#pvp:not([hidden])')), 'arena closed');

  // friend challenge link: a new player opens the game through duel_<code>
  const code = U.getUser(ctx, 5001).ref_code;
  const C = await newPage('C');
  await C.goto(base + '/?dev=5003&name=Ira&lang=uk&start=duel_' + code);
  await C.waitForSelector('#tabs button', { timeout: 15000 });
  await sleep(2500);
  await ev(C, "if (UI.modal && UI.modal.id === 'welcome') closeModal(true)");
  await sleep(700);
  await shot(C, '08_duel_invite');
  ok(!!(await C.$('[data-act^="pvduel:"]')), 'duel invite shown');
  ok(U.getUser(ctx, 5003).ref1 === 5001, 'the duel link also counted as an invite');
  await C.click('[data-act^="pvduel:"]');
  await sleep(1500);
  ok(await ev(C, 'PV.v && PV.v.op.kind') === 'friend', 'fights the friend recording');
  await shot(C, '09_duel_fight');

  // demo (no server): training bot
  const D = await newPage('D');
  await D.route('**/api/**', (r) => r.fulfill({ status: 404, body: '' }));
  await D.goto(base + '/?lang=uk');
  await sleep(2500);
  console.log('  demo mode:', await ev(D, 'NET.mode'));

  console.log(errors.length ? 'page errors:\n' + errors.join('\n') : 'no page errors');
  ok(errors.length === 0, 'no page errors');
  console.log(`e2e pvp: ${passes} passed, ${fails} failed`);
  await browser.close();
  await new Promise((r) => server.close(r));
  ctx.db.close();
  await mock.close();
  process.exit(fails ? 1 : 0);
}
main().catch((e) => { console.error(e); process.exit(1); });
