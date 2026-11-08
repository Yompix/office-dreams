// Замер загрузки Hush Till Six «как на слабой ТВ-приставке»: процессор замедлен в RATE раз (CDP), сеть — локальная.
// Печатает время каждого шага (window.__ldTimes) и до меню. Сервер: cd public/games && python3 -m http.server 8771. node tools/tv/load_prof.js [RATE]
const { chromium } = require('playwright');
const RATE = +(process.argv[2] || 6);
(async () => {
  const b = await chromium.launch(); const c = await b.newContext({ viewport: { width: 1280, height: 720 }, userAgent: 'Mozilla/5.0 (Linux; Android 9; MiTV Build/PI; wv) AppleWebKit/537.36 Chrome/79.0 Safari/537.36' });
  const p = await c.newPage(); const cdp = await c.newCDPSession(p); await cdp.send('Emulation.setCPUThrottlingRate', { rate: RATE });
  const t0 = Date.now(); await p.goto('http://127.0.0.1:' + (process.env.PORT || 8771) + '/nightshift/index.html?lang=ru' + (process.env.Q || ''));
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
  const menu = Date.now() - t0, tm = await p.evaluate(() => window.__ldTimes);
  let prev = 0; for (const [k, ms] of tm) { console.log(String(ms).padStart(7) + ' мс  +' + String(ms - prev).padStart(6) + '  ' + k); prev = ms; }
  console.log('меню через ' + (menu / 1000).toFixed(1) + ' с (процессор ×' + RATE + ')');
  await b.close();
})();
