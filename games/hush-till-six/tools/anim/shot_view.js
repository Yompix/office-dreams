// один кадр игры напрямую (без сюжета): node shot_view.js OUT.png shot [retro|cine] [L|P] [t]
//   CAM="x,y,z;x,y,z;fov" — свой ракурс того же кадра (камера; куда смотрит; угол), PROPS='{"cig":1}' — реквизит кадра
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const [out, shot, gfx = 'retro', or = 'L', t0 = '2'] = process.argv.slice(2), land = or === 'L', W = land ? 1280 : 540, H = land ? 720 : 960;
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route(/\/web\/_sv\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/web/_sv.html?gfx=' + gfx + '&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async ([land]) => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(land); }, [land]);
  let name = shot; if (process.env.CAM) { const [a, b, f] = process.env.CAM.split(';'); name = '_cam'; await p.evaluate(([shot, a, b, f]) => World.dbgShot('_cam', shot, { pos: a.split(',').map(Number), look: b.split(',').map(Number), fov: +f || 40, cctv: 0 }), [shot, a, b, f]); }
  const props = JSON.parse(process.env.PROPS || '{}');
  await p.evaluate(([shot, W, H, t0, props]) => { const g = document.getElementById('c').getContext('2d'); for (let t = 0; t <= +t0; t += 1 / 15) World.draw(g, W, H, shot, { t, T: 100 + t, settle: t === 0, props }); }, [name, W, H, t0, props]);
  await p.waitForTimeout(1500); await p.evaluate(([shot, W, H, t0, props]) => { const g = document.getElementById('c').getContext('2d'); World.draw(g, W, H, shot, { t: +t0, T: 100 + +t0, props }); }, [name, W, H, t0, props]);
  await p.screenshot({ path: out }); await br.close(); console.log('готово', out);
})();
