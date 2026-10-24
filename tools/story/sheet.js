// Автопроверка кадров: каждый кадр каждой сцены в трёх точках (начало, середина, конец) → листы-миниатюры + отчёт детекторов.
//   node tools/story/sheet.js ПАПКА [P|L|PL] [retro|cine] [ru|en] [фильтр сцен — regexp]
//   JOBS=3 — браузеров параллельно; RS=.45 — разрешение «Кино»; PORT=8771 — сервер (cd public/games && python3 -m http.server 8771 --bind 127.0.0.1)
// Детекторы (ПАПКА/report.txt, в листах — красная рамка и подпись):
//   • проникновение тела в предмет (стул, стол, стойка…) — World.clips(): вершины тела внутри коробок/цилиндров, глубина и сколько вершин;
//   • говорит вслух, а лица нет в кадре / лицо за краем / лицо под субтитрами (кроме рации и телефона);
//   • чёрный кадр там, где должна быть картинка; • ошибка JS.
// Кадр ставится перемоткой (__ns.seek): не зависит от скорости рендера. Листы — ПАПКА/sheet_P_01.png…, всё вместе — ПАПКА/index.html.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const [out, ori = 'PL', gfx = 'retro', lang = 'ru', filt] = process.argv.slice(2);
if (!out) { console.log('node tools/story/sheet.js ПАПКА [P|L|PL] [retro|cine] [ru|en] [фильтр]'); process.exit(2); }
const JOBS = +process.env.JOBS || 3, RS = process.env.RS || (gfx === 'cine' ? '.45' : ''), PORT = process.env.PORT || 8771;
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'];
const CS = process.env.CS || '.5';   // 2D-холст игры вдвое меньше: миниатюры всё равно маленькие, а рисовать в 4 раза быстрее
const URL = `http://127.0.0.1:${PORT}/nightshift/index.html?gfx=${gfx}&lang=${lang}&cs=${CS}${RS ? '&rs=' + RS : ''}`;
fs.mkdirSync(path.join(out, 'th'), { recursive: true });

// в странице: всё, что можно проверить по кадру
function probe() {
  const N = window.__ns, W = window.World, cv = document.getElementById('cv'), c = document.createElement('canvas'); c.width = 18; c.height = 32;
  const g = c.getContext('2d'); g.drawImage(cv, 0, 0, 18, 32); const px = g.getImageData(0, 0, 18, 32).data; let s = 0;
  for (let i = 0; i < px.length; i += 4) s += .2126 * px[i] + .7152 * px[i + 1] + .0722 * px[i + 2];
  const st = document.getElementById('stage').getBoundingClientRect(), dl = document.getElementById('dlg'), dr = dl.classList.contains('hide') ? null : dl.getBoundingClientRect();
  const who = N.talkWho, H = W.heads ? W.heads() : {}, keys = (W.names || {})[who] || [], hk = keys.find(k => H[k]);
  const clips = W.clips && N.beatImg ? W.clips().filter(x => x.n >= 4 && x.d >= .025).slice(0, 5) : [];   // кадр без 3D (телефон, текст) — не проверяем
  return { lum: +(s / (px.length / 4)).toFixed(1), who: who || null, fx: N.talkFx || '', head: hk ? H[hk].map(v => typeof v === 'number' ? +v.toFixed(3) : v) : null,
    dTop: dr ? +((dr.top - st.top) / st.height).toFixed(3) : 1, dL: dr ? +((dr.left - st.left) / st.width).toFixed(3) : 0, dR: dr ? +((dr.right - st.left) / st.width).toFixed(3) : 1,
    clips: clips.map(x => ({ k: x.k, prop: x.prop, size: x.size, d: x.d, n: x.n, scr: x.scr })) };
}
const NAMES = { temych: 'Итан', mihalych: 'Фрэнк', cleaner: 'Гас', katya: 'Лили', katyaSmile: 'Лили', katyaThing: '«Лили»', smenshik: 'сменщик', monster: 'монстр', victim: 'жертва', martha: 'Марта' };
function verdict(r) {
  const err = [], warn = [];
  if (r.lum < 2.5 && r.img && !/^(dark|strobe|lunge)$/.test(r.bfx)) err.push('чёрный кадр');
  for (const x of r.clips || []) {   // глубина в тонкой доске не больше половины её толщины: дошло до середины — значит, насквозь
    const off = !x.scr || !x.scr[2] || x.scr[0] < -.02 || x.scr[0] > 1.02 || x.scr[1] < -.02 || x.scr[1] > 1.02, tag = off ? '(за кадром) ' : '';   // самая глубокая точка вне кадра — игрок не видит: «проверить», не ошибка
    if (x.prop === 'пол') { if (x.d < .04) continue; (x.d >= .06 && x.n >= 15 && !off ? err : warn).push(tag + `${NAMES[x.k] || x.k} ушёл под пол на ${Math.round(x.d * 100)} см (${x.n} верш.)`); continue; }   // подошва на 2–4 см в полу не видна
    const through = x.d >= Math.min(...x.size) / 2 - .006;
    ((x.d >= .04 || (through && x.n >= 6)) && !off ? err : warn).push(tag + `${NAMES[x.k] || x.k} в предмете${x.prop ? ' «' + x.prop + '»' : ''} ${x.size.join('×')} м — ${through ? 'насквозь' : 'на ' + Math.round(x.d * 100) + ' см'} (${x.n} верш.)`); }
  if (r.who && r.who !== 'ИТАН' && r.img && !/^(radio|phone|fake|muffle)$/.test(r.fx)) {   // рация, телефон, «чужой» голос, голос за дверью — лица в кадре и не должно быть
    if (!r.head) warn.push(`говорит ${r.who} — его нет в кадре`);
    else if (!r.head[2] || r.head[0] < .03 || r.head[0] > .97 || r.head[1] < .03) warn.push(`лицо: ${r.who} за краем кадра`);
    else if (r.head[1] > r.dTop - .015 && r.head[0] > r.dL && r.head[0] < r.dR) warn.push(`лицо: ${r.who} под субтитрами`);
  }
  for (const e of r.errs || []) err.push('ошибка JS: ' + e);
  return { err, warn };
}
async function job(list, land, ji) {
  const b = await chromium.launch({ args: ARGS }), p = await b.newPage({ viewport: land ? { width: 640, height: 360 } : { width: 360, height: 640 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e.message || e).slice(0, 200)));
  await p.addInitScript(l => { localStorage.setItem('ns1_land3', l ? 'true' : 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); }, land);
  await p.goto(URL); await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  const res = []; let done = 0;
  for (const id of list) {
    const beats = await p.evaluate(id => window.STORY.scenes[id].beats.map(b => ({ if: b.if || null, img: b.img || null, phone: !!b.phone, fx: b.fx || '' })), id);
    for (let bi = 0; bi < beats.length; bi++) {
      const fl = {}; for (const k of [].concat(beats[bi].if || [])) { if (k[0] === '!') fl[k.slice(1)] = 0; else fl[k] = 1; }
      await p.evaluate(([id, bi, fl]) => { window.__ns.setFlags(fl); window.__ns.startScene(id, bi); }, [id, bi, fl]);
      await p.waitForFunction(() => window.__ns.mode !== 'play' || window.__ns.bdur > 0, null, { timeout: 30000 }).catch(() => {});
      const st = await p.evaluate(() => [window.__ns.mode, window.__ns.bi, window.__ns.bdur]);
      if (st[0] !== 'play' || st[1] !== bi) continue;
      const dur = st[2], ts = [.35, dur / 2, Math.max(.4, dur - .35)];
      for (let k = 0; k < 3; k++) {
        const e0 = errs.length;
        await p.evaluate(t => window.__ns.seek(t), ts[k]);
        await p.waitForFunction(() => window.__ns.held, null, { polling: 30, timeout: 120000 });   // кадр дорисован и держится
        const d = await p.evaluate(probe).catch(e => ({ lum: 99, clips: [], perr: String(e).slice(0, 120) }));
        const file = `${id}_${bi}_${k}${land ? 'L' : 'P'}.jpg`;
        await p.screenshot({ path: path.join(out, 'th', file), type: 'jpeg', quality: 72, timeout: 120000 });
        const r = Object.assign({ id, bi, k, t: +ts[k].toFixed(1), land, file, img: beats[bi].img, bfx: beats[bi].fx, errs: errs.slice(e0).concat(d.perr ? ['проба: ' + d.perr] : []) }, d);
        Object.assign(r, verdict(r)); res.push(r);
      }
    }
    done++; if (done % 5 === 0 || done === list.length) console.log(`  [${land ? 'L' : 'P'}${ji}] ${done}/${list.length} сцен`);
  }
  await b.close(); return res;
}
(async () => {
  const t0 = Date.now(), ONLY = process.env.REPORT_ONLY;   // REPORT_ONLY=1 — пересобрать отчёт и листы из ПАПКА/data.json без новых снимков
  const w = {}; new Function('window', fs.readFileSync(path.join(__dirname, '../../public/games/nightshift/story.js'), 'utf8'))(w);
  const meta = Object.entries(w.STORY.scenes).map(([id, s]) => [id, (s.beats || []).length, s.time || '']);
  let tm = ''; const time = {}; for (const [id, , t] of meta) { if (t) tm = t; time[id] = t || tm; }
  const ids = meta.filter(([id, n]) => n && (!filt || new RegExp(filt).test(id))).map(([id, n]) => [id, n]);
  const orients = ori.toUpperCase().split('').filter(c => c === 'P' || c === 'L');
  const all = ONLY ? JSON.parse(fs.readFileSync(path.join(out, 'data.json'), 'utf8')).filter(r => orients.includes(r.land ? 'L' : 'P')) : [];
  for (const o of ONLY ? [] : orients) {
    const land = o === 'L', slices = Array.from({ length: JOBS }, () => []), load = new Array(JOBS).fill(0);
    for (const [id, n] of [...ids].sort((a, b) => b[1] - a[1])) { const j = load.indexOf(Math.min(...load)); slices[j].push(id); load[j] += n; }
    console.log(`${o}: ${ids.length} сцен, ${ids.reduce((s, x) => s + x[1], 0)} кадров, ${JOBS} браузера`);
    const parts = await Promise.all(slices.map((s, j) => s.length ? job(s, land, j + 1) : []));
    all.push(...parts.flat());
  }
  const order = Object.fromEntries(meta.map(([id], i) => [id, i]));
  all.sort((a, b) => (a.land - b.land) || (order[a.id] - order[b.id]) || (a.bi - b.bi) || (a.k - b.k));
  for (const r of all) Object.assign(r, verdict(r));
  fs.writeFileSync(path.join(out, 'data.json'), JSON.stringify(all));
  // отчёт
  const lines = [], code = r => `[${r.land ? 'L' : 'P'}] ${r.id}:${r.bi}:${r.t}`;
  const E = all.filter(r => r.err.length), Wn = all.filter(r => !r.err.length && r.warn.length);
  lines.push(`Автопроверка кадров ${new Date().toISOString().slice(0, 16)} · ${gfx} · ${lang} · ${all.length} снимков · ошибок ${E.length} · проверить ${Wn.length} · ${Math.round((Date.now() - t0) / 1000)} с`, '');
  if (E.length) { lines.push('ОШИБКИ:'); for (const r of E) lines.push(`${code(r)} — ${r.err.join('; ')}${r.warn.length ? ' | ' + r.warn.join('; ') : ''}`); lines.push(''); }
  if (Wn.length) { lines.push('ПРОВЕРИТЬ:'); for (const r of Wn) lines.push(`${code(r)} — ${r.warn.join('; ')}`); }
  fs.writeFileSync(path.join(out, 'report.txt'), lines.join('\n') + '\n');
  // листы: сцены группами, у каждого кадра три снимка; красная рамка — ошибка, жёлтая — проверить; точки — место проникновения и лицо говорящего
  const esc = s => String(s).replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
  const th = r => { const w = r.land ? 192 : 108, h = r.land ? 108 : 192, cls = r.err.length ? 'e' : r.warn.length ? 'w' : '';
    const dots = (r.clips || []).filter(x => x.scr && x.scr[2]).map(x => `<i class="dc" style="left:${(x.scr[0] * w).toFixed(0)}px;top:${(x.scr[1] * h).toFixed(0)}px"></i>`).join('') + (r.head && r.who ? `<i class="dh" style="left:${(r.head[0] * w).toFixed(0)}px;top:${(r.head[1] * h).toFixed(0)}px"></i>` : '');
    return `<div class="t ${cls}"><div class="im" style="width:${w}px;height:${h}px"><img src="th/${r.file}" width="${w}" height="${h}">${dots}</div><div class="c">${r.bi}:${r.t}</div>${cls ? `<div class="f">${esc(r.err.concat(r.warn).join('; '))}</div>` : ''}</div>`; };
  const groups = [];
  for (const o of orients) { const land = o === 'L', rows = all.filter(r => r.land === land), sc = [...new Set(rows.map(r => r.id))]; for (let i = 0; i < sc.length; i += 8) groups.push({ o, sc: sc.slice(i, i + 8), rows }); }
  const html = `<!doctype html><meta charset="utf-8"><title>Кадры</title><style>body{margin:0;background:#0d0e11;color:#ddd;font:12px Arial}.g{padding:10px 12px;width:1600px}.g h2{font-size:15px;margin:4px 0 8px;color:#ffd36b}.s{margin:0 0 10px;border-top:1px solid #2a2c31;padding-top:6px}.s b{color:#fff;font-size:13px}.s b i{color:#ff6b6b;font-style:normal;margin-right:6px}.bs{display:flex;flex-wrap:wrap;gap:6px 14px;margin-top:4px}.bt{display:flex;gap:3px}.t{width:min-content}.im{position:relative;outline:2px solid transparent}.t.e .im{outline-color:#ff2d3a}.t.w .im{outline-color:#ffc23a}.c{color:#8a8f97;font-size:10px}.f{color:#ff9a8a;font-size:10px;max-width:192px;line-height:1.2}.dc,.dh{position:absolute;width:10px;height:10px;margin:-6px 0 0 -6px;border-radius:50%;border:2px solid #ff2d3a}.dh{border-color:#ffd36b}</style>
${groups.map((G, gi) => `<div class="g" id="g${gi}"><h2>${G.o === 'L' ? 'Горизонталь' : 'Вертикаль'} · ${gfx} · ${lang} · лист ${gi + 1}</h2>${G.sc.map(id => { const rs = G.rows.filter(r => r.id === id), bis = [...new Set(rs.map(r => r.bi))];
    return `<div class="s"><b><i>${esc(time[id] || '')}</i>${esc(id)}</b><div class="bs">${bis.map(bi => `<div class="bt">${rs.filter(r => r.bi === bi).map(th).join('')}</div>`).join('')}</div></div>`; }).join('')}</div>`).join('')}`;
  fs.writeFileSync(path.join(out, 'index.html'), html);
  const b = await chromium.launch({ args: ARGS }), p = await b.newPage({ viewport: { width: 1624, height: 1000 } });
  await p.goto('file://' + path.resolve(out, 'index.html')); await p.waitForLoadState('load');
  const per = {};
  for (let gi = 0; gi < groups.length; gi++) { const o = groups[gi].o; per[o] = (per[o] || 0) + 1; await (await p.$('#g' + gi)).screenshot({ path: path.join(out, `sheet_${o}_${String(per[o]).padStart(2, '0')}.png`) }); }
  await b.close();
  console.log(lines.slice(0, 1).join('') + `\nотчёт: ${path.join(out, 'report.txt')}\nлисты: ${groups.length} шт. в ${out}`);
})();
