// Час A1 (ветка «открыл → спрятался в будке», 04:00–05:45): вход из сцены hide_wait, «НЕ ДЫШИ» (не нажал — смерть, повтор с начала часа; держит — выжил),
// конец часа ведёт в hide2 (концовка «Внутри»).   node tools/story/a1_test.js ПАПКА
const { chromium } = require('/opt/node-tools/node_modules/playwright');
(async () => {
  const out = process.argv[2] || '/tmp/a1'; require('fs').mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 }, locale: 'ru-RU' });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.addInitScript(() => localStorage.setItem('ns1_tutDone', '1'));
  await p.goto('http://127.0.0.1:8771/nightshift/index.html?gfx=retro');
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
  const st = () => p.evaluate(() => ({ mode: window.__ns.mode, scene: window.__ns.scene, seg: window.__ns.shf && window.__ns.shf.id }));
  const HOLD = await p.evaluate(() => window.STORY.shift.segs.A1.events.find(e => e.hold).hold);
  const ev = e => p.evaluate(e => { const f = window.__ns.shf; f.ev.push(Object.assign({ m: f.M - .01 }, e)); }, e);
  // 1) из «спрятался» — в час A1
  await p.evaluate(() => window.__ns.startScene('hide_wait')); await p.waitForTimeout(1500); await p.evaluate(() => window.__ns.skipBeat());
  await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 60000 }); await p.evaluate(() => window.__ns.shSpeed(0));
  console.log('вход →', JSON.stringify(await st())); await p.waitForTimeout(1500); await p.screenshot({ path: out + '/a1.png' });
  // 2) «НЕ ДЫШИ»: не нажал — смерть, «Попробовать снова» — с начала часа
  await ev({ hold: Object.assign({}, HOLD, { dur: 3 }) }); await p.waitForSelector('#hBtn', { timeout: 60000 }); await p.screenshot({ path: out + '/hold.png' });
  console.log('окно:', await p.evaluate(() => document.querySelector('#shPop h3').textContent + ' | ' + document.getElementById('hBtn').textContent));
  await p.waitForFunction(() => window.__ns.mode !== 'shift', null, { timeout: 120000 }).catch(() => {}); console.log('не нажал →', JSON.stringify(await st()));
  for (let i = 0; i < 40 && await p.evaluate(() => window.__ns.mode) === 'play'; i++) { await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(400); }   // кадры смерти ждут тапа
  await p.waitForSelector('#oRetry', { timeout: 120000 }); await p.screenshot({ path: out + '/over.png' }); await p.click('#oRetry');
  await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 60000 }).catch(() => {}); console.log('снова →', JSON.stringify(await st()));
  // 3) держит — выжил
  await p.evaluate(() => window.__ns.shSpeed(0)); await ev({ hold: Object.assign({}, HOLD, { dur: 3 }) }); await p.waitForSelector('#hBtn', { timeout: 60000 });
  await p.evaluate(() => document.getElementById('hBtn').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })));
  await p.waitForFunction(() => document.getElementById('shPop').classList.contains('hide') || window.__ns.mode !== 'shift', null, { timeout: 180000 }).catch(() => {});
  console.log('держит →', JSON.stringify(await st()), '|', await p.evaluate(() => (document.getElementById('shToast') || {}).textContent));
  // 4) конец часа — в hide2
  await p.evaluate(() => { const f = window.__ns.shf; f.ev = []; f.act = []; f.gm = f.m1 - f.m0 - .15; window.__ns.shSpeed(1); });
  await p.waitForFunction(() => window.__ns.mode !== 'shift', null, { timeout: 120000 }).catch(() => {}); console.log('конец часа →', JSON.stringify(await st()));
  console.log(errs.length ? 'ОШИБКИ:\n' + errs.join('\n') : 'ошибок нет'); await b.close();
})();
