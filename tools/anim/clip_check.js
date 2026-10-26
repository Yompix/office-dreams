// Быстрая проверка проникновений (тот же детектор World.clips(), что в tools/story/sheet.js, без снимков и листов):
// каждый кадр каждой сцены в трёх точках, по умолчанию в «Ретро» (поза не зависит от графики, а рисуется в разы быстрее).
//   PORT=8783 node tools/anim/clip_check.js [фильтр сцен — regexp] [retro|cine] [P|L] > отчёт.txt   (SOLID=1|0 — твёрдый мир вкл/выкл; STEP=0.5 — снимок каждые полсекунды)
// Ошибка — как в sheet.js: тело в предмете ≥ 4 см или насквозь (тонкая доска), под полом ≥ 6 см и ≥ 15 вершин; в кадре или за ним — пишется отдельно.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const [filt = '^(?!over_)', gfx = 'retro', ori = 'P'] = process.argv.slice(2), port = process.env.PORT || 8771, land = /^l/i.test(ori);
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'];
const NAMES = { temych: 'Итан', mihalych: 'Фрэнк', cleaner: 'Гас', katya: 'Лили', katyaSmile: 'Лили', katyaThing: '«Лили»', smenshik: 'сменщик', monster: 'монстр', victim: 'жертва', martha: 'Марта' };
(async () => {
  const w = {}; new Function('window', fs.readFileSync(path.join(__dirname, '../../public/games/nightshift/story.js'), 'utf8'))(w);
  const ids = Object.entries(w.STORY.scenes).filter(([id, s]) => (s.beats || []).length && new RegExp(filt).test(id)).map(([id]) => id);
  const b = await chromium.launch({ args: ARGS }), p = await b.newPage({ viewport: land ? { width: 640, height: 360 } : { width: 360, height: 640 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e.message || e).slice(0, 200)));
  await p.addInitScript(([l, sol]) => { localStorage.setItem('ns1_land3', l ? 'true' : 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); if (sol != null) window.__SOLID = +sol; window.__SOLLOG = []; }, [land, process.env.SOLID ?? null]);   // SOLID=1 — с твёрдым миром, 0 — без
  await p.goto(`http://127.0.0.1:${port}/nightshift/index.html?gfx=${gfx}&lang=ru&cs=.4${gfx === 'cine' ? '&rs=.3' : ''}`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
  let n = 0, bad = 0, warn = 0; const t0 = Date.now();
  for (const id of ids) {
    const beats = await p.evaluate(id => window.STORY.scenes[id].beats.map(b => ({ if: b.if || null })), id);
    for (let bi = 0; bi < beats.length; bi++) {
      const fl = {}; for (const k of [].concat(beats[bi].if || [])) { if (k[0] === '!') fl[k.slice(1)] = 0; else fl[k] = 1; }
      await p.evaluate(([id, bi, fl]) => { window.__ns.setFlags(fl); window.__ns.startScene(id, bi); }, [id, bi, fl]);
      await p.waitForFunction(() => window.__ns.mode !== 'play' || window.__ns.bdur > 0, null, { timeout: 30000 }).catch(() => {});
      const st = await p.evaluate(() => [window.__ns.mode, window.__ns.bi, window.__ns.bdur, window.__ns.beatImg]);
      if (st[0] !== 'play' || st[1] !== bi || !st[3]) continue;
      const TS = process.env.STEP ? Array.from({ length: Math.max(1, Math.floor(st[2] / +process.env.STEP)) }, (_, i) => .3 + i * +process.env.STEP).filter(t => t < st[2]) : [.35, st[2] / 2, Math.max(.4, st[2] - .35)];
      for (const t of TS) {
        await p.evaluate(t => window.__ns.seek(t), t);
        await p.waitForFunction(() => window.__ns.held, null, { polling: 30, timeout: 120000 });
        const cl = await p.evaluate(() => window.World.clips().filter(x => x.n >= 4 && x.d >= .025).slice(0, 5)); n++;
        for (const x of cl) {
          const off = !x.scr || !x.scr[2] || x.scr[0] < -.02 || x.scr[0] > 1.02 || x.scr[1] < -.02 || x.scr[1] > 1.02, nm = NAMES[x.k] || x.k;
          let err, msg;
          if (x.prop === 'пол') { if (x.d < .04) continue; err = x.d >= .06 && x.n >= 15; msg = `${nm} под полом ${Math.round(x.d * 100)} см (${x.n} верш.)`; }
          else { const through = x.d >= Math.min(...x.size) / 2 - .006; err = x.d >= .04 || (through && x.n >= 6); msg = `${nm} в «${x.prop}» ${x.size.join('×')} — ${through ? 'насквозь' : Math.round(x.d * 100) + ' см'} (${x.n} верш.)`; }
          if (err && !off) bad++; else warn++;
          console.log(`${err && !off ? 'ОШИБКА' : 'проверить'} ${id}:${bi}:${t.toFixed(1)} ${off ? '(за кадром) ' : ''}${msg}`);
        }
      }
    }
  }
  const sl = await p.evaluate(() => (window.__SOLLOG || []).reduce((m, [id, k, x, z]) => { const d = Math.hypot(x, z), key = id + ' ' + k; if (!m[key] || m[key] < d) m[key] = +d.toFixed(2); return m; }, {}));
  const big = Object.entries(sl).sort((a, b) => b[1] - a[1]); if (big.length) console.log('отодвинуты (кадр, кто, max м): ' + big.slice(0, 40).map(([k, d]) => k + ' ' + d).join(' | '));
  console.log(`итого: ${n} снимков, ошибок ${bad}, проверить ${warn}, ${Math.round((Date.now() - t0) / 1000)} с${errs.length ? '\nОШИБКИ JS: ' + errs.slice(0, 5).join(' | ') : ''}`);
  await b.close();
})();
