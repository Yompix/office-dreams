// usage: node skinshot.js <outdir> [extra.css] [lang]
// CLASSIC=1 node skinshot.js <outdir> → the same screens with art mode off (the classic look must not change)
// screenshots every main screen in art mode, optionally injecting a CSS file (a candidate skin) into the page
const fs = require('fs');
const { chromium, open, rich, ev } = require('./common.js');
(async () => {
  const out = process.argv[2] || '.', cssFile = process.argv[3], lang = process.argv[4] || 'ru';
  fs.mkdirSync(out, { recursive: true });
  const css = cssFile && fs.existsSync(cssFile) ? fs.readFileSync(cssFile, 'utf8') : '';
  const b = await chromium.launch();
  for (const [vw, vh, tag] of [[400, 860, ''], [360, 640, '_small']]) {
    const cx = await b.newContext({ viewport: { width: vw, height: vh }, deviceScaleFactor: 2 });
    await cx.addInitScript((classic) => { localStorage.setItem('od2_art2', classic ? '' : '1'); for (const t of ['office', 'secrets', 'pvp', 'studio', 'city', 'reno', 'gw']) localStorage.setItem('od2_tip_' + t, '1'); }, !!process.env.CLASSIC);
    const p = await open(cx, lang);
    if (css) await p.addStyleTag({ content: css });
    await rich(p); await ev(p, 'closeModal(true)');
    const shots = [['office', "go('office')"], ['studio', "go('studio')"], ['quests', "go('quests')"], ['empire', "go('empire')"], ['drop', "go('drop')"]];
    for (const [n, code] of shots) {
      await ev(p, code + '; window.scrollTo(0,0); document.querySelector("#main") && document.querySelector("#main").scrollTo(0,0)'); await p.waitForTimeout(1300);
      await p.screenshot({ path: `${out}/${n}${tag}.png` });
      if (!tag) await p.screenshot({ path: `${out}/${n}_full.png`, fullPage: true });
    }
    if (!tag) {
      await ev(p, "go('office')"); await p.waitForTimeout(600);
      for (const [n, i] of [['side1', 1], ['side2', 2], ['side4', 4], ['side5', 5]]) {
        try { await ev(p, "closeModal(true); go('office')"); await p.waitForTimeout(500); await p.click(`.side .sbtn:nth-child(${i})`); await p.waitForTimeout(1000); await p.screenshot({ path: `${out}/${n}.png` }); } catch (e) { console.log('skip', n, e.message.slice(0, 80)); }
      }
      await ev(p, "closeModal(true); go('office')"); await p.waitForTimeout(400);
      await ev(p, 'pvOpen()'); await p.waitForTimeout(1200); await p.screenshot({ path: `${out}/pvp.png` }); await ev(p, 'pvClose(true)');
    }
    await cx.close();
  }
  await b.close();
  console.log('ok', out);
})();
