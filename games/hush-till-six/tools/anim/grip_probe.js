// «Как держит предмет»: проходит все кадры игры и ищет предмет в руке, который держат не по-человечески.
//   node tools/anim/grip_probe.js ['^регэксп кадров'] [секунды через запятую]      (сервер: cd games/hush-till-six && python3 -m http.server 8771)
// Проверки (для длинных предметов — фонарь, ручка, рация, телефон):
//   • «торчит из ладони» — длинная ось предмета смотрит по нормали ладони (как фонарь «на подносе», владелец 14 ноя), а не лежит поперёк неё;
//   • «не в руке» — середина предмета дальше 9 см от ладони (висит в воздухе рядом с кистью).
// Это быстрый поиск подозрительных мест; каждое найденное — смотреть глазами (tools/anim/shot_view.js с CAM= сбоку и снизу).
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [re = '^(?!over_)', times = '.5,1.5,2.5'] = process.argv.slice(2), PORT = process.env.PORT || 8771;
const AXIS = { torch: [1, 0, 0], pen: [0, 1, 0], radio: [0, 1, 0], phone: [0, 1, 0] };   // длинная ось предмета в его осях (см. propMesh в chars2.js)
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 480, height: 270 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 200)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="480" height="270"></canvas></body></html>`;
  await p.route(/\/web\/_gp\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${PORT}/web/_gp.html?gfx=retro&lang=ru`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  const res = await p.evaluate(async ([re, times, AXIS]) => {
    await window.__W3chars.load(); World.init();
    const T = window.__W3core.T, g = document.getElementById('c').getContext('2d'), out = [], R = new RegExp(re), ts = times.split(',').map(Number);
    const props = Object.keys(AXIS).map(n => window.__W3chars.prop3(n));
    const bp = (C, n) => C.bone[n] && C.bone[n].getWorldPosition(new T.Vector3());
    const ids = Object.keys(World.shots).filter(k => R.test(k));
    for (const id of ids) for (const t of ts) {
      try { for (let s = 0; s <= 6; s++) World.draw(g, 480, 270, id, { t: Math.max(0, t - .4 + s / 15), T: 100 + t, settle: s === 0, props: { clock: '21:30', jacket: 1, badge: 1, book: 1 } }); } catch (e) { continue; }
      for (const pr of props) {
        if (!pr.obj.visible || !pr.C || !pr.sd) continue; const C = pr.C, sd = pr.sd;
        const h = bp(C, 'hand_' + sd), m = bp(C, 'middle_01_' + sd), i = bp(C, 'index_01_' + sd), k = bp(C, 'pinky_01_' + sd); if (!h || !m || !i || !k) continue;
        const n = i.clone().sub(k).cross(m.clone().sub(h)).normalize(), palm = h.clone().lerp(m, .6);
        const ax = new T.Vector3(...AXIS[pr.name]).applyQuaternion(pr.obj.getWorldQuaternion(new T.Quaternion())).normalize();
        const c = new T.Box3().setFromObject(pr.obj).getCenter(new T.Vector3()), d = c.distanceTo(palm), up = Math.abs(ax.dot(n));
        const bad = []; if (up > .75 && pr.name !== 'phone') bad.push('торчит из ладони (' + up.toFixed(2) + ')'); if (d > .09) bad.push('не в руке: ' + Math.round(d * 100) + ' см от ладони');
        if (bad.length) out.push(`${id}@${t} ${C.root.name || ''} ${pr.name} (${sd === 'l' ? 'левая' : 'правая'}): ${bad.join(', ')}`);
      }
    }
    return { n: ids.length, out };
  }, [re, times, AXIS]);
  console.log(`кадров проверено: ${res.n}; подозрительных: ${res.out.length}`); res.out.forEach(l => console.log('  ' + l));
  await br.close();
})();
