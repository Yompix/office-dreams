// Полосы кадров постановки (несколько моментов одной сцены на одном листе) без сервера и без правки игры — с голосом: для beat:"сцена:бит" подставляется,
// кто говорит (timing.json who), поэтому видны жесты «говорит руками» и реакция слушателя (в немых кадрах люди в разговоре стоят как манекены). NOTALK=1 — без голоса.
//   GFX=retro|cine node strip.js OUTDIR jobs.json
// jobs.json: [{ name, beat:"сцена:бит" | shot:"постановка", times:[…] | n:6, cam:"x,y,z;x,y,z;fov" | {at:["lily.hand_r",…], off:[dx,dy,dz], fov}, props:{…}, cols:3, w:512, h:288,
//              gus:{kinds:["open","in"], n:5, nth:0} — распорядок Гаса (cam07): по n кадров внутри пункта плана }]
// Пишет OUTDIR/<name>.jpg и OUTDIR/<name>.txt (кости актёров на последнем кадре — чтобы навести крупный план).
const { chromium } = require('/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const GAME = path.resolve(__dirname, '../..');
const ROOT = GAME, [OUT, JOBS] = process.argv.slice(2), GFX = process.env.GFX || 'retro', SEED = +(process.env.SEED || 7);
const MIME = { '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.wasm': 'application/wasm', '.mp3': 'audio/mpeg', '.ktx2': 'image/ktx2' };
const w = {}; new Function('window', fs.readFileSync(path.join(GAME, 'web/story.js'), 'utf8'))(w); const S = w.STORY;
const TM = JSON.parse(fs.readFileSync(path.join(GAME, 'web/timing.json'), 'utf8'));
function beatInfo(spec) { const [sc, bi] = spec.split(':'), scene = S.scenes[sc], b = scene.beats[+bi]; let d = 0; const sched = []; for (const s of b.say || []) { const e = TM[s] || {}; sched.push({ t0: d + .15, t1: d + .15 + (e.dur || 2), who: e.who || null }); d += (e.dur || 2) + .35; } d = Math.max(d + (b.pad || .3), 1.2);
  return { sched, shot: b.img, dur: d, props: Object.assign({ clock: scene.time && scene.time < '03:00' && scene.time > '12:00' ? scene.time : '03:00', jacket: 1, badge: 1, book: 1 }, scene.props, b.props), cams: b.cams }; }
(async () => {
  fs.mkdirSync(OUT, { recursive: true }); const jobs = JSON.parse(fs.readFileSync(JOBS, 'utf8'));
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 800, height: 450 } }); p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  await p.addInitScript(seed => { let s = seed >>> 0; const rnd = () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
    window.__reseed = v => { s = (v == null ? seed : v) >>> 0; }; Math.random = rnd; }, SEED);
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><canvas id="c" width="512" height="288"></canvas><canvas id="s"></canvas></body></html>`;
  await p.route('http://127.0.0.1:8771/**', r => { const u = new URL(r.request().url()); if (u.pathname === '/web/_g.html') return r.fulfill({ contentType: 'text/html', body: HTML });
    const f = path.join(ROOT, decodeURIComponent(u.pathname)); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return r.fulfill({ status: 404, body: '' });
    r.fulfill({ body: fs.readFileSync(f), contentType: MIME[path.extname(f)] || 'application/octet-stream' }); });
  await p.goto('http://127.0.0.1:8771/web/_g.html?gfx=' + GFX + '&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(([v, q]) => { window.FWARM = v; window.PREROLL = q; }, [process.env.FULLWARM === '1', +(process.env.PRE || 1.2)]);
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true);
    window.__bones = () => { const A = World.actors, out = {}, T = THREE, v = new T.Vector3(); for (const k in A) { const C = A[k]; if (!C.root || !C.root.parent || !C.root.visible || !C.bone) continue; let vis = true; for (let n = C.root; n; n = n.parent) if (!n.visible) vis = false; if (!vis) continue;
        C.root.updateMatrixWorld(true); const o = {}; for (const b of ['Head', 'hand_l', 'hand_r', 'pelvis', 'foot_l', 'foot_r']) if (C.bone[b]) { C.bone[b].getWorldPosition(v); o[b] = [+v.x.toFixed(2), +v.y.toFixed(2), +v.z.toFixed(2)]; } out[k] = o; } return out; };
    window.__tile = (i, cols, n, W, H, label) => { const s = document.getElementById('s'), c = document.getElementById('c'); if (i === 0) { s.width = W * Math.min(cols, n); s.height = H * Math.ceil(n / cols); }
      const g = s.getContext('2d'), x = (i % cols) * W, y = Math.floor(i / cols) * H; g.drawImage(c, 0, 0, W, H, x, y, W, H); g.font = 'bold 15px monospace'; g.fillStyle = 'rgba(0,0,0,.6)'; g.fillRect(x, y, g.measureText(label).width + 10, 20); g.fillStyle = '#ff0'; g.fillText(label, x + 5, y + 15); };
    window.__fullWarm = FWARM; window.__pre = PREROLL; window.__jpg = () => document.getElementById('s').toDataURL('image/jpeg', .88);
  });
  for (const J of jobs) { try {
    const bi = J.beat ? beatInfo(J.beat) : { shot: J.shot, dur: J.dur || 4, props: {} }; const shot = J.shot || bi.shot; if (!shot) { console.log('нет постановки:', J.name); continue; }
    const W = J.w || 512, H = J.h || 288, cols = J.cols || 3, props = Object.assign({}, bi.props, J.props);
    let frames;
    if (J.gus) { // распорядок Гаса: кадры внутри пунктов плана
      await p.evaluate(([W, H]) => { window.__reseed(); const c = document.getElementById('c'); c.width = W; c.height = H; const g = c.getContext('2d'); World.draw(g, W, H, 'cam07', { t: 0, T: 100, settle: true, props: { clock: '02:00', sh: { gus: 1, gz: -10 } } }); window.__reseed(); World.draw(g, W, H, 'cam07', { t: 5, T: 105, settle: true, props: { clock: '02:00', sh: { gus: 1, gz: 0 } } }); }, [W, H]);
      const plan = await p.evaluate(() => (window.__gusPlan || []).map(x => ({ k: x.k, t0: +x.t0.toFixed(2), d: +x.d.toFixed(2) })));
      fs.writeFileSync(path.join(OUT, 'gus_plan.json'), JSON.stringify(plan)); frames = [];
      if (J.gus.gz) for (const gz of J.gus.gz) { const it = plan.find(x => gz >= x.t0 && gz < x.t0 + x.d); frames.push({ gz, label: (it ? it.k : '?') + ' gz' + gz.toFixed(1) }); }
      else for (const k of J.gus.kinds) { const its = plan.filter(x => x.k === k), it = its[J.gus.nth || 0]; if (!it) { console.log('в плане нет', k); continue; } const n = J.gus.n || 5;
        for (let i = 0; i < n; i++) { const gz = it.t0 + it.d * (i + .5) / n; frames.push({ gz, label: k + ' +' + (gz - it.t0).toFixed(2) + '/' + it.d.toFixed(1) }); } }
    } else if (J.sweep) { frames = J.sweep.vals.map(v => ({ gz: v, label: (J.sweep.label || J.sweep.key) + ' ' + v })); 
    } else { const times = J.times || Array.from({ length: J.n || 6 }, (_, i) => +(bi.dur * (i + .5) / (J.n || 6)).toFixed(2)); frames = times.map(t => ({ t, label: (J.beat || shot) + ' @' + t })); }
    // свой ракурс: по костям (первый проход до первого кадра) или явный
    let cam = J.cam, name = shot;
    await p.evaluate(sc => { window.__sched = sc; window.__talkAt = t => { for (const x of window.__sched || []) if (x.who && t >= x.t0 && t < x.t1) return { who: x.who, v: .35 + .5 * Math.abs(Math.sin(t * 9.1)) * (Math.sin(t * 2.3) > -.7 ? 1 : .1) }; return null; }; }, process.env.NOTALK ? [] : (bi.sched || []));
    const info = await p.evaluate(async ([shot, frames, W, H, cols, props, cam, isGus, shotLabel, SWK, NPRE]) => { try {
      const c = document.getElementById('c'); c.width = W; c.height = H; const g = c.getContext('2d'); let name = shot; const log = [];
      const FULL = window.__fullWarm, PRE = window.__pre, drawTo = (name, t, t0) => { if (!FULL && (t0 === 0 ? t > PRE : t - t0 > PRE + .3)) { const a = Math.max(0, t - PRE); World.draw(g, W, H, name, { t: a, T: 100 + a, settle: true, props, talk: window.__talkAt(a) }); t0 = a + 1 / 15; } for (let x = t0; x < t - 1e-6; x += 1 / 15) World.draw(g, W, H, name, { t: x, T: 100 + x, settle: x === 0, props, talk: window.__talkAt(x) }); World.draw(g, W, H, name, { t, T: 100 + t, props, talk: window.__talkAt(t) }); };
      const gusDraw = (name, gz) => { window.__reseed(); for (let i = NPRE; i >= 0; i--) { const z = gz - i / 15; World.draw(g, W, H, name, { t: 5 + z, T: 105 + z, settle: i === NPRE, props: Object.assign({ clock: '02:00' }, props, { sh: Object.assign(SWK === 'gz' ? { gus: 1 } : {}, props.sh, { [SWK]: z }) }) }); } };
      if (cam && typeof cam === 'object') { if (isGus) gusDraw(shot, frames[0].gz); else drawTo(shot, frames[0].t, 0); const B = window.__bones(); const pts = cam.at.map(s => { if (Array.isArray(s)) return s; const [a, b] = s.split('.'); return B[a] && B[a][b]; }).filter(Boolean);
        if (!pts.length) return { err: 'нет костей ' + JSON.stringify(cam.at) + ' есть: ' + Object.keys(B) }; const m = [0, 1, 2].map(i => pts.reduce((s, q) => s + q[i], 0) / pts.length); cam = (cam.from ? cam.from : m.map((v, i) => +(v + cam.off[i]).toFixed(3))).join(',') + ';' + m.map(v => +v.toFixed(3)).join(',') + ';' + (cam.fov || 35); }
      if (cam) { const [a, b, f] = cam.split(';'); World.dbgShot('_cam', shot, { pos: a.split(',').map(Number), look: b.split(',').map(Number), fov: +f || 40 }); name = '_cam'; log.push('CAM=' + cam); }
      let tPrev = 0;
      for (let i = 0; i < frames.length; i++) { const F = frames[i]; if (isGus) gusDraw(name, F.gz); else { drawTo(name, F.t, i === 0 ? 0 : tPrev + 1 / 15); tPrev = F.t; }
        await new Promise(r => setTimeout(r, 30)); window.__tile(i, cols, frames.length, W, H, F.label); }
      const sh = World.shots[shot] || {}; return { log, bones: window.__bones(), shotCam: { pos: sh.pos, look: sh.look, fov: sh.fov, loc: sh.loc }, jpg: window.__jpg() }; } catch (e) { return { err: String(e) + (e.stack || '').slice(0, 300) }; } }, [shot, frames, W, H, cols, props, cam || null, !!(J.gus || J.sweep), J.beat || shot, J.sweep ? J.sweep.key : 'gz', J.npre || 6]);
    if (J.gus) { const plan2 = await p.evaluate(() => (window.__gusPlan || []).map(x => x.k + x.t0.toFixed(2)).join()); const plan1 = JSON.parse(fs.readFileSync(path.join(OUT, 'gus_plan.json'), 'utf8')).map(x => x.k + x.t0.toFixed(2)).join(); if (plan1 !== plan2) console.log('ВНИМАНИЕ: план Гаса сменился за работу', J.name); }
    if (info.err) { console.log('ошибка', J.name, info.err); continue; }
    fs.writeFileSync(path.join(OUT, J.name + '.jpg'), Buffer.from(info.jpg.split(',')[1], 'base64'));
    fs.writeFileSync(path.join(OUT, J.name + '.txt'), JSON.stringify({ shot, frames: frames.map(f => f.label), log: info.log, shotCam: info.shotCam, bones: info.bones }));
    console.log(new Date().toISOString().slice(14,19), 'ok', J.name, shot, frames.length + ' кадров', info.log.join(' '), 'акт:', Object.keys(info.bones).join(','));
  } catch (e) { console.log('сбой', J.name, String(e).slice(0, 300)); } }
  await br.close();
})();
