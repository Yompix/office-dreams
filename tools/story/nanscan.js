// Ищет места, где на настоящей видеокарте получится NaN (чёрные точки/линии): программный рендер считает pow(отрицательное) «по модулю»,
// а видеокарты — NaN. Страница B получает макросы pow/log/log2, которые дают NaN для отрицательного основания, как на видеокарте.
//   node tools/story/nanscan.js OUT [cine|ultra|retro] [W H] [фильтр кадров] [шаг]   → OUT/plain, OUT/hook (все планы сюжета)
//   python3 tools/story/nandiff.py OUT   → список кадров с почерневшими точками и OUT/diff/*.jpg (слева обычный, справа «как на видеокарте»: фиолетовое — NaN)
//   (сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1)
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const [out, gfx = 'cine', W = 960, H = 540, filt = '', step = 1] = process.argv.slice(2);
const HOOK = () => {
  const inj = '#define pow(a,b) (sqrt(sign(a))*pow(abs(a),(b)))\n#define log2(a) (log2(a)+(sqrt(sign(a))-sqrt(sign(a))))\n#define log(a) (log(a)+(sqrt(sign(a))-sqrt(sign(a))))\n';
  for (const P of [WebGL2RenderingContext.prototype, WebGLRenderingContext.prototype]) { const o = P.shaderSource;
    P.shaderSource = function (sh, src) { if (/^#version[^\n]*\n/.test(src)) src = src.replace(/^(#version[^\n]*\n)/, '$1' + inj); else src = inj + src; return o.call(this, sh, src); }; }
};
const SEED = () => { let s = 12345; Math.random = () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; };
(async () => {
  fs.mkdirSync(out + '/plain', { recursive: true }); fs.mkdirSync(out + '/hook', { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  let ids = null;
  for (const mode of ['plain', 'hook']) {
    const p = await b.newPage({ viewport: { width: +W, height: +H } });
    await p.addInitScript(() => { localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_land3', 'true'); });
    await p.addInitScript(SEED); if (mode === 'hook') await p.addInitScript(HOOK);
    const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200))); p.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 300)); });
    await p.goto(`http://127.0.0.1:8771/nightshift/index.html?gfx=${gfx}&lang=en`);
    await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 1200000 });
    if (!ids) { ids = await p.evaluate(([filt, step]) => { const s = new Set(); for (const sc of Object.values(window.STORY.scenes)) for (const bt of sc.beats || []) { if (typeof bt.img === 'string') s.add(bt.img); for (const c of bt.cams || []) if (typeof c[4] === 'string') s.add(c[4]); }
      return [...s].filter(x => new RegExp(filt).test(x)).filter((x, i) => i % step === 0); }, [filt, +step]); console.log('shots', ids.length); }
    const t0 = Date.now();
    for (const id of ids) {
      const img = await p.evaluate(([id, W, H]) => { const c = document.createElement('canvas'); c.width = W; c.height = H; const g = c.getContext('2d');
        try { for (let i = 0; i < 3; i++) World.draw(g, W, H, id, { t: 1.2 + i * .05, T: 1.2 + i * .05, settle: i === 0, z: 1 }); } catch (e) { return 'ERR ' + e; }
        return c.toDataURL('image/png'); }, [id, +W, +H]);
      if (img.startsWith('ERR')) { console.log(mode, id, img); continue; }
      fs.writeFileSync(`${out}/${mode}/${id}.png`, Buffer.from(img.split(',')[1], 'base64'));
    }
    console.log(mode, 'done', ((Date.now() - t0) / 1000) | 0, 's', 'errors', errs.length, errs.slice(0, 5));
    await p.close();
  }
  fs.writeFileSync(out + '/ids.json', JSON.stringify(ids));
  await b.close();
})();
