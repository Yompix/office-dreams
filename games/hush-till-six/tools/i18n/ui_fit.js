// Влезает ли текст на всех языках: меню, настройки, выбор, смена (камеры, «Записать», пульт, журнал, помощь), пауза.
// На каждом экране ищет надписи, которые обрезаны, вылезают из кнопки или за экран, кнопки в 3+ строки, подписи камер в 2 строки; снимает экраны.
//   node tools/i18n/ui_fit.js [языки через запятую] [папка снимков] [P|L]   (по умолчанию все 13 языков, горизонталь)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const langs = (process.argv[2] || 'ru,en,es,pt,de,fr,it,pl,tr,uk,zh,ja,ko').split(','), out = process.argv[3] || '/tmp/ui_fit', land = !/^p/i.test(process.argv[4] || 'L');
fs.mkdirSync(out, { recursive: true });
const SCAN = sel => {   // в странице: что не влезает внутри sel
  const res = [], root = document.querySelector(sel); if (!root) return res;
  const vis = el => { for (let e = el; e && e !== document.body; e = e.parentElement) { const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden' || +cs.opacity === 0) return false; } return true; };
  for (const el of root.querySelectorAll('*')) {
    const own = [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').trim(); if (!own || !vis(el)) continue;
    const cs = getComputedStyle(el), r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) continue;
    const rg = document.createRange(); rg.selectNodeContents(el); const t = rg.getBoundingClientRect(), txt = own.slice(0, 60);
    if (el.scrollWidth > el.clientWidth + 2 && /hidden|clip|auto|scroll/.test(cs.overflowX)) res.push(['обрезано', txt]);
    else if (t.right > r.right + 3 || t.left < r.left - 3) res.push(['вылезает из рамки', txt]);
    let scroll = false; for (let e = el.parentElement; e && e !== document.body; e = e.parentElement) { const o = getComputedStyle(e).overflowY; if (/auto|scroll/.test(o) && e.scrollHeight > e.clientHeight + 2) { scroll = true; break; } }   // ниже края в прокручиваемом окне (настройки на вертикальном экране) — докручивается, не ошибка
    if (t.right > innerWidth + 1 || t.left < -1 || (!scroll && (t.bottom > innerHeight + 1 || t.top < -1))) res.push(['за краем экрана', txt]);
    const btn = el.closest('button'); if (btn) { const lh = parseFloat(cs.lineHeight) || parseFloat(cs.fontSize) * 1.25, n = Math.round(t.height / lh);
      if (el.matches('.sh-cams small') && n > 1) res.push(['подпись камеры в ' + n + ' строки', txt]); else if (n > 2) res.push(['кнопка в ' + n + ' строки', txt]); }
  }
  return res;
};
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  let total = 0;
  for (const L of langs) {
    const p = await b.newPage({ viewport: land ? { width: 960, height: 540 } : { width: 405, height: 720 } }), errs = [], found = [];
    p.on('pageerror', e => errs.push(String(e.message || e).slice(0, 160)));
    await p.addInitScript(l => { localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); localStorage.setItem('ns1_land3', l ? 'true' : 'false'); }, land);
    await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=retro&lang=${L}&cs=.4`);
    await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
    const step = async (name, sel, act) => { try { if (act) await act(); await p.waitForTimeout(700); const r = await p.evaluate(SCAN, sel); for (const [k, t] of r) found.push(`${name}: ${k} — «${t}»`); await p.screenshot({ path: `${out}/${L}_${name}.png` }); } catch (e) { found.push(`${name}: не открылся (${String(e.message).slice(0, 80)})`); } };
    await p.keyboard.press('Enter'); await p.waitForTimeout(900);
    await step('меню', '#ui');
    await step('настройки', '#ui', () => p.click('#mSet'));
    await p.evaluate(() => window.__ns.back()); await p.waitForTimeout(500);
    await step('выбор', '#choice', async () => { await p.evaluate(() => window.__ns.startScene('intro', 6)); await p.waitForSelector('#choice button', { timeout: 60000 }); });
    await step('смена', '#mg', async () => { await p.evaluate(() => window.__ns.startScene('sh_S1')); await p.waitForSelector('#shLog', { timeout: 60000 }); await p.waitForTimeout(1500); });
    await step('записать', '#mg', async () => { await p.click('#shLog'); await p.waitForSelector('.sh-grid button'); });
    await p.evaluate(() => { const x = document.getElementById('shX') || document.querySelector('#shPop .sx'); if (x) x.click(); }).catch(() => {}); await p.waitForTimeout(400);
    for (const [nm, id] of [['пульт', '#shPanel'], ['журнал', '#shBook'], ['помощь', '#shHelp']]) {
      await step(nm, '#mg', () => p.click(id));
      await p.evaluate(() => { const x = document.getElementById('shX') || document.querySelector('#shPop .sx'); if (x) x.click(); }).catch(() => {}); await p.waitForTimeout(400);
    }
    await step('пауза', '#pz', () => p.keyboard.press('Escape'));
    console.log(`${L}: ${found.length ? found.length + ' замечаний' : 'всё влезает'}${errs.length ? ' | ОШИБКИ JS: ' + errs.slice(0, 3).join(' ; ') : ''}`);
    for (const f of found) console.log('   ' + f);
    total += found.length; await p.close();
  }
  console.log(`итого замечаний: ${total}; снимки — ${out}`);
  await b.close();
})();
