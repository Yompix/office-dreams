// Проникновения на камерах СМЕНЫ (то, чего не видит clip_check.js — он ходит только по сюжетным сценам):
// каждая камера × каждая странность (S.shift.an.props) × распорядок Гаса по времени (cam07) → World.clips().
//   PW=$PWD/tools/nosrv_pw.js node tools/anim/shift_clip_check.js [шаг секунд распорядка, по умолчанию 1.5] [сколько секунд распорядка, 420]
// Ошибка — как в clip_check: тело в стене/предмете глубже 4 см (или насквозь), под полом глубже 6 см.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const STEP = +(process.argv[2] || 1.5), LEN = +(process.argv[3] || 420), PORT = process.env.PORT || 8771;
(async () => {
  const w = {}; new Function('window', fs.readFileSync(path.join(__dirname, '../../web/story.js'), 'utf8'))(w); const AN = w.STORY.shift.an;
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 480, height: 270 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><canvas id="c" width="480" height="270"></canvas></body></html>`;
  await p.route(/\/web\/_scc\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${PORT}/web/_scc.html?gfx=retro&lang=ru`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); });
  const bad = [], seen = new Set(); let n = 0;
  const run = async (label, shot, sh, t) => {
    const r = await p.evaluate(([shot, sh, t]) => { const g = document.getElementById('c').getContext('2d'); try { for (let i = 0; i <= 3; i++) World.draw(g, 480, 270, shot, { t: t + i / 15, T: 100 + t + i / 15, settle: i === 0, props: { clock: '02:00', sh } }); } catch (e) { return [{ err: String(e) }]; }
      return World.clips().filter(c => c.prop === 'пол' ? c.d >= .06 && c.n >= 15 : c.d >= .04).map(c => ({ k: c.k, prop: c.prop, d: c.d, n: c.n, at: c.at })); }, [shot, sh, t]);
    n++; for (const c of r) { const key = label.replace(/@.*/, '') + '|' + c.k + '|' + c.prop; if (c.err) { bad.push(label + ': ОШИБКА ' + c.err); continue; } if (!seen.has(key)) { seen.add(key); bad.push(`${label}: ${c.k} в «${c.prop}» на ${Math.round(c.d * 100)} см (вершин ${c.n}), точка ${c.at}`); } } };
  const camShot = c => 'cam' + (c === 'm2' ? '_m2' : c);
  // странности: каждая на своей камере (и на «also»), Гас — как требует need
  for (const [id, a] of Object.entries(AN)) { if (!a.props && !a.at) continue;
    for (const cam of [a.cam].concat(a.also || [])) { const sh = Object.assign({ gus: a.need === 'gus' ? 1 : 0, gusGone: a.need === 'nogus' ? 1 : 0, gz: 5 }, a.props || {});
      for (const t of [.5, 2, 4]) await run(`${id} (CAM ${cam})@${t}`, camShot(cam), sh, t); } }
  // распорядок Гаса на cam07: три разных ночи, вся длина
  for (let night = 0; night < 3; night++) { await p.evaluate(() => { window.__gusPlan = null; });
    for (let gz = 0; gz < LEN; gz += STEP) { const kind = await p.evaluate(gz => { const P = window.__gusPlan; if (!P) return ''; const it = P.find(x => gz >= x.t0 && gz < x.t0 + x.d); return it ? it.k : ''; }, gz);
      await run(`Гас ночь ${night + 1}: ${kind || '?'}@${gz.toFixed(1)}`, 'cam07', { gus: 1, gz: night === 0 ? gz : gz + night * 0.001 }, 5 + gz);
      if (gz === 0 && night > 0) await p.evaluate(() => 0); }
    await p.evaluate(() => { try { World.draw(document.getElementById('c').getContext('2d'), 480, 270, 'cam07', { t: 0, T: 100, settle: true, props: { sh: { gus: 1, gz: -10 } } }); } catch (e) {} }); }
  // состояния камер без странностей
  for (const [cam, sh] of [['07', { gusGone: 1 }], ['07', { gus: 0 }], ['10', {}], ['m2', {}], ['01', {}], ['02', {}], ['03', {}], ['04', {}], ['01', { lilyDoor: 1 }], ['02', { blackout: 1 }]]) await run(`CAM ${cam} ${JSON.stringify(sh)}@1`, camShot(cam), sh, 1);
  console.log(`проверено положений: ${n}; проникновений (разных): ${bad.length}; ошибок JS: ${errs.length}`); bad.forEach(l => console.log('  ' + l)); errs.slice(0, 5).forEach(e => console.log('  JS: ' + e));
  await br.close();
})();
