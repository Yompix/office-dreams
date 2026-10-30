// Запись постановки кадров старой игры для Godot: в странице игры (three.js) кадр ставится без рисования (1–2 мс),
// и покадрово (30 к/с) пишется всё, что двигается: кости героев, лицо, предметы в руках, камера кадра, подвижные части места и свет.
// Godot проигрывает запись своим светом — позы, касания и «твёрдый мир» те же, что в старой игре (перезапись — после правок старой игры).
// Губы и громкость речи не пишем (talk пустой): их Godot считает сам по языку игры.
//   node tools/godot/bake_shots.js [кадр,кадр…|all]   →  godot/nightshift/bake/<кадр>.json + .bin (float32)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), core = require('./bake_core.js');
const ROOT = path.join(__dirname, '../..'), OUT = path.join(ROOT, 'godot/nightshift/bake'), FPS = 30, HOLD = 6, MARGIN = 2;
const story = JSON.parse(fs.readFileSync(path.join(ROOT, 'godot/nightshift/data/story.json'), 'utf8'));
const timings = fs.readdirSync(path.join(ROOT, 'public/games/nightshift')).filter(f => /^timing(_\w+)?\.json$/.test(f)).map(f => JSON.parse(fs.readFileSync(path.join(ROOT, 'public/games/nightshift', f), 'utf8')));
// сколько секунд кадр (img) идёт в сценарии: реплики по самому длинному языку, подряд идущие кадры с тем же img — одно время
function needs() {
  const need = {}; const lineDur = k => Math.max(0, ...timings.map(T => (T[k] && T[k].dur) || 0));
  for (const sc of Object.values(story.scenes)) { let prev = null, acc = 0;
    for (const b of sc.beats || []) { let d = (b.sayAt || .15); for (const k of b.say || []) d += lineDur(k) + .3;
      if (b.text) d += b.text.length / 14; d = Math.max(d + (b.pad != null ? b.pad : .3), 1.2);
      if (b.img && b.img === prev) acc += d; else acc = d; prev = b.img;
      if (b.img) need[b.img] = Math.max(need[b.img] || 0, acc); }
    const th = sc.then || {}; for (const k of ['explore', 'talk']) if (th[k] && th[k].img) need[th[k].img] = Math.max(need[th[k].img] || 0, 4); }
  return need;
}
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const need = needs(); const want = (process.argv[2] || 'all') === 'all' ? Object.keys(need) : process.argv[2].split(',');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.evaluate(require('./naming.js')); await p.evaluate(() => { window.__R0 = window.__W3core.R.render.bind(window.__W3core.R); });
  let total = 0;
  for (const id of want) {
    const dur = (need[id] || 6) + MARGIN;
    let r; try { r = await p.evaluate(core, [id, { dur, fps: FPS, hold: HOLD }]); } catch (e) { console.log(id, 'ошибка:', String(e).split('\n')[0].slice(0, 160)); continue; }
    if (r.err) { console.log(id, r.err); continue; }
    const bin = Buffer.from(r.bin, 'base64'); fs.writeFileSync(path.join(OUT, id + '.bin'), bin); fs.writeFileSync(path.join(OUT, id + '.json'), JSON.stringify(r.meta));
    total += bin.length; console.log(id, r.meta.dur.toFixed(1) + ' с,', r.meta.frames, 'кадров,', r.meta.chans.length, 'каналов,', (bin.length / 1e6).toFixed(2), 'МБ');
  }
  console.log('всего', (total / 1e6).toFixed(1), 'МБ;', errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
