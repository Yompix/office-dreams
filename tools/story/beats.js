// Кадр игры точно на секунде — по коду из отчёта 🐞 (режим проверки ?qa=1) или списком:
//   node tools/story/beats.js ПАПКА P|L cine|retro ru|en "сцена:кадр:секунда[,секунда…];сцена:кадр:…" ['флаги JSON']
//   пример: node tools/story/beats.js /tmp/b P cine ru "fb_photo:0:1.5,6.2" '{"photo":1}'
// Кадр ставится перемоткой (__ns.seek, игра на паузе) — не ждём настоящего времени; «Кино» по умолчанию в 60 % разрешения (RS=1 — полное).
// Сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1 (PORT= — другой порт)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const [out, o = 'P', gfx = 'cine', lang = 'ru', spec, fl] = process.argv.slice(2);
  if (!out || !spec) { console.log('node tools/story/beats.js ПАПКА P|L cine|retro ru|en "сцена:кадр:секунда,…;…" [\'флаги JSON\']'); process.exit(2); }
  const land = /^(l|1|land)$/i.test(o), rs = process.env.RS || (gfx === 'cine' ? '.6' : ''), port = process.env.PORT || 8771;
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: land ? { width: 960, height: 540 } : { width: 432, height: 768 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(l => { localStorage.setItem('ns1_land3', l ? 'true' : 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); }, land);
  await p.goto(`http://127.0.0.1:${port}/nightshift/index.html?gfx=${gfx}&lang=${lang}${rs ? '&rs=' + rs : ''}`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  const flags = JSON.parse(fl || '{}');
  for (const part of spec.split(';').filter(Boolean)) {
    const [sc, bi = '0', ts = '1'] = part.split(':');
    await p.evaluate(([s, bb, f]) => { window.__ns.setFlags(f); window.__ns.startScene(s, +bb); }, [sc, bi, flags]);
    await p.waitForFunction(() => window.__ns.mode !== 'play' || window.__ns.bdur > 0, null, { timeout: 30000 }).catch(() => {});
    const st = await p.evaluate(() => [window.__ns.mode, window.__ns.bi, window.__ns.bdur]);
    if (st[0] !== 'play') { console.log(`${part}: сцена не в режиме кадра (${st[0]})`); continue; }
    if (st[1] !== +bi) console.log(`${part}: кадр ${bi} не показан при этих флагах — снимаю кадр ${st[1]}`);
    for (const t of ts.split(',').map(Number)) {
      await p.evaluate(t => window.__ns.seek(t), Math.min(t, st[2] - .05));
      await p.waitForFunction(() => window.__ns.held, null, { polling: 30, timeout: 180000 });   // кадр дорисован и держится
      const f = path.join(out, `${sc}_${st[1]}_${t}${land ? 'L' : 'P'}.png`); await p.screenshot({ path: f, timeout: 180000 }); console.log(f);
    }
  }
  console.log(errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
