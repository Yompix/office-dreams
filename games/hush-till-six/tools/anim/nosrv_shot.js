// Кадры игры без локального сервера (файлы web/ отдаются с диска перехватом запросов в playwright).
//   node tools/anim/nosrv_shot.js OUTDIR кадр@секунда [кадр@секунда …]
//   GFX=retro|cine (по умолчанию cine), W=960 H=540, PROPS='{"clock":"03:31","sh":{…}}' — реквизит/состояние кадра,
//   CAM="x,y,z;x,y,z;fov" — свой ракурс (камера; куда смотрит; угол) для того же кадра (снять руки сбоку/снизу).
// Пишет OUTDIR/<кадр>_<сек>.png. Кадр прогревается с 0 до нужной секунды (15 к/с), как в игре.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const ROOT = path.resolve(__dirname, '../..'), [OUT, ...SHOTS] = process.argv.slice(2), W = +(process.env.W || 960), H = +(process.env.H || 540), GFX = process.env.GFX || 'cine';
const MIME = { '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.wasm': 'application/wasm', '.mp3': 'audio/mpeg', '.ktx2': 'image/ktx2' };
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } }); p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route('http://127.0.0.1:8771/**', r => { const u = new URL(r.request().url()); if (u.pathname === '/web/_g.html') return r.fulfill({ contentType: 'text/html', body: HTML });
    const f = path.join(ROOT, decodeURIComponent(u.pathname)); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return r.fulfill({ status: 404, body: '' });
    r.fulfill({ body: fs.readFileSync(f), contentType: MIME[path.extname(f)] || 'application/octet-stream' }); });
  await p.goto('http://127.0.0.1:8771/web/_g.html?gfx=' + GFX + '&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); });
  const props = JSON.parse(process.env.PROPS || '{}'), cam = process.env.CAM;
  for (const sp of SHOTS) { const [shot, tt = '1'] = sp.split('@');
    const err = await p.evaluate(([shot, tt, W, H, props, cam]) => { try { let name = shot;
      if (cam) { const [a, b, f] = cam.split(';'); World.dbgShot('_cam', shot, { pos: a.split(',').map(Number), look: b.split(',').map(Number), fov: +f || 40 }); name = '_cam'; }
      const g = document.getElementById('c').getContext('2d'); for (let t = 0; t <= +tt; t += 1 / 15) World.draw(g, W, H, name, { t, T: 100 + t, settle: t === 0, props });
      World.draw(g, W, H, name, { t: +tt, T: 100 + +tt, props }); return ''; } catch (e) { return String(e); } }, [shot, tt, W, H, props, cam]);
    await p.waitForTimeout(400); const f = path.join(OUT, shot + '_' + tt + '.png'); await p.screenshot({ path: f, timeout: 240000 }); console.log(err ? 'ошибка ' + shot + ': ' + err : 'ok ' + f); }
  await br.close();
})();
