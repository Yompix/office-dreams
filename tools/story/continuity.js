// Сквозная проверка «кто где и когда»: обходит сценарий от intro, у каждой сцены — время ночи (своё, от сцены-родителя или от часа смены, где можно умереть),
// у каждого кадра — кто в кадре (World.heads(): голова видна на экране) в начале, середине и конце кадра; камеры смены — с тем, что на них в каждом часе (st).
// Правила (время — минуты ночи, как toMin в engine.js):
//   • Гас живой (cleaner) — только 22:25–03:00 (в 03:00 он пропал; запись с камеры и фото — можно); мёртвый (victim) — только после 03:00;
//   • Фрэнк (mihalych) — только после 04:10 (нашёлся на CAM −2), в концовке «Новая запись» (morning) его уже нет; на фото — можно;
//   • дневной охранник (smenshik) — 21:00–21:05 и с 06:00 (между — только по телефону);
//   • «Лили» (katya…) — только оно, с 03:30 (настоящая Лили в общежитии); двойник Итана — только в смертях; Марта — только на фото;
//   • Итана нет в будке на камерах, пока он в серверной (S6) или под стойкой (A1).
//   node tools/story/continuity.js [ПАПКА]   → таблица ПАПКА/continuity.txt (сцена, время, кадр, кто в кадре) и нарушения; код выхода 1 — есть нарушения.
//   PORT=8771 — сервер статики (cd public/games && python3 -m http.server 8771 --bind 127.0.0.1); ORI=P|L|PL (по умолчанию PL); ONLY=regexp — только эти сцены
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const G = path.join(__dirname, '../../public/games/nightshift'), out = process.argv[2] || '/tmp/continuity', PORT = process.env.PORT || 8771, ORI = process.env.ORI || 'PL';
const only = process.env.ONLY ? new RegExp(process.env.ONLY) : null;
const window = {}; new Function('window', fs.readFileSync(path.join(G, 'story.js'), 'utf8'))(window);
const S = window.STORY, SC = S.scenes, SG = S.shift.segs, AN = S.shift.an, TIM = JSON.parse(fs.readFileSync(path.join(G, 'timing.json'), 'utf8'));
const toMin = s => { const [h, m] = s.split(':').map(Number); return (h < 12 ? h + 24 : h) * 60 + m; }, minStr = v => String(Math.floor(v / 60) % 24).padStart(2, '0') + ':' + String(v % 60).padStart(2, '0');
// ---- время каждой сцены: своё (time), иначе — откуда в неё пришли (обход: сцена с then.back — время родителя); смерти смены — часы, где так можно умереть
const times = {}, fromSeg = {};   // сцена → Set минут; смерть → из каких часов смены (S6 — Итан в серверной, A1 — под стойкой)
const add = (id, m) => { if (!SC[id] || m == null) return false; const s = times[id] || (times[id] = new Set()); if (s.has(m)) return false; s.add(m); return true; };
const edges = id => { const th = (SC[id] || {}).then || {}, r = [];
  for (const k of ['goto', 'win', 'lose', 'back']) if (th[k]) r.push(th[k]);
  for (const o of th.opts || []) r.push(o.go);
  for (const A of [th.explore, th.talk]) if (A) { for (const x of A.spots || A.topics || []) r.push(x.go); const lv = A.leave || A.bye; if (lv) r.push(lv.go); }
  if (th.shift) r.push('sh_' + th.shift);
  return r; };
const segDeaths = id => { const sg = SG[id], d = new Set(['over_come']); const anD = a => { const def = AN[a]; if (!def) return; if (def.die) d.add(def.die); if (def.stare) { d.add('over_stare'); d.add('over_stare_b'); } };
  for (const e of sg.events) { if (e.an) anD(e.an); for (const x of [e.call, e.hold]) if (x && x.die) d.add(x.die); }
  for (const a of (sg.rnd || {}).pool || []) anD(a);
  if (sg.events.some(e => e.c13) || ['S5', 'S6', 'A1'].includes(id)) d.add('over_k1');   // CAM 13 на пульте — с 02:00 (S4) и до утра
  return [...d]; };
{ const q = [S.start]; add(S.start, toMin(SC[S.start].time));
  while (q.length) { const id = q.shift(), sc = SC[id], ms = [...(times[id] || [])];
    // час смены: начало и конец часа; из него — следующая сцена и смерти этого часа
    const sh = (sc.then || {}).shift;
    if (sh) { const sg = SG[sh]; for (const d of segDeaths(sh)) (fromSeg[d] = fromSeg[d] || new Set()).add(sh);
      for (const to of [sg.next, ...segDeaths(sh)]) for (const m of [toMin(sg.from), toMin(sg.to)]) if (add(to, (SC[to] || {}).time && to === sg.next ? toMin(SC[to].time) : m)) q.push(to); continue; }
    const last = sc.beats && sc.beats.length ? Math.max(...ms, ...sc.beats.filter(b => b.time).map(b => toMin(b.time))) : Math.max(...ms);
    for (const to of edges(id)) { const t2 = SC[to] && SC[to].time ? toMin(SC[to].time) : last; if (add(to, t2)) q.push(to); } } }
// ---- кадры: длина как в игре (реплики + паузы), три точки на кадр
const list = [];
for (const [id, sc] of Object.entries(SC)) { if (only && !only.test(id)) continue; if (!times[id]) { list.push({ scene: id, unreached: 1 }); continue; }
  let off = 0, prevImg = null, bt = null;
  (sc.beats || []).forEach((b, i) => { if (b.time) bt = toMin(b.time);
    let t = b.sayAt ?? .15; for (const k of b.say || []) t += ((TIM[k] || {}).dur || 2) + .3; if (b.text) t += Math.max(2.2, b.text.length / 14 + 1.3);
    const d = Math.max(t + (b.pad || .3), 1.2), o = b.img && b.img === prevImg ? off : 0; prevImg = b.img; off = o + d;
    if (!b.img) return;
    const imgs = [...new Set([b.img, ...(b.cams || []).map(c => c[4]).filter(Boolean)])];
    for (const img of imgs) list.push({ scene: id, i, img, ts: [o + .3, o + d / 2, o + d - .05].map(x => +x.toFixed(2)), mins: bt != null ? [bt] : [...times[id]] }); }); }
// камеры смены: в каждом часе — с тем, что на них с начала часа (S2: Гас приходит в 22:25)
const camList = [];
for (const [id, sg] of Object.entries(SG)) { const st0 = Object.assign({}, sg.st || {}), noEthan = id === 'S6' || id === 'A1' ? 1 : 0;
  const pts = [[toMin(sg.from), st0]]; for (const e of sg.events) if (e.state) pts.push([toMin(e.at), Object.assign({}, pts[pts.length - 1][1], e.state)]);
  for (const [m, st] of pts) for (const c of sg.cams) camList.push({ seg: id, cam: c, img: 'cam' + (c === 'm2' ? '_m2' : c), min: m, sh: Object.assign({ clk: m }, st, noEthan ? { noEthan } : {}) }); }
const RULES = [
  ['cleaner', 'Гас живой', (m, x) => (m >= toMin('22:25') && m <= toMin('03:00')) || /^(ny_photo|f7_stop)$/.test(x.img)],   // f7_stop в 03:02 — запись камеры (перемотка)
  ['victim', 'Гас мёртвый', m => m >= toMin('03:00')],
  ['mihalych', 'Фрэнк', (m, x) => x.img === 'ny_photo' || (m >= toMin('04:10') && x.scene !== 'morning')],
  ['smenshik', 'дневной охранник', m => m <= toMin('21:05') || m >= toMin('06:00')],
  ['katya', '«Лили»', m => m >= toMin('03:30')], ['katyaSmile', '«Лили»', m => m >= toMin('03:30')], ['katyaThing', '«Лили»', m => m >= toMin('03:30')],
  ['ethanThing', 'двойник Итана', (m, x) => /^over_/.test(x.scene)], ['martha', 'Марта', (m, x) => x.img === 'ny_photo']];
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script>
<canvas id="c" width="216" height="384"></canvas></body></html>`;
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 400, height: 400 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.route(/\/nightshift\/_cont\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${PORT}/nightshift/_cont.html?gfx=retro`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 120000 });
  await p.evaluate(async () => { await window.__W3chars.load(); World.init(); });
  // кто в кадре: все точки кадра одним вызовом (на загруженной машине каждый вызов страницы дорог)
  const who = (img, ts, props) => p.evaluate(([img, ts, props]) => { const c = document.getElementById('c'), r = new Set();
    for (const t of ts) { World.draw(c.getContext('2d'), c.width, c.height, img, { t, T: t + 100, cx: .5, cy: .5, z: 1, props: props || undefined });
      const h = World.heads(); for (const k in h) { const q = h[k]; if (q[2] && q[0] > -.02 && q[0] < 1.02 && q[1] > -.02 && q[1] < 1.02) r.add(k); } }
    const sh = World.shots[(World.alias && World.alias[img]) || img]; if (sh && sh.loc === 'lobby' && r.has('temych')) r.add('@lobby');   // Итан в холле/будке
    return [...r]; }, [img, ts, props]);
  const oris = ORI.includes('P') && ORI.includes('L') ? [false, true] : [ORI === 'L'];
  const rows = [], bad = [], seenL = list.map(() => new Set()), seenC = camList.map(() => new Set());
  const check = (x, ks, mins, where) => { for (const [k, nm, ok] of RULES) if (ks.includes(k)) for (const m of mins) if (!ok(m, x)) { bad.push(`${where}: ${nm} (${k}) в ${minStr(m)}`); break; } };
  for (const land of oris) {
    await p.evaluate(l => { const c = document.getElementById('c'); c.width = l ? 384 : 216; c.height = l ? 216 : 384; World.setLand(l); }, land);
    for (let i = 0; i < list.length; i++) if (!list[i].unreached) for (const k of await who(list[i].img, list[i].ts)) seenL[i].add(k);
    for (let i = 0; i < camList.length; i++) if (!only || only.test('sh_' + camList[i].seg)) for (const k of await who(camList[i].img, [.5, 3], { sh: camList[i].sh })) seenC[i].add(k);
  }
  list.forEach((x, i) => {
    if (x.unreached) { if (!/^sh_/.test(x.scene)) rows.push(`${x.scene.padEnd(14)} —  в сцену никто не ведёт (кроме движка)`); return; }
    const ks = [...seenL[i]].sort();
    rows.push(`${x.scene.padEnd(14)} ${x.mins.map(minStr).join('/').padEnd(18)} [${x.i}] ${x.img.padEnd(18)} ${ks.filter(k => k[0] !== '@').join(', ') || '—'}`);
    check(x, ks, x.mins, `${x.scene}[${x.i}] ${x.img}`);
    // смерть из часа S6: Итан всю ночь в серверной с Фрэнком — в будке его быть не может
    if (ks.includes('@lobby') && fromSeg[x.scene] && fromSeg[x.scene].has('S6')) bad.push(`${x.scene}[${x.i}] ${x.img}: Итан в будке, а смерть бывает и в часе S6 (он в серверной)`);
  });
  rows.push('', '---- камеры смены (что на них с начала часа и после событий state)');
  for (const [i, x] of camList.entries()) {
    if (only && !only.test('sh_' + x.seg)) continue;
    const ks = [...seenC[i]].sort();
    rows.push(`${x.seg.padEnd(4)} ${minStr(x.min)} ${x.img.padEnd(8)} ${JSON.stringify(x.sh).padEnd(54)} ${ks.join(', ') || '—'}`);
    check({ scene: 'sh_' + x.seg, img: x.img }, ks, [x.min], `час ${x.seg} ${x.img} ${minStr(x.min)}`);
    if (x.sh.noEthan && ks.includes('temych')) bad.push(`час ${x.seg} ${x.img}: Итан в будке, а он ${x.seg === 'S6' ? 'в серверной' : 'под стойкой'}`);
  }
  if (errs.length) bad.push(...errs.map(e => 'ошибка JS: ' + e));
  fs.writeFileSync(path.join(out, 'continuity.txt'), rows.join('\n') + '\n\nНАРУШЕНИЯ:\n' + (bad.join('\n') || 'нет') + '\n');
  console.log(rows.length, 'строк →', path.join(out, 'continuity.txt'));
  for (const s of bad) console.log('  ✗', s);
  console.log(bad.length ? `НАРУШЕНИЙ: ${bad.length}` : 'кто где — без противоречий');
  await b.close(); process.exit(bad.length ? 1 : 0);
})();
