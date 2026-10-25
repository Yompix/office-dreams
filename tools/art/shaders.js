// Сколько шейдерных программ собирает «Кино» и как долго (регрессия: не множить программы — LOOK.md, раздел 2).
//   PORT=8784 node tools/art/shaders.js [cine|retro] [ALL=1 — ещё и все кадры]
// Печатает: время World.init() и World.warm(), число программ, конфигурации света (точечные/с тенью + прожекторы/с маской + полусфера),
// самый длинный фрагментный шейдер, самые «тяжёлые» по активным текстурам (предел слабых видеокарт — 16) и векторам uniform.
// Нужен сервер статики: cd public/games && python3 -m http.server 8784 --bind 127.0.0.1. Время — относительное (программная отрисовка).
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const gfx = process.argv[2] || 'cine', PORT = process.env.PORT || 8771;
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 640, height: 360 } });
  const errs = []; p.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 300)); }); p.on('pageerror', e => errs.push(String(e)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script></body></html>`;
  await p.route(/\/nightshift\/_sh\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${PORT}/nightshift/_sh.html?gfx=${gfx}&rs=0.3`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 90000 });
  const r = await p.evaluate(async all => {
    await window.__W3chars.load();
    const t0 = performance.now(); World.init(); const t1 = performance.now(); World.warm(); const t2 = performance.now();
    const R = window.__W3core.R, gl = R.getContext(), nWarm = R.info.programs.length;
    if (all) { const c = document.createElement('canvas'); c.width = 64; c.height = 36; const g = c.getContext('2d'); for (const id of Object.keys(World.shots)) World.draw(g, 64, 36, id, { t: 1, T: 1 }); }
    const t3 = performance.now(), cfg = {}, heavy = [];
    const ST = new Set([gl.SAMPLER_2D, gl.SAMPLER_CUBE, gl.SAMPLER_2D_SHADOW, gl.SAMPLER_3D, gl.SAMPLER_2D_ARRAY]), VEC = { [gl.FLOAT_MAT3]: 3, [gl.FLOAT_MAT4]: 4, [gl.FLOAT_MAT2]: 2 };
    let maxFs = 0;
    for (const pr of R.info.programs) {
      let f = ''; for (const s of gl.getAttachedShaders(pr.program)) if (gl.getShaderParameter(s, gl.SHADER_TYPE) === gl.FRAGMENT_SHADER) f = gl.getShaderSource(s);
      const g = re => (f.match(re) || [])[1] || 0, k = `${g(/PointLight pointLights\[\s*(\d+)/)}/${g(/pointShadowMap\[\s*(\d+)/)}+${g(/SpotLight spotLights\[\s*(\d+)/)}/${g(/spotLightMap\[\s*(\d+)/)}h${g(/HemisphereLight hemisphereLights\[\s*(\d+)/)}`;
      cfg[k] = (cfg[k] || 0) + 1; maxFs = Math.max(maxFs, f.length);
      let samp = 0, vec = 0; const n = gl.getProgramParameter(pr.program, gl.ACTIVE_UNIFORMS);
      for (let i = 0; i < n; i++) { const u = gl.getActiveUniform(pr.program, i); if (ST.has(u.type)) samp += u.size; else vec += (VEC[u.type] || 1) * u.size; }
      heavy.push([samp, vec]);
    }
    heavy.sort((a, b) => b[0] - a[0] || b[1] - a[1]);
    return { initMs: t1 - t0 | 0, warmMs: t2 - t1 | 0, allMs: all ? t3 - t2 | 0 : undefined, progWarm: nWarm, progAll: R.info.programs.length, cfg, maxFs, maxSamplers: heavy[0][0], maxUniformVec: Math.max(...heavy.map(h => h[1])), units: gl.getParameter(gl.MAX_TEXTURE_IMAGE_UNITS) };
  }, !!process.env.ALL);
  console.log(gfx, JSON.stringify(r, null, 1));
  if (errs.length) console.log('ОШИБКИ:', errs.slice(0, 5));
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
