// Ночь целиком, как игрок: от «Начать смену» по всем веткам до четырёх концовок, и на каждом решении сначала — смертельный вариант:
// смерть → «Попробовать снова» → то же решение → дальше правильно. Мини-игры проходит сам (перемотка, шнур, стекло, «не дыши», бег, фонарь),
// часы смены — с ускорением; странности «улаживает» сам (кроме проверок «ОНО ПРИШЛО» и «Засмотрелся»).
// Ловит: ошибки JS, зависания (сцена не меняется), часы назад (кроме повтора после смерти), куда ведёт «снова», концовки и смерти в счётчиках.
//   node tools/story/path_test.js [ПАПКА для снимков]   (PORT=8771; LAND=1 — горизонтально; LANG2=en|uk; PATHS=sister,inside,dawn,newentry;
//   FROM=glass2 — сразу с «стекла» 03:30, как будто ночь до него прошла; TRACE=1 — печатать каждую сцену)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const out = process.argv[2], PORT = process.env.PORT || 8771, LAND = process.env.LAND === '1', LANG = process.env.LANG2 || process.env.LANGX || '';
  const PATHS = (process.env.PATHS || 'sister,inside,dawn,newentry').split(',');
  if (out) fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await b.newContext({ viewport: LAND ? { width: 640, height: 360 } : { width: 360, height: 640 }, locale: 'ru-RU' });
  await ctx.addInitScript(l => { if (!sessionStorage.getItem('init')) { sessionStorage.setItem('init', 1); localStorage.clear(); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_land3', l ? 'true' : 'false'); } }, LAND);
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.goto(`http://127.0.0.1:${PORT}/nightshift/index.html?gfx=retro&cs=${process.env.CS || .25}${LANG ? '&lang=' + LANG : ''}`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  // «держи» в смене — жмёт сама страница в тот же кадр, как появилась кнопка (на загруженной машине ответ из теста опаздывал дольше, чем можно не держать); __botHold = 0 — проверка смерти
  await p.evaluate(() => { window.__botHold = 1; (function loop() { const b = document.getElementById('hBtn'); if (b && window.__botHold && !b.classList.contains('on')) b.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); requestAnimationFrame(loop); })(); });
  let fail = 0; const bad = m => { console.log('  ✗ ' + m); fail++; }, log = m => console.log('  ' + m);
  const st = () => p.evaluate(() => { const n = window.__ns, f = n.shf, c = document.getElementById('clock');
    return { mode: n.mode, scene: n.scene, bi: n.bi, clock: c && !c.classList.contains('hide') ? c.textContent : '', seg: f && f.id, M: f && f.M,
      pop: !!document.querySelector('#shPop:not(.hide)'), cTake: !!document.getElementById('cTake'), cYes: !!document.getElementById('cYes'), hBtn: !!document.getElementById('hBtn'),
      hb: !!document.getElementById('hb'), plug: !!document.getElementById('plug'), arrows: !!document.querySelector('#mg .arrows'), tape: !!document.getElementById('tape'),
      oRetry: !!document.getElementById('oRetry'), over: (document.querySelector('.screen.over h1') || {}).textContent || '', jr: !!document.querySelector('.screen.jr'),
      stamp: (document.querySelector('.jr-stamp') || {}).textContent || '', choice: [...document.querySelectorAll('#choice:not(.hide) button')].map(x => x.dataset.i), small: (document.querySelector('#mg small') || {}).textContent || '' }; });
  const toMin = s => { const [h, m] = s.split(':').map(Number); return (h < 12 ? h + 24 : h) * 60 + m; };
  const deaths = [], ends = [], done = new Set();   // done — какие смерти уже проверены (второй раз — правильный вариант)
  // смертельный вариант на решении: сцена → номер варианта (data-i) и смерть, которой он кончается
  const DEADLY = { floor7b: [0, 'over_k2'], radio: [0, 'over_k3'], basement: [0, 'over_k7'], call540: [0, 'over_k9'] };
  const mouseAt = async (x, y) => { const r = await p.evaluate(() => { const c = document.getElementById('cv').getBoundingClientRect(); return [c.left, c.top, c.width, c.height]; }); return [r[0] + x * r[2], r[1] + y * r[3]]; };
  async function drive(name, pick, stopAt) {   // pick(scene, opts) → номер варианта на решении; stopAt — на какой сцене остановиться (не доходя до концовки)
    let last = '', lastT = -1, same = 0, prevKey = '', justRetried = false, expect = null, want = null;   // want — куда должно вернуть «снова» (ставится на смертельном решении)
    for (let step = 0; step < 4000; step++) {
      const s = await st(); const key = s.mode + '|' + s.scene + '|' + s.bi + '|' + (s.M ? Math.floor(s.M) : '');
      if (key === prevKey) { if (++same > 120) { bad(`${name}: застряло — ${key}`); return; } } else { same = 0; prevKey = key; }
      if (s.scene !== last) { if (expect && s.scene !== expect && s.mode !== 'end') bad(`${name}: «снова» привело в ${s.scene}, ждал ${expect}`); expect = null; last = s.scene; if (process.env.TRACE) log(`→ ${s.scene} ${s.clock}`); }
      if (stopAt && s.scene === stopAt && s.mode === 'choice') return;
      if (s.clock && /^\d\d:\d\d$/.test(s.clock)) { const t = toMin(s.clock); if (lastT >= 0 && t < lastT && !justRetried) bad(`${name}: часы назад ${lastT}→${s.clock} в ${s.scene}`); lastT = t; justRetried = false; }
      if (s.mode === 'end') {
        if (s.oRetry) { deaths.push(s.over.trim()); log(`смерть «${s.over.trim()}» (${s.scene}) → снова`);
          if (out) await p.screenshot({ path: `${out}/${name}_${s.scene}.png` }).catch(() => {});
          await p.evaluate(() => document.getElementById('oRetry').click()); justRetried = true; lastT = -1; expect = want; want = null; await p.waitForTimeout(600); continue; }
        if (s.jr) { await p.waitForTimeout(2500); const s2 = await st(); ends.push(s2.stamp); log(`концовка: ${s2.stamp.replace(/\s+/g, ' ')}`); if (out) await p.screenshot({ path: `${out}/${name}_end.png` }).catch(() => {}); return; }
        await p.waitForTimeout(300); continue;
      }
      if (s.mode === 'play') { await p.evaluate(() => { window.__ns.skipBeat(); const f = document.getElementById('fin'); if (f && window.__ns.fin) f.click(); }); await p.waitForTimeout(120); continue; }
      if (s.mode === 'choice') {
        const D = DEADLY[s.scene]; let i;
        if (D && !done.has(D[1])) { done.add(D[1]); i = D[0]; want = s.scene; }
        else i = pick(s.scene, s.choice);
        if (i == null) i = D ? +s.choice.find(x => +x !== D[0]) : +s.choice[0];   // смерть уже проверена — безопасный вариант
        log(`${s.clock} ${s.scene}: вариант ${i}`);
        await p.evaluate(i => document.querySelector(`#choice button[data-i="${i}"]`).click(), i); await p.waitForTimeout(300); continue;
      }
      if (s.mode === 'explore' || s.mode === 'talk') {   // всё осмотреть и обо всём спросить, потом выход
        const clicked = await p.evaluate(() => { const bs = [...document.querySelectorAll('#act button')].filter(b => !b.classList.contains('bye') && !b.classList.contains('ex-leave'));
          const b = bs[0] || document.querySelector('#act .bye, #act .ex-leave'); if (b) { b.click(); return b.textContent.trim().slice(0, 30); } return null; });
        await p.waitForTimeout(clicked ? 400 : 300); continue;
      }
      if (s.mode === 'mg') {
        if (s.tape) { const r = await p.evaluate(() => { const b = document.getElementById('tape').getBoundingClientRect(); return [b.left + 3, b.top + b.height / 2]; }); await p.mouse.move(r[0], r[1]); await p.mouse.down(); await p.waitForTimeout(150); await p.mouse.up(); await p.waitForTimeout(1500); continue; }
        if (s.plug) { if (!done.has('over_k4')) { done.add('over_k4'); want = 'cam13'; for (let k = 0; k < 240 && (await p.evaluate(() => window.__ns.mode)) === 'mg'; k++) await p.waitForTimeout(500); } else await p.evaluate(() => window.__ns.cordWin()); await p.waitForTimeout(500); continue; }
        if (s.arrows) { const dir = /→/.test(s.small) ? 'R' : 'L', lose = !done.has('over_k6'); if (lose) { done.add('over_k6'); want = 'run'; }
          await p.evaluate(d => document.querySelector(`#mg .arrows button[data-d="${d}"]`).click(), lose ? (dir === 'R' ? 'L' : 'R') : dir); await p.waitForTimeout(250); continue; }
        if (s.hb) { const die = s.scene === 'hide' ? 'over_k5' : 'over_k8';
          if (!done.has(die)) { done.add(die); want = s.scene; for (let k = 0; k < 240 && (await p.evaluate(() => window.__ns.mode)) === 'mg'; k++) await p.waitForTimeout(500); continue; }   // не нажал — смерть
          const h0 = await p.evaluate(() => { document.getElementById('hb').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); return (document.querySelector('#mg .mghead') || {}).textContent; });   // держит, пока шаги/голос рядом
          for (let k = 0; k < 300; k++) { await p.waitForTimeout(80); const t = await p.evaluate(h0 => { const h = (document.querySelector('#mg .mghead') || {}).textContent; if (h !== h0 && document.getElementById('hb')) document.getElementById('hb').dispatchEvent(new PointerEvent('pointerup', { bubbles: true })); return [window.__ns.mode, h]; }, h0); if (t[0] !== 'mg' || t[1] !== h0) break; }   // стихло (заголовок сменился) — сразу отпускает
          await p.waitForTimeout(800); continue; }
        if (s.scene === 'glass') { await p.waitForTimeout(1500); continue; }   // стекло само «протрётся» за 15 с
        if (s.scene === 'down') { const [x, y] = await mouseAt(.5, .52 + .07); await p.mouse.move(x, y); await p.mouse.down(); await p.mouse.move(x + 1, y); await p.waitForTimeout(1500); await p.mouse.up(); continue; }
        await p.waitForTimeout(400); continue;
      }
      if (s.mode === 'shift') { await shiftBot(s, name); continue; }
      await p.waitForTimeout(300);
    }
    bad(`${name}: не дошёл до конца за 4000 шагов`);
  }
  // бот смены: ускорение, пульт к 22:00, странности улаживает; звонки, рация и «держи» — с проверкой смертей (по одному разу)
  async function shiftBot(s, name) {
    const seg = s.seg;
    if (seg === 'S3' && !done.has('over_come')) { done.add('over_come'); log('S3: странности без внимания → «ОНО ПРИШЛО»');
      await p.evaluate(() => { const f = window.__ns.shf; window.__ns.shSpeed(40); for (const id of ['door10', 'lift', 'snd_m2']) f.ev.push({ m: f.M + .2, an: id }); });
      for (let k = 0; k < 240 && (await p.evaluate(() => window.__ns.mode)) === 'shift'; k++) await p.waitForTimeout(500);   // ждём, не улаживая
      return; }
    if (seg === 'S3' && !done.has('over_stare')) { done.add('over_stare'); log('S3: смотрит в помехи на CAM 04 → «Засмотрелся»');
      await p.evaluate(() => { const f = window.__ns.shf; window.__ns.shSpeed(0); f.act = []; f.ev.push({ m: f.M - .01, an: 'st04' }); }); await p.waitForTimeout(300); await p.evaluate(() => window.__ns.shiftCam('04'));
      for (let k = 0; k < 240 && (await p.evaluate(() => window.__ns.mode)) === 'shift'; k++) await p.waitForTimeout(500);   // время взгляда идёт по кадрам — ждём, пока не случится
      return; }
    if (seg === 'S4' && s.M >= 26 * 60 + 2 && !done.has('over_k1')) { done.add('over_k1'); log('S4: CAM 13 два раза → «Посмотрел в лицо»'); await p.evaluate(() => { window.__ns.shiftCam('13'); window.__ns.shiftCam('13'); }); await p.waitForTimeout(800); return; }
    await p.evaluate(() => { const n = window.__ns, f = n.shf; if (!f) return; n.shSpeed(document.getElementById('hBtn') ? 2 : f.id === 'S4' && f.M < 26 * 60 + 3 ? 12 : 20);   // пока «держи» — почти обычный темп: на ×20 семь секунд удержания = 35 минут часа, и второе «держи» (05:34) наступало, пока шло первое const k = n.shk; if (f.id === 'S1') { k.door = 1; k.lift = 1; }
      if (!f._bot) f._bot = 1; f.act = f.act.filter(a => (a.def.fix || 'log') === 'none' || a.def.trap); });   // «улажено»; ловушку у входа не трогаем: вход заперт
    if (s.cTake) {   // звонок: S4 «Итан?» и телефон Фрэнка, S5 рация — сначала ответить (смерть), потом сбросить; A1 — не брать
      const title = await p.evaluate(() => document.querySelector('#shPop h3').textContent), die = /Фрэнка|Frank|Френка/.test(title) ? 'over_fmom' : seg === 'S5' ? 'over_k3s5' : seg === 'S4' ? 'over_phone' : null;
      if (die && !done.has(die)) { done.add(die); log(`${seg}: «${title}» — ответить`); await p.click('#cTake'); return; }
      await p.click('#cNo'); return; }
    if (s.cYes) { await p.click('#cYes'); return; }
    if (s.hBtn) { const die = seg === 'S6' ? 'over_k8s6' : 'over_k5b';
      if (!done.has(die)) { done.add(die); log(`${seg}: «держи» — не держит`); await p.evaluate(() => { window.__botHold = 0; const b = document.getElementById('hBtn'); if (b) b.dispatchEvent(new PointerEvent('pointerup', { bubbles: true })); }); for (let k = 0; k < 240 && (await p.evaluate(() => window.__ns.mode === 'shift' && !!document.getElementById('hBtn'))); k++) await p.waitForTimeout(500); await p.evaluate(() => { window.__botHold = 1; }); return; }   // страница нажимает «держи» в тот же кадр — для проверки смерти отпускаем (pointerup)
      await p.evaluate(() => document.getElementById('hBtn').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })));   // сразу, одним вызовом: на загруженной машине движение мыши опаздывало дольше, чем можно не держать
      for (let k = 0; k < 900 && (await p.evaluate(() => !!document.getElementById('hBtn'))); k++) await p.waitForTimeout(100); return; }
    if (seg === 'S5' && !done.has('over_door') && await p.evaluate(() => window.__ns.shf.act.some(a => a.id === 'lilyDoor'))) { done.add('over_door'); log('S5: открыл вход, пока она у двери');
      await p.evaluate(() => { window.__ns.shiftPanel(); document.getElementById('pDoor').click(); }); await p.waitForTimeout(800); return; }
    await p.waitForTimeout(400);
  }
  const PICK = { intro: 1, glass_write: 0 };
  const pk = extra => (sc, opts) => (sc in extra ? extra[sc] : PICK[sc]);
  // 1) до стекла (03:30) — общая часть, потом ветки из сохранённого состояния (в памяти: флаги, пульт)
  if (!process.env.FROM) { console.log('путь: начало → стекло');
    await p.evaluate(() => window.__ns.begin('intro'));
    await drive('start', pk({}), 'glass2'); }
  else await p.evaluate(() => { localStorage.setItem('ns1_shk', JSON.stringify({ door: 1, lift: 1, lights: {}, log: [], st: {}, c13: true, read: true }));   // FROM=glass2: как будто ночь до стекла прошла (вход заперт, лифт стоит)
    window.__ns.setFlags({ asked: 1, salt: 1, photo: 1, fphone: 1, jread: 1, fpcall: 1, heard: 1 }); });
  for (const P of PATHS) {
    console.log('путь:', P);
    if (P !== 'sister' || process.env.FROM) await p.evaluate(() => window.__ns.startScene('glass2', true));
    const plan = P === 'sister' ? { glass2: 1, glass_write: 0, branch_a: 1 } : P === 'inside' ? { glass2: 0, branch_a: 0 } : P === 'dawn' ? { glass2: 2, server: 0 } : { glass2: 2, server: 1 };
    await drive(P, pk(plan));
  }
  const ov = await p.evaluate(() => JSON.parse(localStorage.getItem('ns1_overs') || '[]')), en = await p.evaluate(() => JSON.parse(localStorage.getItem('ns1_endings') || '[]'));
  console.log(`смертей: ${new Set(deaths).size} разных (${deaths.length} раз) · в счётчике ${ov.length}: ${ov.join(', ')}`);
  console.log(`концовок: ${ends.length} · в счётчике ${en.join(', ')}`);
  if (PATHS.length === 4 && en.length !== 4) bad('не все четыре концовки');
  if (errs.length) bad('ошибки JS: ' + errs.slice(0, 4).join(' | '));
  console.log(fail ? `НЕ ПРОШЛО: ${fail}` : 'всё прошло'); await b.close(); process.exit(fail ? 1 : 0);
})();
