// Финалы концовок и журнал ночи: для каждой концовки — последний кадр с названием, потом страница журнала; «Вернуться к выбору» ведёт к выбору.
//   node tools/story/end_test.js ПАПКА [концовка1,концовка2]   (dawn, newentry, inside, sister; сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1)
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const SC = { dawn: 'dawn', newentry: 'morning', inside: 'hide2', sister: 'run2' };
(async () => {
  const out = process.argv[2] || '/tmp/end'; require('fs').mkdirSync(out, { recursive: true });
  const only = (process.argv[3] || 'dawn,newentry,inside,sister').split(',');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const errs = [];
  for (const [land, vp] of [[1, { width: 960, height: 540 }], [0, { width: 432, height: 768 }]]) {
    const p = await b.newPage({ viewport: vp, locale: 'ru-RU' }); p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
    await p.addInitScript(l => { if (!sessionStorage.getItem('init')) { sessionStorage.setItem('init', 1); localStorage.setItem('ns1_land3', l ? 'true' : 'false');
      localStorage.setItem('ns1_run', JSON.stringify({ picks: [{ sc: 'keys_ask', q: 'Сменщик протягивает связку. Рука дрожит.', a: 'Взять ключи' }, { sc: 'radio_off', q: '«Итан, открой!»', a: 'Отойти от двери' }, { sc: 'server', q: 'Фрэнк жив. Он внизу.', a: 'Спуститься к нему' }], sec: 6420, rec: 31, ok: 26, miss: 3, hrs: ['S1', 'S2', 'S3', 'S4', 'S6'] })); } }, land);
    await p.goto('http://127.0.0.1:' + (process.env.PORT || 8771) + '/nightshift/index.html?gfx=retro');
    await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
    for (const e of (land ? only : only.slice(0, 1))) {
      await p.evaluate(s => window.__ns.startScene(s), SC[e]);
      for (let i = 0; i < 80; i++) { const st = await p.evaluate(() => [window.__ns.mode, window.__ns.fin]); if (st[0] !== 'play' || st[1]) break; await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(250); }
      const fin = await p.evaluate(() => window.__ns.fin);
      await p.waitForTimeout(3500); await p.screenshot({ path: `${out}/${e}_${land ? 'L' : 'P'}_fin.png` });
      await p.waitForFunction(() => window.__ns.mode === 'end', null, { timeout: 180000 });   // в headless ~1 кадр/с: финал (8,5 с игрового времени) идёт минуту и больше
      await p.waitForTimeout(6000); await p.screenshot({ path: `${out}/${e}_${land ? 'L' : 'P'}_log.png` });
      const back = await p.evaluate(() => { const b = document.getElementById('eBack'); return b ? b.textContent : null; });
      console.log(e, land ? 'гориз.' : 'верт.', '| финал:', fin, '| журнал: да | к выбору:', back);
    }
    // «Вернуться к выбору» → тот же выбор (последний кадр сцены и выбор)
    await p.click('#eBack'); await p.waitForTimeout(4000); await p.evaluate(() => window.__ns.skipBeat());   // последний кадр ждёт тапа
    await p.waitForFunction(() => window.__ns.mode === 'choice', null, { timeout: 90000 }).catch(() => {});
    console.log('вернулся к выбору →', await p.evaluate(() => [window.__ns.mode, window.__ns.scene, document.querySelector('#choice h2') && document.querySelector('#choice h2').textContent].join(' | ')));
    await p.screenshot({ path: `${out}/back_${land ? 'L' : 'P'}.png` }); await p.close();
  }
  console.log(errs.length ? 'ОШИБКИ:\n' + errs.join('\n') : 'ошибок нет'); await b.close();
})();
