/* Screenshot helpers for art work (dev only, not part of the game).
   Serves the BUILT dist/debug.html at http://od.test/ plus public/ files, seeds language/tutorial flags,
   rich(p) fills a lived-in demo studio, ev(p, code) runs code inside the page via window.__ev. */
const fs = require('fs');
const path = require('path');
let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { ({ chromium } = require('/opt/node-tools/node_modules/playwright')); }
const ROOT = path.join(__dirname, '../..');
const HTML = fs.readFileSync(path.join(ROOT, 'dist/debug.html'));
const DIR = path.join(ROOT, 'public') + '/';
async function open(ctx, lang) {
  const p = await ctx.newPage();
  p.on('pageerror', (e) => console.log('ERR', e.message));
  await ctx.route('**/*', (r) => {
    const u = new URL(r.request().url());
    if (u.hostname === 'od.test' && (u.pathname === '/' || u.pathname === '/index.html')) return r.fulfill({ body: HTML, contentType: 'text/html' });
    if (u.hostname === 'od.test' && fs.existsSync(DIR + u.pathname.slice(1)) && u.pathname.length > 1) return r.fulfill({ body: fs.readFileSync(DIR + u.pathname.slice(1)) });
    if (u.hostname === 'od.test') return r.fulfill({ status: 404, body: '' });
    return r.continue();
  });
  await p.addInitScript((lang) => {
    if (localStorage.getItem('__seeded')) return;
    localStorage.setItem('__seeded', '1');
    localStorage.setItem('od_lang', lang); localStorage.setItem('od2_lang', lang);
    localStorage.setItem('od2_intro', '1');
    localStorage.setItem('od2_tut', 'done');
    for (const t of ['pvp', 'studio', 'city', 'reno', 'gw']) localStorage.setItem('od2_tip_' + t, '1');
  }, lang);
  await p.goto('http://od.test/?lang=' + lang);
  await p.waitForTimeout(1500);
  return p;
}
// a studio that looks lived-in: floors, staff, upgrades, a project in progress
async function rich(p) {
  await ev(p, `
    S.cash = 4e6; S.gems = 420;
    for (const f of ['f1', 'f2', 'f3', 'f4', 'f5']) netAct('flr', f);
    for (let k = 0; k < 3; k++) for (const r of ['prog', 'art', 'mkt', 'des', 'qa', 'snd', 'prod', 'cm']) netAct('hire', r);
    for (let k = 0; k < 4; k++) for (const r of ['prog', 'art', 'des']) netAct('ups', r);
    if (!S.project) netAct('new', 'rpg', 'indie', 'Neon Saga');
    S.cash = 1.92e6; S.energy = S.energyMax || 40;
    try { render(); } catch (e) {}
  `);
  await p.waitForTimeout(800);
}
const ev = (p, code) => p.evaluate((c) => window.__ev(c), code);
module.exports = { chromium, open, rich, ev };
