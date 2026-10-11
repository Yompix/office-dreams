'use strict';
/* End-to-end test in a real browser against the real server (DEV_AUTH, in-memory DB, fake Telegram API).
 *   node test/e2e.js [w=400] [h=860]
 * Screenshots go to test/out/e2e_*.png
 */
const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');
const { build } = require('../server/config.js');
const { createApp } = require('../server/app.js');
const U = require('../server/users.js');
const { startMock } = require('./mock-telegram.js');

const OUT = path.join(__dirname, 'out');
fs.mkdirSync(OUT, { recursive: true });
const W = +(process.argv.find((a) => /^w=/.test(a)) || 'w=400').slice(2);
const H = +(process.argv.find((a) => /^h=/.test(a)) || 'h=860').slice(2);
const DEBUG_HTML = fs.readFileSync(path.join(__dirname, '../dist/debug.html'), 'utf8');
const ART_HTML = fs.readFileSync(path.join(__dirname, '../dist/artifact.html'), 'utf8');
const FONT_CSS = "@font-face{font-family:'Press Start 2P';src:local('DejaVu Sans Mono');size-adjust:150%;}";

let fails = 0, passes = 0;
const ok = (c, m) => { if (c) passes++; else { fails++; console.log('  FAIL', m); } };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const mock = await startMock({});
  const cfg = build({
    BOT_TOKEN: '123456:TEST', BOT_USERNAME: 'od_test_bot', APP_SHORTNAME: 'play', DB_PATH: ':memory:', TG_API_BASE: mock.url, BOT_MODE: 'off',
    DEV_AUTH: '1', SESSION_SECRET: 'e2e', ADSGRAM_BLOCK_ID: '999', CHANNEL: '@od_news', CHANNEL_URL: 'https://t.me/od_news', CHAT_URL: 'https://t.me/od_chat', PUBLIC_URL: '',
  });
  const { ctx, handler } = createApp(cfg, { log: () => {} });
  const server = http.createServer(handler);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port;

  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' }).catch(() => chromium.launch());
  const errors = [];
  async function newPage(tag) {
    const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 2, hasTouch: true });
    page.on('pageerror', (e) => errors.push(tag + ': ' + e.message));
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(tag + ' console: ' + m.text()); });
    await page.route('**/*', (route) => {
      const u = route.request().url();
      if (u.startsWith(base + '/?') || u === base + '/') return route.fulfill({ status: 200, contentType: 'text/html', body: DEBUG_HTML });
      if (u.startsWith(base)) return route.continue();
      if (u.includes('fonts.googleapis.com')) return route.fulfill({ status: 200, contentType: 'text/css', body: FONT_CSS });
      if (u.includes('sad.adsgram.ai')) return route.fulfill({ status: 200, contentType: 'application/javascript', body: 'window.Adsgram={init:function(){return{show:function(){return new Promise(function(r){setTimeout(function(){r({done:true})},300)})}}}};' });
      return route.fulfill({ status: 200, contentType: 'application/javascript', body: '' });
    });
    return page;
  }
  const shot = (page, name) => page.screenshot({ path: path.join(OUT, 'e2e_' + name + '.png') });
  const ev = (page, code) => page.evaluate((c) => window.__ev(c), code);
  const click = async (page, sel) => { await page.click(sel); await sleep(250); };

  /* ---------------- online player (Ukrainian) ---------------- */
  const page = await newPage('p1');
  await page.goto(base + '/?dev=1001&name=Volodymyr&lang=uk');
  await page.waitForSelector('.welcome', { timeout: 15000 });
  await sleep(500);
  await shot(page, '01_welcome_uk');
  ok(await ev(page, 'NET.mode') === 'online', 'online mode');
  ok(await ev(page, 'LANG') === 'uk', 'ukrainian detected');
  await click(page, '[data-act="start_game"]');
  await shot(page, '02_games_new');
  ok(await ev(page, 'UI.tab') === 'studio', 'start leads to studio/games');
  await sleep(600);
  ok(await page.$('#coach .c-bub') !== null, 'tutorial bubble shown');
  await shot(page, '02b_tutorial');
  await click(page, '[data-act="genre:plat"]');
  await sleep(500);
  await click(page, '[data-act="start"]');
  await sleep(600);
  ok(await ev(page, 'COACH.i') === 2, 'tutorial advanced to the tap step: ' + await ev(page, 'COACH.i'));
  await shot(page, '02c_tutorial_tap');
  ok(await ev(page, '!!S.project'), 'project started');
  // tap like a human: irregular intervals
  const btn = await page.$('#workbtn');
  const box = await btn.boundingBox();
  for (let i = 0; i < 30; i++) {
    await page.mouse.click(box.x + box.width / 2 + (i % 7) * 4, box.y + box.height / 2 + (i % 3) * 3);
    await sleep(110 + ((i * 37) % 90));
  }
  await sleep(300);
  await shot(page, '03_office_tapping');
  await ev(page, 'tutStop()');
  // rewarded ad (fake ad network): energy refilled, cash and a few points
  ok(!!(await page.$('[data-act="ad"]')), 'ad button shown');
  const e0 = await ev(page, 'S.energy');
  await click(page, '[data-act="ad"]');
  await sleep(2500);
  ok((await ev(page, 'S.energy')) > e0 + 10, 'energy refilled by the ad: ' + e0 + ' -> ' + await ev(page, 'S.energy'));
  ok((await ev(page, 'NET.eco.ads.left')) === 7, 'ad counted');
  await shot(page, '03b_after_ad');
  const taps = await ev(page, 'S.stats.taps');
  ok(taps >= 15, 'taps registered: ' + taps);
  // the server confirms the progress
  await ev(page, 'netSync()');
  await sleep(1500);
  const srv = U.loadState(ctx, 1001);
  ok(srv && srv.stats.taps === taps, `server taps ${srv && srv.stats.taps} == client ${taps}`);
  const eco1 = await ev(page, 'NET.eco');
  ok(eco1 && eco1.pts > 0, 'points earned from play: ' + (eco1 && eco1.pts));

  // give cash on the server (like a Stars purchase) and let the client adopt it
  U.grantState(ctx, 1001, { cash: 50000, gems: 20 });
  await ev(page, 'netSync({full:true})');
  await sleep(1200);
  ok((await ev(page, 'S.cash')) > 40000, 'client adopted server grant');
  await ev(page, 'go("studio","team")'); await sleep(300);
  await click(page, '[data-act="sel:prog"]');
  await click(page, '[data-act="hire:prog"]');
  await click(page, '[data-act="sel:art"]');
  await click(page, '[data-act="hire:art"]');
  await click(page, '[data-act="sel:des"]');
  await click(page, '[data-act="hire:des"]');
  await click(page, '[data-act="ups:des"]');
  ok((await ev(page, 'hiredCount(S)')) === 3, 'hired 3');
  await shot(page, '04_team');
  await click(page, '[data-act="tab:empire"]');
  await click(page, '[data-act="flr:f1"]');
  await click(page, '[data-act="flr:f2"]');
  await click(page, '[data-act="buy:coffee"]');
  await click(page, '[data-act="buy:monitor2"]');
  await sleep(1200);
  await page.evaluate(() => document.querySelector('#main').scrollTo(0, 0));
  await shot(page, '05_empire');
  ok(await ev(page, 'S.floors.f2 === 1'), 'floors bought');
  // release the game quickly: client and server must agree
  await ev(page, 'S.project && (S.project.pts = S.project.need - 1)'); // local only → server will correct it
  await click(page, '[data-act="tab:office"]');
  await sleep(600);
  await ev(page, 'netSync()');
  await sleep(1500);
  const s1 = U.loadState(ctx, 1001);
  const cl = await ev(page, '({r: S.stats.released, p: S.project && Math.round(S.project.pts)})');
  ok(s1.stats.released === cl.r, `release state agrees after correction (srv ${s1.stats.released}, cl ${cl.r})`);
  if (await page.$('.modal')) await click(page, '[data-act="close"]');

  // event modal
  await ev(page, 'spawnEvent(S, "investor"), render(true), showEvent()');
  await sleep(500);
  await shot(page, '06_event');
  await click(page, '[data-act="ev:2"]');
  await shot(page, '07_outcome');
  await click(page, '[data-act="close"]');
  await ev(page, 'netSync()');
  await sleep(1200);

  // release modal look (purely visual, local)
  await ev(page, 'showRelease({game:{name:"Neon Run",genre:"plat",tier:"mini",score:8.7,total:4200},repGain:2.3,gemGain:0})');
  await sleep(900);
  await shot(page, '08_release');
  await click(page, '[data-act="close"]');

  // airdrop hub
  await click(page, '[data-act="tab:drop"]');
  await sleep(800);
  await shot(page, '09_drop_home');
  const ciBtn = await page.$('[data-act="checkin"]');
  ok(!!ciBtn, 'check-in button present');
  if (ciBtn) { await ciBtn.click(); await sleep(1200); }
  const eco2 = await ev(page, 'NET.eco');
  ok(eco2.checkin.done, 'checked in');
  await shot(page, '10_drop_checked');
  await click(page, '[data-act="sub:drop:token"]');
  await sleep(600);
  await shot(page, '10b_token');
  await page.evaluate(() => document.querySelector('#main').scrollTo(0, 700));
  await sleep(300);
  await shot(page, '10c_token_more');
  await ev(page, 'showHelp()');
  await sleep(400);
  await shot(page, '10d_help');
  await ev(page, 'closeModal()');
  await click(page, '[data-act="sub:drop:friends"]');
  await sleep(800);
  await shot(page, '11_friends');
  ok((await page.textContent('.linkbox code')).includes('t.me/od_test_bot/play?startapp=ref_'), 'invite link shown');
  await click(page, '[data-act="sub:drop:tasks"]');
  await sleep(800);
  await shot(page, '12_tasks');
  // channel task: not a member yet → error, then membership → claim
  await click(page, '[data-act="task:claim:channel"]');
  await sleep(700);
  mock.members.add(1001);
  await click(page, '[data-act="task:claim:channel"]');
  await sleep(900);
  ok((await ev(page, 'UI.hub.tasks.find(t=>t.id==="channel").done')), 'channel task claimed');
  await click(page, '[data-act="sub:drop:shop"]');
  await sleep(800);
  await shot(page, '13_shop');
  ok((await page.$$('.sku')).length === 5, 'shop items');

  /* ---------------- second player via invite link (English) ---------------- */
  const code = (await ev(page, 'NET.eco.ref.code'));
  const p2 = await newPage('p2');
  await p2.goto(base + '/?dev=1002&name=Alex&lang=en&start=ref_' + code);
  await p2.waitForSelector('.welcome', { timeout: 15000 });
  ok(await ev(p2, 'LANG') === 'en', 'english for p2');
  await click(p2, '[data-act="start_game"]');
  await ev(p2, 'tutStop()');
  const u2 = U.getUser(ctx, 1002);
  ok(u2 && u2.ref1 === 1001, 'referral linked');
  await ev(p2, 'go("drop","top")');
  await sleep(900);
  await shot(p2, '14_top_en');
  await click(p2, '[data-act="settings"]');
  await sleep(300);
  await shot(p2, '15_settings_en');
  await click(p2, '[data-act="lang:ru"]');
  await click(p2, '[data-act="close"]');
  await click(p2, '[data-act="tab:office"]');
  await sleep(500);
  await shot(p2, '16_office_ru');
  ok(await ev(p2, 'LANG') === 'ru', 'switched to russian');

  // friends list of p1 shows p2
  await click(page, '[data-act="sub:drop:friends"]');
  await ev(page, 'loadHub("friends", true)');
  await sleep(900);
  ok((await ev(page, 'UI.hub.friends.friends.length')) === 1, 'friend listed');
  await shot(page, '17_friends_list');

  // human check modal
  await ev(page, 'showChallenge({id:"x1",kind:"odd",cells:["🍎","🍎","🍎","🍏","🍎","🍎","🍎","🍎","🍎","🍎","🍎","🍎"]})');
  await sleep(500);
  await shot(page, '18_challenge');
  await ev(page, 'closeModal(true)');

  /* ---------------- outside Telegram without dev id ---------------- */
  const p3 = await newPage('p3');
  await p3.goto(base + '/');
  await p3.waitForSelector('#splash.err', { timeout: 10000 });
  await shot(p3, '19_no_telegram');
  ok((await p3.textContent('.sp-actions')).length > 0, 'open-in-telegram button');

  /* ---------------- demo artifact ---------------- */
  const p4 = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 2, hasTouch: true });
  p4.on('pageerror', (e) => errors.push('demo: ' + e.message));
  await p4.route('**/*', (route) => {
    const u = route.request().url();
    if (u === 'http://demo.test/') return route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body>' + ART_HTML + '</body></html>' });
    if (u.includes('fonts.googleapis.com')) return route.fulfill({ status: 200, contentType: 'text/css', body: FONT_CSS });
    return route.fulfill({ status: 404, body: '' });
  });
  await p4.goto('http://demo.test/');
  await p4.waitForSelector('.welcome', { timeout: 10000 });
  await shot(p4, '20_demo_welcome');
  await click(p4, '[data-act="start_game"]');
  await click(p4, '[data-act="start"]');
  const b4 = await (await p4.$('#workbtn')).boundingBox();
  for (let i = 0; i < 25; i++) { await p4.mouse.click(b4.x + 60 + i, b4.y + 40); await sleep(120 + (i * 29) % 80); }
  await sleep(4500);
  await shot(p4, '21_demo_office');
  await click(p4, '[data-act="go:drop:home"]');
  await sleep(500);
  await shot(p4, '22_demo_drop');

  ok(errors.length === 0, 'no page errors:\n    ' + errors.join('\n    '));
  await browser.close();
  await new Promise((r) => server.close(r));
  ctx.db.close();
  await mock.close();
  console.log(`e2e: ${passes} passed, ${fails} failed`);
  process.exit(fails ? 1 : 0);
}
main().catch((e) => { console.error(e); process.exit(1); });
