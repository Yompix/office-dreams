// Сервер: cd public/games && python3 -m http.server 8784 --bind 127.0.0.1 (PORT=… — другой).
// финал смерти кадр за кадром (как в игре: 30 к/с между снимками): node gore_strip.js over_k4[,…] OUT [шаг] [W H] [gfx]
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const [ids, out = 'gs', st = '.3', Wd = '270', Hd = '480', gfx = 'cine'] = process.argv.slice(2), W = +Wd, H = +Hd;
const ENG = fs.readFileSync(require('path').join(__dirname, '../../public/games/nightshift/engine.js'), 'utf8');
const GK = eval('(' + /const GORE_KIND = (\{[\s\S]*?\});/.exec(ENG)[1] + ')'), GB = eval('(' + /const GORE_BASE = (\{[\s\S]*?\});/.exec(ENG)[1] + ')');
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text().slice(0, 300)); });
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script>
<canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route(/\/nightshift\/_gs\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8784}/nightshift/_gs.html?gfx=${gfx}&lang=en`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async (land) => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (land) World.setLand(true); }, W > H);
  fs.mkdirSync(out, { recursive: true });
  for (const id of ids.split(',')) {
    const gk = GK[id] || GK[id.replace(/_(srv|hide)$/, '')];
    const r = await p.evaluate(([id, gk, GBid, st]) => {
      const S = window.STORY.scenes[id], bt = (S.beats || []).find(b => b.black != null); if (!bt) return { err: 'нет кадра с black' };
      const sid = 'gore@' + (GBid || bt.img) + ':' + gk, TL = World.goreTL(sid), d = TL ? TL.d : 3.4;
      const c = document.getElementById('c'), g = c.getContext('2d'), res = [], props = Object.assign({ clock: '03:00', jacket: 1, badge: 1, book: 0 }, S.props || {}, bt.props || {});
      let first = true, t = 0;
      const shotAt = tt => sid + (!TL && gk !== 'drag' && tt > .55 && tt < 2.15 ? '|c' : '');
      const D = tt => World.draw(g, c.width, c.height, shotAt(tt), { t: tt, T: 100 + tt, cx: .5, cy: .5, z: TL ? 1 : 1 + .04 * tt, sx: 0, sy: 0, settle: first, cctv: 0, props });
      for (let tw = 0; tw <= d + 1e-6; tw += st) { while (t < tw - 1e-6) { D(t); first = false; t = Math.min(tw, t + 1 / 30); } D(tw); first = false; res.push([tw, c.toDataURL('image/jpeg', .85)]); }
      return { res, sid, tl: !!TL, d, fit: World.gfit };
    }, [id, gk, GB[id], +st]);
    if (r.err) { console.log(id, r.err); continue; }
    const dir = path.join(out, id); fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
    r.res.forEach(([tw, u], i) => fs.writeFileSync(path.join(dir, String(i).padStart(2, '0') + '_' + tw.toFixed(2) + '.jpg'), Buffer.from(u.split(',')[1], 'base64')));
    console.log(id, r.sid, r.tl ? 'сценарий' : 'старый', 'длина', r.d, 'снимков', r.res.length);
  }
  await br.close();
})();
