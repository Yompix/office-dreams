// Проверка часов S5/S6: «держи Фрэнка» (успех и провал), «Лили» у входа (открыть — смерть), рация голосом Гаса.
//   node tools/story/s56_test.js ПАПКА
const { chromium } = require('/opt/node-tools/node_modules/playwright');
(async () => {
  const out = process.argv[2] || '/tmp/s56'; require('fs').mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 }, locale: 'ru-RU' });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.goto('http://127.0.0.1:' + (process.env.PORT || 8771) + '/nightshift/index.html?gfx=retro');
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
  const start = async seg => { await p.evaluate(s => window.__ns.startScene('sh_' + s), seg); await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 30000 }); await p.evaluate(() => window.__ns.shSpeed(0)); };
  const ev = e => p.evaluate(e => { const f = window.__ns.shf; f.ev.push(Object.assign({ m: f.M - .01 }, e)); }, e);
  const st = () => p.evaluate(() => ({ mode: window.__ns.mode, scene: window.__ns.scene }));
  // 1) держать: не нажал — Фрэнк уходит
  // время игры идёт по кадрам (не больше 0,1 с за кадр): в медленном браузере ждём по условию, а не по часам
  await start('S6'); await ev({ hold: { say: 'n37', dur: 3, die: 'over_k8' } }); await p.waitForSelector('#hBtn', { timeout: 60000 }); await p.screenshot({ path: out + '/hold.png' });
  await p.waitForFunction(() => window.__ns.mode !== 'shift', null, { timeout: 120000 }).catch(() => {}); console.log('держать, не нажал →', JSON.stringify(await st()));
  // 2) держать: нажал и держит — выжил
  await start('S6'); await ev({ hold: { say: 'n37', dur: 3, die: 'over_k8' } }); await p.waitForSelector('#hBtn', { timeout: 60000 });
  await p.evaluate(() => { const bt = document.getElementById('hBtn'); bt.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); });
  await p.waitForFunction(() => document.getElementById('shPop').classList.contains('hide') || window.__ns.mode !== 'shift', null, { timeout: 180000 }).catch(() => {});
  console.log('держать, держит →', JSON.stringify(await st()), 'окно открыто:', await p.evaluate(() => !document.getElementById('shPop').classList.contains('hide')));
  // 3) «Лили» у входа, открыть вход — смерть
  await start('S5'); await p.evaluate(() => { window.__ns.shk.door = 1; }); await ev({ an: 'lilyDoor' }); await p.evaluate(() => window.__ns.shiftCam('01'));
  await p.waitForTimeout(2500); await p.screenshot({ path: out + '/lily.png' });
  await p.evaluate(() => window.__ns.shiftPanel()); await p.waitForSelector('#pDoor'); await p.click('#pDoor');
  await p.waitForFunction(() => window.__ns.mode !== 'shift', null, { timeout: 30000 }).catch(() => {});
  console.log('открыл вход при ней →', JSON.stringify(await st()));
  // 4) рация голосом Гаса: ответить нельзя, выключить — дальше
  await start('S5'); await ev({ call: { title: 'Рация. Канал уборщика', sub: 'Сквозь шипение — голос Гаса.', take: 'Ответить', no: 'Не отвечать', radio: 1, say: 'n26', ask: '«Итан… Спустись ко мне»', yes: '— Гас! Держись, я сейчас поднимусь!', hang: 'Выключить рацию', die: 'over_k3', after: 'n27' } });
  await p.waitForSelector('#cTake', { timeout: 60000 }); await p.screenshot({ path: out + '/radio.png' }); await p.click('#cTake');
  await p.waitForSelector('#cHang', { timeout: 180000 }); await p.screenshot({ path: out + '/radio2.png' });
  await p.click('#cHang'); await p.waitForTimeout(800); console.log('рация, выключил →', JSON.stringify(await st()));
  console.log(errs.length ? 'ОШИБКИ:\n' + errs.join('\n') : 'ошибок нет'); await b.close();
})();
