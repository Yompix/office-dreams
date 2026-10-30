// Смерти для Godot: бросок в объектив (death_scream) и финал «как оно убивает» — кадры gore@<база>:<вид> старой игры.
// Их нет в списке кадров (scenes.js собирает их на лету: goreShot), поэтому страница игры здесь чуть подправлена в памяти —
// goreShot доступен снаружи (файлы игры не меняются). Таблицы смертей (GORE_KIND, GORE_BASE) читаются из engine.js.
//   node tools/godot/bake_gore.js  →  godot/nightshift/bake/gore/<база>__<вид>[_c].json + .bin, index.json; bake/death_scream.*
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), core = require('./bake_core.js');
const ROOT = path.join(__dirname, '../..'), OUT = path.join(ROOT, 'godot/nightshift/bake'), SRC = path.join(ROOT, 'public/games/nightshift');
const story = JSON.parse(fs.readFileSync(path.join(ROOT, 'godot/nightshift/data/story.json'), 'utf8'));
const eng = fs.readFileSync(path.join(SRC, 'engine.js'), 'utf8');
const obj = name => { const m = new RegExp('const ' + name + ' = (\\{[\\s\\S]*?\\});').exec(eng); if (!m) throw new Error('нет ' + name + ' в engine.js'); return Function('return ' + m[1])(); };
const GORE_KIND = obj('GORE_KIND'), GORE_BASE = obj('GORE_BASE'), JUMP_D = .75, GORE_D = 3.4;
const kindOf = id => GORE_KIND[id] || GORE_KIND[id.replace(/_(srv|hide)$/, '')] || null;
(async () => {
  fs.mkdirSync(path.join(OUT, 'gore'), { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.route(/scenes\.js/, async r => { const res = await r.fetch(); let s = await res.text();
    if (!s.includes('function goreShot(id) {')) throw new Error('goreShot не найден в scenes.js');
    s = s.replace('function goreShot(id) {', 'window.__goreShot = goreShot; function goreShot(id) {'); await r.fulfill({ response: res, body: s }); });
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.evaluate(require('./naming.js')); await p.evaluate(() => { window.__R0 = window.__W3core.R.render.bind(window.__W3core.R); });
  const save = (file, r) => { const bin = Buffer.from(r.bin, 'base64'); fs.writeFileSync(path.join(OUT, file + '.bin'), bin); fs.writeFileSync(path.join(OUT, file + '.json'), JSON.stringify(r.meta)); return bin.length; };
  let total = 0; const index = {};
  { const r = await p.evaluate(core, ['death_scream', { dur: 3.2, fps: 30, hold: 0 }]); if (r.err) console.log('death_scream', r.err); else { total += save('death_scream', r); console.log('death_scream', r.meta.frames, 'кадров'); } }
  for (const [sid, sc] of Object.entries(story.scenes)) {
    if (!sid.startsWith('over_')) continue; const kind = kindOf(sid); if (!kind) continue;
    const bk = (sc.beats || []).find(x => x.black != null) || (sc.beats || [])[0] || {};
    const base = GORE_BASE[sid] || bk.img; if (!base) { console.log(sid, 'нет кадра-основы'); continue; }
    const gid = `gore@${base}:${kind}`, tl = await p.evaluate(id => window.World.goreTL(id), gid);
    const ids = [gid].concat(!tl && kind !== 'drag' && !/^drag/.test(kind) ? [gid + '|c'] : []);   // без сценария: общий план → крупно рана → общий
    index[sid] = { kind, base, tl: tl ? { d: tl.d, hit: tl.hit, splat: tl.splat, sfx: tl.sfx || [] } : null, files: [] };
    for (const id of ids) {
      const file = 'gore/' + base + '__' + kind + (id.endsWith('|c') ? '_c' : '');
      if (index[sid].files.includes(file) || Object.values(index).some(v => v !== index[sid] && v.files.includes(file))) { index[sid].files.push(file); continue; }
      let r; try { r = await p.evaluate(core, [id, { dur: (tl ? tl.d : GORE_D) + .3, fps: 30, hold: 0 }]); } catch (e) { console.log(id, 'ошибка:', String(e).split('\n')[0].slice(0, 160)); continue; }
      if (r.err) { console.log(id, r.err); continue; }
      r.meta.id = file; total += save(file, r); index[sid].files.push(file);
      console.log(sid, id, r.meta.frames, 'кадров,', r.meta.chans.length, 'каналов');
    }
  }
  fs.writeFileSync(path.join(OUT, 'gore', 'index.json'), JSON.stringify({ jump: JUMP_D, gore: GORE_D, overs: index }, null, 1));
  console.log('всего', (total / 1e6).toFixed(1), 'МБ;', errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
