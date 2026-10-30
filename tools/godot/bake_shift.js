// Камеры смены для Godot — по слоям. Что видно на камере, зависит от хода смены (пульт, кто где, странности: shiftProps в engine.js),
// поэтому пишется основа каждой камеры (состояние по умолчанию, петля) и слои — по одному на состояние, только то, что меняется
// относительно основы (предметы, свет, новые герои; герой основы, которого в этом состоянии нет, — в списке hide).
// Godot складывает слои по ходу смены (scripts/shift/*). Фигура «помех» (stare) — 4 слоя: как близко она подошла.
//   node tools/godot/bake_shift.js   →  godot/nightshift/bake/shift/<камера>@<слой>.json + .bin, index.json
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), core = require('./bake_core.js');
const ROOT = path.join(__dirname, '../..'), OUT = path.join(ROOT, 'godot/nightshift/bake/shift');
const story = JSON.parse(fs.readFileSync(path.join(ROOT, 'godot/nightshift/data/story.json'), 'utf8'));
const CAM = { '01': 'cam01', '02': 'cam02', '03': 'cam03', '04': 'cam04', '07': 'cam07', '10': 'cam10', 'm2': 'cam_m2' };
const BASE_DUR = 12, STARE_DUR = 3, GUS_P = 2 * (14 + .9 + 1.6 + .9) + (14 + .9 + 2.6 + .9);   // gusRoutine: период мытья полосы из трёх участков
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.evaluate(require('./naming.js')); await p.evaluate(() => { window.__R0 = window.__W3core.R.render.bind(window.__W3core.R); });
  // слои: [камера, имя, состояние (sh), длина, петля]
  const L = [];
  for (const cam of Object.values(CAM)) L.push([cam, 'base', {}, BASE_DUR]);
  for (const [id, a] of Object.entries(story.shift.an)) {
    const cam = CAM[a.cam]; if (!cam) continue;
    if (a.props && Object.keys(a.props).length) L.push([cam, 'an_' + id, a.props, a.props.cat ? 6 : 1]);
    if (a.stare) {   // фигура: шаги 0…3 (как близко подошла), точка и поза — как в shiftProps / stareFig
      const T = [[0, 1.3, 2.6, 3.8], [0, .42, .76, 1]];   // STARE_T, STARE_K в engine.js (скрыты в его замыкании)
      for (let n = 0; n < 4; n++) { const k = T[0][n] + .01, s = a.steps ? a.steps[n] : (() => { const A = a.at, B = a.near || A, q = T[1][n]; return [0, 1, 2].map(i => (A[i] || 0) + ((B[i] || 0) - (A[i] || 0)) * q); })();
        L.push([cam, `st_${id}_${n}`, { ['stare' + a.cam]: k, ['stareAt' + a.cam]: s, ['stareN' + a.cam]: n, ['stareP' + a.cam]: a.pose3 }, STARE_DUR]); }
    }
  }
  // кто где и пульт (engine: shk.st, shk.lights)
  L.push(['cam07', 'gus', { gus: 1 }, GUS_P], ['cam07', 'gusGone', { gusGone: 1 }, 1], ['cam07', 'gusGone_l7', { gusGone: 1, l7: 1 }, 1], ['cam07', 'l7', { l7: 1 }, 1],
    ['cam10', 'l10', { l10: 1 }, 1], ['cam02', 'noEthan', { noEthan: 1 }, 1]);
  const index = {}; let total = 0;
  for (const [cam, name, st, dur] of L) {
    const id = cam + '@' + name, props = { sh: Object.assign({ gz: undefined }, st) };
    if (name === 'gus') props.sh.gz = null;   // часы швабры — время кадра (s.gz ?? t)
    let r; try { r = await p.evaluate(core, [cam, { dur, fps: 30, loop: true, props, store: name === 'base' ? cam : null, ref: name === 'base' ? null : cam }]); }
    catch (e) { console.log(id, 'ошибка:', String(e).split('\n')[0].slice(0, 160)); continue; }
    if (r.err) { console.log(id, r.err); continue; }
    r.meta.id = id;
    const bin = Buffer.from(r.bin, 'base64'); fs.writeFileSync(path.join(OUT, id + '.bin'), bin); fs.writeFileSync(path.join(OUT, id + '.json'), JSON.stringify(r.meta));
    (index[cam] || (index[cam] = { base: cam + '@base', layers: [] }));
    if (name !== 'base') index[cam].layers.push({ id, when: st, chans: r.meta.chans.length + Object.keys(r.meta.const).length, hide: r.meta.hide || [] });
    total += bin.length; console.log(id, r.meta.frames, 'кадров,', r.meta.chans.length, 'каналов,', Object.keys(r.meta.const).length, 'постоянных,', (bin.length / 1e6).toFixed(2), 'МБ', r.meta.hide && r.meta.hide.length ? 'hide ' + r.meta.hide : '');
  }
  fs.writeFileSync(path.join(OUT, 'index.json'), JSON.stringify(index, null, 1));
  console.log('всего', (total / 1e6).toFixed(1), 'МБ;', errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
