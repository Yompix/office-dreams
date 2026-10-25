// «Не замирает ли»: доигрывает кадр до конца реплики (игра ждёт нажатия, время кадра стоит) и меряет, на сколько миллиметров
// сдвинулись кости людей за 1,2 с. 0–1 мм — человек стоит статуей; живое тело — единицы и десятки миллиметров.
//   PORT=8783 node tools/anim/hold_probe.js "сцена:кадр[,сцена:кадр…]" [ПАПКА для снимков] [флаги: nolife]
// Сервер: cd public/games && python3 -m http.server 8783 --bind 127.0.0.1
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [list = 'gs_long:1', out, flag = ''] = process.argv.slice(2), port = process.env.PORT || 8771;
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'];
(async () => {
  const b = await chromium.launch({ args: ARGS }), p = await b.newPage({ viewport: { width: 270, height: 480 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e.message || e).slice(0, 300)));
  await p.addInitScript(nl => { localStorage.setItem('ns1_land3', 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); window.__CUTGAP = 3; if (nl) window.__NOLIFE = 1; }, /nolife/.test(flag));
  await p.goto(`http://127.0.0.1:${port}/nightshift/index.html?gfx=retro&lang=ru&cs=.4`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
  const snap = () => p.evaluate(() => { const A = window.World.actors, r = {};
    for (const k in A) { const C = A[k]; if (!C.root.parent || !C.root.visible || !C.bone) continue; r[k] = {};
      for (const bn of ['Head', 'spine_03', 'clavicle_l', 'hand_l', 'hand_r', 'index_03_r']) { const bb = C.bone[bn]; if (!bb) continue; const w = bb.getWorldPosition(new bb.position.constructor()); r[k][bn] = [w.x, w.y, w.z]; } }
    return r; });
  for (const item of list.split(',')) {
    const [id, bi] = item.split(':');
    await p.evaluate(([id, bi]) => window.__ns.startScene(id, +bi), [id, bi]);
    await p.waitForFunction(() => window.__ns.mode === 'play' && window.__ns.bdur > 0, null, { timeout: 60000 });
    const dur = await p.evaluate(() => window.__ns.bdur);
    await p.evaluate(t => { window.__ns.seek(t); window.__ns.pause(false); }, Math.max(0, dur - 1.2));
    await p.waitForTimeout(2500);   // кадр доигран — игра ждёт нажатия
    console.log('  dbg', await p.evaluate(() => window.__ns.dbg + ' mode=' + window.__ns.mode + ' img=' + window.__ns.beatImg));
    const a = await snap(); if (out) await p.screenshot({ path: `${out}/hold_${id}_${bi}_a.png` });
    await p.waitForTimeout(1200);
    const c = await snap(); if (out) await p.screenshot({ path: `${out}/hold_${id}_${bi}_b.png` });
    const rows = [];
    for (const k in a) { if (!c[k]) continue; const d = []; for (const bn in a[k]) d.push(bn + ' ' + (Math.hypot(...a[k][bn].map((v, i) => v - c[k][bn][i])) * 1000).toFixed(1)); rows.push(`${k}: ${d.join(', ')}`); }
    console.log(`${id}:${bi} (кадр ${dur.toFixed(1)} с), мм за 1,2 с:\n  ` + rows.join('\n  '));
  }
  if (errs.length) console.log('ОШИБКИ JS:', errs.slice(0, 5).join(' | '));
  await b.close();
})();
