// Картинки для Steam — только из движка игры (без ИИ): node tools/desktop/steam/kit.js ПАПКА [frames] [shots]
//   frames → ПАПКА/frames/*.png — кадры «Кино» без интерфейса для капсул, фона библиотеки и иконок достижений
//            (потом python3 tools/desktop/steam/kit.py ПАПКА — капсулы с логотипом, иконки 256×256 открытые/закрытые)
//   shots  → ПАПКА/screenshots/*.png — настоящая игра с интерфейсом, 1920×1080 (субтитры, выбор, смена, мини-игра)
// Сервер статики: cd public/games && python3 -m http.server 8785 --bind 127.0.0.1 (PORT=…). ONLY=имя,имя — только эти кадры.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const [out, ...what] = process.argv.slice(2), PORT = process.env.PORT || 8785, ONLY = process.env.ONLY ? process.env.ONLY.split(',') : null;
if (!out) { console.log('node tools/desktop/steam/kit.js ПАПКА [frames] [shots]'); process.exit(2); }
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'];
// кадры: имя, кадр движка, секунда, план (cx, cy, z), горизонтально?, размер холста, доля разрешения «Кино», камера наблюдения
const FRAMES = [
  ['key_land', 'door_katya', 3, .5, .5, 1.0, 1, [1920, 1080], 1],           // Лили за стеклом под дождём — титульный кадр игры
  ['key_port', 'door_katya', 3, .5, .48, 1.0, 0, [1080, 1920], 1],
  ['hero', 'door_katya', 3.4, .5, .5, 1.0, 1, [3840, 2160], 2],            // фон библиотеки 3840×1240 вырезается из 3840×2160
  ['ach_END_INSIDE', 'h_reflect', 2, .5, .5, 1.1, 1, [960, 540], .6], ['ach_END_SISTER', 'x_rain', 2, .5, .45, 1.1, 1, [960, 540], .6],
  ['ach_END_DAWN', 'x_smoke', 2, .5, .45, 1.1, 1, [960, 540], .6], ['ach_END_NEWENTRY', 'jr_write', 2, .5, .5, 1.1, 1, [960, 540], .6],
  ['ach_DEATH_01', 'k1_face', 2.4, .5, .5, 1.1, 1, [960, 540], .6], ['ach_DEATH_02', 'k2_last', 1.6, .5, .45, 1.15, 1, [960, 540], .6],
  ['ach_DEATH_03', 'k3_radio', 2, .5, .45, 1.1, 1, [960, 540], .6], ['ach_DEATH_04', 'k4_owl', 3.2, .5, .45, 1.0, 1, [960, 540], .6, 1],
  ['ach_DEATH_05', 'k5_drag', 1.5, .5, .5, 1.0, 1, [960, 540], .6], ['ach_DEATH_06', 'k6_fall', 2, .5, .5, 1.0, 1, [960, 540], .6],
  ['ach_DEATH_07', 'k7_behind', 1.5, .5, .5, 1.0, 1, [960, 540], .6], ['ach_DEATH_08', 'k8_ceiling', 3.1, .5, .5, 1.08, 1, [960, 540], .6],
  ['ach_DEATH_09', 'k9_glass', 2, .5, .45, 1.05, 1, [960, 540], .6], ['ach_DEATH_10', 'come_door', 2.5, .5, .5, 1.08, 1, [960, 540], .6],
  ['ach_DEATH_11', 'hush_face', 1.2, .5, .5, 1.07, 1, [960, 540], .6], ['ach_DEATH_12', 'dbl_cam', 2, .6, .58, 1.05, 1, [960, 540], .6, 1],
  ['ach_DEATH_13', 'door_tilt', 2, .5, .45, 1.1, 1, [960, 540], .6], ['ach_DEATH_14', 'fmom_turn', 2.2, .5, .5, 1.0, 1, [960, 540], .6],
  ['ach_ALL_ENDINGS', 'door_katya', 3, .5, .5, 1.3, 1, [960, 540], .6], ['ach_ALL_DEATHS', 'c13_face', 2, .5, .45, 1.6, 1, [960, 540], .6, 1],
  ['ach_NIGHT_DONE', 'hall_dawn', 2, .5, .5, 1.06, 1, [960, 540], .6],
];
// снимки игры: имя, сцена, кадр (или 'end' — сразу к решению), секунда, что сделать перед снимком
const SHOTS = [
  ['01_prologue', 'intro', 0, 3.2], ['02_keys_choice', 'intro', 'end', 0], ['03_lily_glass', 'glass2', 1, 2.6],
  ['04_shift_cameras', 'sh_S2', 'shift', 0, 'log'], ['05_lily_smile', 'branch_b', 2, 2.4], ['06_elevator_13', 'lift', 1, 2.2], ['07_frank_server', 'knock3', 4, 3.0],
];
const pick = list => list.filter(f => !ONLY || ONLY.includes(f[0]));

async function frames(b) {
  const dir = path.join(out, 'frames'); fs.mkdirSync(dir, { recursive: true });
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script>
<canvas id="c" width="16" height="16"></canvas></body></html>`;
  const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 200)));
  await p.route(/\/nightshift\/_kit\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${PORT}/nightshift/_kit.html?gfx=cine`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; await window.__W3chars.load(); World.init(); });
  for (const [name, shot, t, cx, cy, z, land, [w, h], rs, cctv] of pick(FRAMES)) {
    const t0 = Date.now();
    const url = await p.evaluate(([shot, t, cx, cy, z, land, w, h, rs, cctv]) => {
      const V = window.__W3core.V; V.rs = rs; World.setLand(!!land, true);   // доля разрешения сменилась — буферы под новый размер
      const c = document.getElementById('c'); c.width = w; c.height = h; const g = c.getContext('2d');
      const ok = World.draw(g, w, h, shot, { t, T: 100 + t, cx: land ? .5 + (cx - .5) * (9 / 16) / (16 / 9) : cx, cy, z, sx: 0, sy: 0, settle: true, cctv: cctv ? 1 : 0, props: { clock: '03:00', jacket: 1, badge: 1, book: 0 } });
      return ok ? c.toDataURL('image/png') : '';
    }, [shot, t, cx, cy, z, land, w, h, rs, cctv || 0]);
    if (!url) { console.log('✗ нет кадра', shot); continue; }
    fs.writeFileSync(path.join(dir, name + '.png'), Buffer.from(url.split(',')[1], 'base64'));
    console.log(`кадр ${name} (${shot}@${t}, ${w}×${h}) — ${((Date.now() - t0) / 1000).toFixed(0)} с`);
  }
  await p.close();
}

async function shots(b) {
  const dir = path.join(out, 'screenshots'); fs.mkdirSync(dir, { recursive: true });
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); });
  for (const lang of (process.env.LANGS || 'en,ru').split(',')) {
    await p.goto(`http://127.0.0.1:${PORT}/nightshift/index.html?gfx=cine&lang=${lang}&rs=${process.env.RS || '.85'}`);
    await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
    for (const [name, sc, bi, t, act] of pick(SHOTS)) {
      const t0 = Date.now();
      if (bi === 'shift') {
        await p.evaluate(s => window.__ns.startScene(s), sc);
        await p.waitForFunction(() => window.__ns.mode === 'shift' && window.__ns.shf, null, { timeout: 120000 });
        await p.waitForTimeout(2500); if (act === 'log') await p.click('#shLog');
        await p.waitForTimeout(1500);
      } else if (bi === 'end') {
        await p.evaluate(s => window.__ns.startScene(s, true), sc);
        for (let i = 0; i < 40 && await p.evaluate(() => window.__ns.mode) === 'play'; i++) { await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(500); }
        await p.waitForTimeout(1500);
      } else {
        await p.evaluate(([s, i]) => window.__ns.startScene(s, i), [sc, bi]);
        await p.waitForFunction(() => window.__ns.mode !== 'play' || window.__ns.bdur > 0, null, { timeout: 120000 }).catch(() => {});
        const dur = await p.evaluate(() => window.__ns.bdur);
        await p.evaluate(t => window.__ns.seek(t), Math.min(t, Math.max(0, dur - .05)));
        await p.waitForFunction(() => window.__ns.held, null, { polling: 50, timeout: 300000 });
      }
      const f = path.join(dir, `${name}_${lang}.png`); await p.screenshot({ path: f, timeout: 300000 });
      console.log(`снимок ${path.basename(f)} — ${((Date.now() - t0) / 1000).toFixed(0)} с`);
      await p.evaluate(() => window.__ns.pause(false)).catch(() => {});
    }
  }
  await p.close();
}

(async () => {
  const b = await chromium.launch({ args: ARGS });
  const all = !what.length;
  if (all || what.includes('frames')) await frames(b);
  if (all || what.includes('shots')) await shots(b);
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
