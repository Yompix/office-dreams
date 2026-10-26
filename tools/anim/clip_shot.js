// Один кадр так же, как его видит clip_check.js (сцена, бит, секунда): снимок экрана, проникновения (World.clips) и что толкает каждого (World.solidWhy).
//   PORT=8783 node tools/anim/clip_shot.js сцена:бит:секунда[,…] ПАПКА [P|L]      (SOLID=0 — без твёрдого мира)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const [list, out, ori = 'P'] = process.argv.slice(2), land = /^l/i.test(ori), port = process.env.PORT || 8771; fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: land ? { width: 640, height: 360 } : { width: 360, height: 640 } });
  await p.addInitScript(([l, sol]) => { localStorage.setItem('ns1_land3', l ? 'true' : 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); window.__SOLID = sol; }, [land, +(process.env.SOLID ?? 1)]);
  await p.goto(`http://127.0.0.1:${port}/nightshift/index.html?gfx=retro&lang=ru&cs=.4`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
  for (const it of list.split(',')) {
    const [id, bi, t] = it.split(':');
    await p.evaluate(([id, bi]) => { const b = window.STORY.scenes[id].beats[bi], fl = {}; for (const k of [].concat(b.if || [])) { if (k[0] === '!') fl[k.slice(1)] = 0; else fl[k] = 1; } window.__ns.setFlags(fl); window.__ns.startScene(id, bi); }, [id, +bi]);
    await p.waitForFunction(() => window.__ns.mode !== 'play' || window.__ns.bdur > 0, null, { timeout: 30000 }).catch(() => {});
    await p.evaluate(t => window.__ns.seek(t), +t);
    await p.waitForFunction(() => window.__ns.held, null, { polling: 30, timeout: 120000 });
    const r = await p.evaluate(() => ({ img: window.__ns.beatImg, cl: window.World.clips().filter(x => x.n >= 4 && x.d >= .025).slice(0, 5).map(x => `${x.k} в «${x.prop}» ${x.size.join('×')} ${Math.round(x.d * 100)} см (${x.n} верш.) в точке ${x.at.join(', ')}`),
      why: Object.keys(window.World.actors).filter(k => window.World.actors[k].root.visible && window.World.actors[k].root.parent).map(k => k + ': ' + JSON.stringify(window.World.solidWhy(k).slice(0, 2))) }));
    await p.screenshot({ path: `${out}/${id}_${bi}_${t}.png` });
    console.log(`${it} (кадр ${r.img})\n  проникновения: ${r.cl.join(' | ') || 'нет'}\n  толкает: ${r.why.join('\n           ')}`);
  }
  await b.close();
})();
