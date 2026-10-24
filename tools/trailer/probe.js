// «Hush Till Six» — какие сегменты трейлера одинаковы на двух языках (нет надписей в кадре и никто не говорит в кадре):
// их кадры английская версия берёт из русской, рендер почти вдвое короче.
//   node tools/trailer/probe.js TIMELINE_RU.json TIMELINE_EN.json OUT.json
// Для каждого сегмента — по кадру на русском и английском (две страницы, одинаковый Math.random), сравнение пикселей,
// плюс проверка, видна ли голова говорящего (тогда губы разные — нужен свой рендер).
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const [ruPath, enPath, outPath] = process.argv.slice(2);
const RU = JSON.parse(fs.readFileSync(ruPath)), EN = JSON.parse(fs.readFileSync(enPath)), LAND = RU.fmt === 'yt';
const [W, H] = LAND ? [480, 270] : [270, 480];
const scrX = x => LAND ? .5 + (x - .5) * (9 / 16) / (16 / 9) : x;
const HTML = lang => `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script>{ let s = 12345; Math.random = () => { s = (s + 0x6D2B79F5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; window.__seed = n => { s = n | 0; }; }</script>
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script>${lang === 'en' ? '<script src="i18n.js"></script>' : ''}<script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script>
<canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
async function page(b, lang) {
  const p = await b.newPage({ viewport: { width: W, height: H } });
  await p.route(/\/nightshift\/_pr\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML(lang) }));
  await p.goto('http://127.0.0.1:8771/nightshift/_pr.html?gfx=cine&lang=' + lang);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 120000 });
  await p.evaluate(async land => { window.__W3core.V.rs = .45; window.__W3core.V.rsPin = true; window.__seed(777); await window.__W3chars.load(); World.init(); if (land) World.setLand(true); }, LAND);
  return p;
}
// кадр сегмента: прогрев и снимок в оттенках серого; who — кто говорит в этом сегменте (видна ли его голова)
const shoot = (p, s, who) => p.evaluate(([s, who, land]) => {
  const c = document.getElementById('c'), g = c.getContext('2d'), cam = (land ? s.camL : s.camP) || s.cam || [.5, .5, 1, 1], raw = land && !!s.camL;
  const sx = x => land ? .5 + (x - .5) * (9 / 16) / (16 / 9) : x, shift = /^cam(\d|_m2)/.test(s.shot), tau = Math.min(1, s.dur / 2);
  window.__seed(4242); let heads = [];
  for (let i = -4; i <= 0; i++) {
    const t = Math.max(0, (s.t0 || 0) + tau + i / 24), T = 100 + t, z = (cam[2] + cam[3]) / 2;
    const o = { t: shift ? T : t, T, z, props: Object.assign({ clock: '03:00', jacket: 1, badge: 1, book: 0 }, s.props || {}), cctv: s.cctv ? 1 : 0, settle: i === -4, sx: 0, sy: 0 };
    if (Array.isArray(cam[0])) { const q = World.projectAt(s.shot, cam[0]); o.cx = land ? .5 + (q[0] - .5) : q[0]; o.cy = q[1] + (cam[1] || 0); } else { o.cx = raw ? cam[0] : sx(cam[0]); o.cy = cam[1]; }
    World.draw(g, c.width, c.height, s.shot, o);
  }
  const H2 = World.heads ? World.heads() : {}, N = World.names || {};
  for (const w of who) for (const k of N[w] || []) { const h = H2[k]; if (h && h[2] && h[0] > -.05 && h[0] < 1.05 && h[1] > -.05 && h[1] < 1.05) heads.push(w + ':' + k); }
  const d = g.getImageData(0, 0, c.width, c.height).data, gray = new Array(c.width * c.height);
  for (let i = 0; i < gray.length; i++) gray[i] = (d[i * 4] * 2 + d[i * 4 + 1] * 5 + d[i * 4 + 2]) >> 3;
  return { gray, heads };
}, [s, who, LAND]);

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const [pr, pe] = await Promise.all([page(b, 'ru'), page(b, 'en')]);
  const res = { fmt: RU.fmt, neutral: [], specific: [], why: {} };
  for (const s of RU.segs.filter(x => x.shot)) {
    const se = EN.segs.find(x => x.id === s.id); if (!se) continue;
    const who = [...new Set([...RU.vo, ...EN.vo].filter(v => v.aloud && v.seg === s.id).map(v => v.who))];
    const [a, e] = await Promise.all([shoot(pr, s, who), shoot(pe, Object.assign({}, se, { dur: s.dur }), who)]);
    let n = 0, sum = 0; for (let i = 0; i < a.gray.length; i++) { const dd = Math.abs(a.gray[i] - e.gray[i]); sum += dd; if (dd > 24) n++; }
    const diff = n > 12, talk = a.heads.length > 0, same = JSON.stringify([s.shot, s.t0, s.cam, s.camL, s.camP, s.props, s.cctv ? 1 : 0, s.scareAt]) === JSON.stringify([se.shot, se.t0, se.cam, se.camL, se.camP, se.props, se.cctv ? 1 : 0, se.scareAt]) && se.n <= s.n + 3;   // длина может отличаться на пару кадров: comp.js держит последний кадр
    const why = [diff ? `надписи (${n} пикс.)` : '', talk ? 'говорит в кадре: ' + a.heads.join(',') : '', same ? '' : 'другой монтаж'].filter(Boolean);
    (why.length ? res.specific : res.neutral).push(s.id); res.why[s.id] = why.join('; ') || `одинаково (${n} пикс., ср. ${(sum / a.gray.length).toFixed(2)})`;
    console.log(s.id.padEnd(8), res.why[s.id]);
  }
  fs.writeFileSync(outPath, JSON.stringify(res, null, 1));
  console.log('одинаковых', res.neutral.length, 'своих', res.specific.length);
  await b.close();
})();
