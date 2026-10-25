// Рывок позы на смене кадра: доигрывает кадр до конца реплики, «нажимает» дальше и пишет кости людей каждый кадр.
// Скачок — насколько сдвинулись кисти и голова в осях персонажа (без его перемещения) на первом кадре после склейки,
// рядом — обычный сдвиг за кадр до склейки. Для склеек того же ракурса (вопрос → ответ) скачок должен быть как обычный кадр.
//   PORT=8783 node tools/anim/cut_probe.js "сцена:кадр[,сцена:кадр…]"   (кадр — тот, ПОСЛЕ которого склейка)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [list = 'gs_frank:0'] = process.argv.slice(2), port = process.env.PORT || 8771;
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'];
(async () => {
  const b = await chromium.launch({ args: ARGS }), p = await b.newPage({ viewport: { width: 360, height: 640 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e.message || e).slice(0, 300)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); window.__CUTGAP = 3; });
  await p.goto(`http://127.0.0.1:${port}/nightshift/index.html?gfx=cine&lang=ru&cs=.5&rs=.3`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
  await p.evaluate(() => {   // запись костей каждый кадр (в осях персонажа)
    window.__rec = []; const BN = ['Head', 'hand_l', 'hand_r', 'spine_03'];
    const tick = () => { const A = window.World && window.World.actors, N = window.__ns;
      if (A && N && N.mode === 'play') { const fr = { bi: N.bi, img: N.beatImg, c: {} };
        for (const k in A) { const C = A[k]; if (!C.root.parent || !C.root.visible || !C.bone || C.o.monster) continue; C.root.updateWorldMatrix(true, true); fr.c[k] = {};
          for (const bn of BN) { const bb = C.bone[bn]; if (!bb) continue; const v = bb.getWorldPosition(new bb.position.constructor()); C.root.worldToLocal(v); fr.c[k][bn] = [v.x, v.y, v.z]; } }
        window.__rec.push(fr); if (window.__rec.length > 400) window.__rec.shift(); }
      requestAnimationFrame(tick); };
    requestAnimationFrame(tick); });
  for (const item of list.split(',')) {
    const [id, bi] = item.split(':');
    await p.evaluate(([id, bi]) => window.__ns.startScene(id, +bi), [id, bi]);
    await p.waitForFunction(() => window.__ns.mode === 'play' && window.__ns.bdur > 0, null, { timeout: 60000 });
    const dur = await p.evaluate(() => window.__ns.bdur);
    await p.evaluate(t => { window.__ns.seek(t); window.__ns.pause(false); }, Math.max(0, dur - 1.0));
    await p.waitForTimeout(2500);   // ждёт нажатия
    await p.evaluate(() => { window.__rec.length = 0; window.__ns.skipBeat(); });
    await p.waitForTimeout(2500);
    const rec = await p.evaluate(() => window.__rec);
    const j = rec.findIndex((f, i) => i > 0 && f.bi !== rec[i - 1].bi);
    if (j < 2) { console.log(`${item}: склейки не поймал (${rec.length} кадров)`); continue; }
    const step = (A, B) => { const out = {}; for (const k in B.c) { if (!A.c[k]) continue; let m = 0; for (const bn in B.c[k]) if (A.c[k][bn]) m = Math.max(m, Math.hypot(...B.c[k][bn].map((v, i) => v - A.c[k][bn][i]))); out[k] = m * 1000; } return out; };
    const jump = step(rec[j - 1], rec[j]), norm = {}; for (let i = Math.max(1, j - 6); i < j; i++) { const s = step(rec[i - 1], rec[i]); for (const k in s) norm[k] = Math.max(norm[k] || 0, s[k]); }
    console.log(`${item}: ${rec[j - 1].img} → ${rec[j].img}: ` + Object.keys(jump).map(k => `${k} скачок ${jump[k].toFixed(0)} мм (обычный кадр до ${(norm[k] || 0).toFixed(0)} мм)`).join('; '));
  }
  if (errs.length) console.log('ОШИБКИ JS:', errs.slice(0, 5).join(' | '));
  await b.close();
})();
