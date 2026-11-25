// Стенд Жильца для критики модели: кадр игры + ровный свет, снимки — во весь рост, лицо, кисть крупно (спереди и сбоку).
//   GFX=cine node tools/anim/monster_view.js ПАПКА [кадр=lunge_booth] [секунда=0.7]
// Сервер не нужен. Пишет ПАПКА/body.png, face.png, hand_a.png, hand_b.png.
const { chromium } = require('/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const ROOT = path.resolve(__dirname, '../..'), [OUT, SHOT = 'lunge_booth', TT = '0.7'] = process.argv.slice(2), GFX = process.env.GFX || 'cine', W = 720, H = 720, t = +TT;
const MIME = { '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.wasm': 'application/wasm', '.ktx2': 'image/ktx2' };
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } }); p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route('http://127.0.0.1:8771/**', r => { const u = new URL(r.request().url()); if (u.pathname === '/web/_g.html') return r.fulfill({ contentType: 'text/html', body: HTML });
    const f = path.join(ROOT, decodeURIComponent(u.pathname)); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return r.fulfill({ status: 404, body: '' });
    r.fulfill({ body: fs.readFileSync(f), contentType: MIME[path.extname(f)] || 'application/octet-stream' }); });
  await p.goto('http://127.0.0.1:8771/web/_g.html?gfx=' + GFX + '&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); });
  const draw = (id) => p.evaluate(([id, t, W, H]) => { const g = document.getElementById('c').getContext('2d'); for (let s = 0; s <= 8; s++) World.draw(g, W, H, id, { t: Math.max(0, t - .5 + s / 16), T: 100 + t, settle: s === 0, props: {} }); }, [id, t, W, H]);
  await p.evaluate(h => { window.__HEMI = h; }, +(process.env.HEMI || .45)); await draw(SHOT);
  const J = await p.evaluate(() => { const T = window.__W3core.T, A = World.actors[window.__MON || 'monster'], v = new T.Vector3(), o = {}; if (!A.bone.Head) return { err: Object.keys(A.bone).slice(0, 80).join(' '), par: !!A.root.parent };
    for (const b of ['Head', 'hand_r', 'middle_03_r', 'pelvis', 'spine_03']) { const x = A.bone[b]; if (x) { x.getWorldPosition(v); o[b] = v.toArray(); } }
    const S = A.root.parent; if (S && !S.__dbgL) { S.__dbgL = new T.HemisphereLight(0xd8dde0, 0x302c28, +(window.__HEMI || .45)); S.add(S.__dbgL); const d = new T.DirectionalLight(0xfff4e8, +(window.__DIRL || .8)); d.position.set(o.Head[0] + 1.5, o.Head[1] + 1.5, o.Head[2] - 2); d.target.position.set(...o.Head); S.add(d, d.target); }   // мягкий свет + контровой холодный
    const C = World.shots[Object.keys(World.shots)[0]]; return o; });
  console.log(JSON.stringify(J)); if (J.err) process.exit(1);
  const cam = async (name, at, dir, dist, fov) => { const L = Math.hypot(...dir), pos = at.map((a, i) => a + dir[i] / L * dist);
    await p.evaluate(([id, pos, look, fov]) => World.dbgShot('_cam', id, { pos, look, fov, dof: 0 }), [SHOT, pos, at, fov]); await draw('_cam'); await p.waitForTimeout(200);
    await p.screenshot({ path: path.join(OUT, name + '.png'), timeout: 240000 }); console.log('ok', name); };
  const sh = J.spine_03 || J.pelvis, fwd = await p.evaluate(() => { const T = window.__W3core.T, q = new T.Quaternion(); World.actors.monster.root.getWorldQuaternion(q); return new T.Vector3(0, 0, 1).applyQuaternion(q).toArray(); });
  const side = [fwd[2], 0, -fwd[0]];
  await cam('body', [sh[0], sh[1] - .2, sh[2]], [fwd[0], .1, fwd[2]], 3.2, 40);
  await cam('face', J.Head, [fwd[0] + side[0] * .4, .05, fwd[2] + side[2] * .4], .9, 30);
  await cam('hand_a', J.middle_03_r || J.hand_r, [fwd[0], .25, fwd[2]], .45, 32);
  await cam('hand_b', J.middle_03_r || J.hand_r, [side[0], .3, side[2]], .45, 32);
  await br.close();
})();
