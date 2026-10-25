// Живой слой в настоящей игре (не перемотка): играет кадр со звуком и пишет каждый кадр, что делают люди —
// говорит/слушает, моргание, отвёл глаза, кивок, повороты головы. В конце — сводка: морганий в минуту, сколько раз отвёл глаза,
// кивки говорящего и «угу» слушающего, и где кивки пришлись относительно начала слов (по timing.json).
//   PORT=8783 node tools/anim/life_probe.js "сцена:кадр[,сцена:кадр…]" [секунд на кадр, по умолчанию — весь кадр]
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [list = 'gs_long:1', secs] = process.argv.slice(2), port = process.env.PORT || 8771;
const ARGS = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'];
(async () => {
  const b = await chromium.launch({ args: ARGS }), p = await b.newPage({ viewport: { width: 270, height: 480 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e.message || e).slice(0, 300)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'false'); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_qa', 'false'); window.__CUTGAP = 3; });
  await p.goto(`http://127.0.0.1:${port}/nightshift/index.html?gfx=retro&lang=ru&cs=.4`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 600000 });
  await p.evaluate(() => {
    window.__life = []; let last = 0;
    const tick = () => { const A = window.World && window.World.actors, N = window.__ns;
      if (A && N && N.mode === 'play' && N.bdur) { const t = N.bt, row = { t, who: N.talkWho, c: {} };
        if (t !== last) { last = t;
          for (const k in A) { const C = A[k]; if (!C.root.parent || !C.root.visible || !C.bone || C.o.monster) continue;
            const W = C.W || {}, hl = C.hl || {}; row.c[k] = { talk: !!C.talkingNow, hear: !!C.hearingNow, blink: +(W.eyeBlinkLeft || 0).toFixed(2), av: +(Math.hypot(C.avY || 0, C.avP || 0)).toFixed(3),
              nod: hl.nT != null && hl.nT < hl.nD ? +hl.nA.toFixed(3) : 0, on: !!C.onset, ld: C.lookDir ? [C.lookDir.x, C.lookDir.y, C.lookDir.z].map(v => +v.toFixed(3)) : null }; }
          window.__life.push(row); } }
      requestAnimationFrame(tick); };
    requestAnimationFrame(tick); });
  for (const item of list.split(',')) {
    const [id, bi] = item.split(':');
    await p.evaluate(() => { window.__life.length = 0; });
    await p.evaluate(([id, bi]) => window.__ns.startScene(id, +bi), [id, bi]);
    await p.waitForFunction(() => window.__ns.mode === 'play' && window.__ns.bdur > 0, null, { timeout: 60000 });
    const dur = await p.evaluate(() => window.__ns.bdur), len = Math.min(+secs || dur, dur) + 1.5;
    await p.waitForTimeout(len * 1000);
    const rec = await p.evaluate(() => window.__life), tim = await p.evaluate(() => window.__ns.tim);
    const keys = await p.evaluate(([id, bi]) => (window.STORY.scenes[id].beats[+bi].say || []), [id, bi]);
    const T = rec.length ? rec[rec.length - 1].t - rec[0].t : 0, fps = T ? (rec.length / T).toFixed(1) : 0;
    const out = [`${item} — ${T.toFixed(1)} с записи, ${fps} к/с; реплики: ${keys.map(k => (tim[k] || {}).who || '?').join(', ')}`];
    for (const k of Object.keys(rec[rec.length - 1] ? rec[rec.length - 1].c : {})) {
      let blinks = 0, avs = 0, nods = 0, ons = 0, talkT = 0, hearT = 0, prevB = 0, prevA = 0, prevN = 0, prevT = null; const nodAt = [];
      for (const r of rec) { const c = r.c[k]; if (!c) continue; const dt = prevT == null ? 0 : r.t - prevT; prevT = r.t;
        if (c.talk) talkT += dt; if (c.hear) hearT += dt; if (c.blink > .5 && prevB <= .5) blinks++; if (c.av > .03 && prevA <= .03) avs++; if (c.nod && !prevN) { nods++; nodAt.push(+r.t.toFixed(2)); } if (c.on) ons++;
        prevB = c.blink; prevA = c.av; prevN = c.nod; }
      out.push(`  ${k}: говорит ${talkT.toFixed(1)} с, слушает ${hearT.toFixed(1)} с · морганий ${blinks} (${T ? (blinks / T * 60).toFixed(0) : 0}/мин) · отвёл глаза ${avs} · кивков ${nods}${nodAt.length ? ' на ' + nodAt.join(' ') : ''} · ударных слогов ${ons}`);
    }
    // начала слов в кадре (для сверки кивков говорящего): время реплики в кадре + время слова
    let t0 = .15; const onsets = []; for (const k of keys) { const L = tim[k]; if (!L) continue; if (L.w) for (const w of L.w) onsets.push(+(t0 + w[0]).toFixed(2)); t0 += (L.dur || 2) + .3; }
    out.push(`  начала слов: ${onsets.slice(0, 40).join(' ')}`);
    console.log(out.join('\n'));
  }
  if (errs.length) console.log('ОШИБКИ JS:', errs.slice(0, 5).join(' | '));
  await b.close();
})();
