// Прогон сценария в настоящей игре (index.html): каждая сцена запускается, кадры пролистываются, ловятся ошибки страницы.
//   cd public/games && python3 -m http.server 8771 --bind 127.0.0.1   (в другом окне)
//   node tools/story/play_test.js [сцена1,сцена2,…]   [SHOTS=dir — снимок первого кадра каждой сцены]
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const only = process.argv[2] ? process.argv[2].split(',') : null, shots = process.env.SHOTS;
  if (shots) fs.mkdirSync(shots, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: { width: 540, height: 960 } });
  let errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300))); p.on('console', m => m.type() === 'error' && errs.push(m.text().slice(0, 300)));
  await p.goto('http://127.0.0.1:8771/nightshift/index.html?gfx=' + (process.env.GFX || 'cine'));   // GFX=retro — быстрее в headless
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
  const ids = await p.evaluate(() => Object.keys(window.STORY.scenes));
  const res = [];
  for (const id of ids) {
    if (only && !only.includes(id)) continue;
    errs = [];
    await p.evaluate(id => window.__ns.startScene(id), id);
    let end = 'timeout', n = 0;
    for (let i = 0; i < 160; i++) {
      await p.waitForTimeout(250);
      const st = await p.evaluate(() => ({ m: window.__ns.mode, s: window.__ns.scene }));
      if (shots && i === 2) await p.screenshot({ path: `${shots}/${id}.png`, timeout: 120000 }).catch(e => console.log('снимок не вышел:', id, String(e).slice(0, 80)));
      if (st.s !== id) { end = '→ ' + st.s; break; }
      if (st.m !== 'play') { end = st.m; break; }
      await p.evaluate(() => window.__ns.skipBeat()); n++;
    }
    const miss = await p.evaluate(() => window.__ns.voiceMiss); if (miss) { errs.push(`реплики без буфера: ${miss}`); await p.evaluate(() => { window.__ns.voiceMiss = 0; }); }
    res.push([id, end, n, errs.slice(0, 3)]);
    console.log(`${id.padEnd(14)} ${end.padEnd(16)} кадров ${String(n).padStart(2)} буферов ${await p.evaluate(() => window.__ns.voiceN)} ${errs.length ? 'ОШИБКИ: ' + errs.slice(0, 2).join(' | ') : ''}`);
  }
  const bad = res.filter(r => r[3].length || r[1] === 'timeout');
  console.log(bad.length ? `с ошибками: ${bad.length}` : 'ошибок нет');
  await b.close(); process.exit(bad.length ? 1 : 0);
})();
