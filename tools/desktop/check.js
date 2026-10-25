// Автопроверка ПК-версии: xvfb-run -a node tools/desktop/check.js ПАПКА_LINUX_ТЕСТ ПАПКА_СНИМКОВ
// (сборка: node tools/desktop/build.js linux --test --out …; Windows-сборка — те же файлы игры и та же обёртка, только .exe)
// Проверяет: окно открывается, меню, «Выйти из игры» есть, доната нет, звук идёт, сцена играет без ошибок,
// сохранения переживают перезапуск, «Выйти из игры» закрывает программу.
const path = require('path'), fs = require('fs');
const { _electron } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [dir, out] = process.argv.slice(2); fs.mkdirSync(out, { recursive: true });
const exe = path.join(dir, 'hush-till-six'), flags = ['--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'];   // в контейнере: root и нет видеокарты
const fail = m => { console.log('✗', m); process.exitCode = 1; }, ok = m => console.log('✓', m);
async function open(errs) {
  const app = await _electron.launch({ executablePath: exe, args: flags, env: Object.assign({}, process.env, { NS_WINDOWED: '1' }), timeout: 120000 });
  const p = await app.firstWindow();
  p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  p.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text().slice(0, 200)); });
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  return { app, p };
}
(async () => {
  const errs = [];
  let { app, p } = await open(errs);
  ok('окно и меню: ' + await p.title() + ', адрес ' + p.url());
  const st = await p.evaluate(() => ({ desk: !!window.nsDesktop, quit: !!document.getElementById('mQuit'), sup: !!document.querySelector('.msup'), lang: window.NS_LANG }));
  st.desk ? ok('мостик nsDesktop есть') : fail('нет window.nsDesktop');
  st.quit ? ok('кнопка «Выйти из игры» есть') : fail('нет кнопки «Выйти из игры»');
  !st.sup ? ok('доната в меню нет') : fail('в меню кнопка доната');
  await p.keyboard.press('Enter'); await p.waitForTimeout(1500);
  await p.screenshot({ path: out + '/1menu.png' });
  await p.evaluate(() => { localStorage.setItem('ns1_desk_probe', JSON.stringify(42)); });
  await p.evaluate(() => window.__ns.startScene('intro')); await p.waitForTimeout(4000);
  const ac = await p.evaluate(() => window.__ns.audio);
  ac === 'running' ? ok('звук идёт') : fail('звук: ' + ac);
  for (let i = 0; i < 3; i++) { await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(1500); }
  await p.screenshot({ path: out + '/2intro.png' });
  await app.close();
  ({ app, p } = await open(errs));
  const v = await p.evaluate(() => localStorage.getItem('ns1_desk_probe'));
  v === '42' ? ok('сохранения пережили перезапуск') : fail('сохранения пропали после перезапуска: ' + v);
  await p.keyboard.press('Enter'); await p.waitForTimeout(1200);
  const closed = new Promise(r => app.on('close', () => r(true)));
  await p.click('#mQuit').catch(() => {});
  (await Promise.race([closed, new Promise(r => setTimeout(() => r(false), 15000))])) ? ok('«Выйти из игры» закрыло программу') : (fail('программа не закрылась'), await app.close());
  const real = errs.filter(e => !/favicon/.test(e));
  real.length ? fail('ошибки на странице:\n  ' + real.join('\n  ')) : ok('ошибок на странице нет');
})().catch(e => { fail(e.stack || e); process.exit(1); });
